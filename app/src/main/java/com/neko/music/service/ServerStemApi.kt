package com.neko.music.service

import com.neko.music.debug.NekoLog as Log
import com.neko.music.util.UrlConfig
import com.neko.music.util.installNekoClientHeader
import com.neko.music.util.preferHttp2AlpnOverHttp1
import com.neko.music.util.protocolLogSuffixOrEmpty
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.prepareGet
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readLine
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal sealed interface ServerStemEvent {
    data class Accepted(val queuePosition: Long) : ServerStemEvent
    data class Started(val totalChunks: Int, val device: String, val chunkSeconds: Double) : ServerStemEvent
    data class Chunk(
        val index: Int,
        val startSec: Double,
        val endSec: Double,
        val vocalsUrl: String,
        val accompanimentUrl: String,
    ) : ServerStemEvent
    data class Done(val vocalsUrl: String, val accompanimentUrl: String) : ServerStemEvent
    data class Failed(val message: String) : ServerStemEvent
}

internal class ServerStemHttpException(
    val statusCode: Int,
    message: String,
) : IOException(message)

/** 服务端人声 / 伴奏分离 SSE 与媒体分片下载。 */
internal class ServerStemApi : Closeable {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        installNekoClientHeader()
        engine {
            config {
                preferHttp2AlpnOverHttp1()
                // 分离队列可能需要等待，且分片事件间隔不固定。
                readTimeout(0, TimeUnit.MILLISECONDS)
                callTimeout(0, TimeUnit.MILLISECONDS)
            }
        }
    }

    suspend fun stream(musicId: Int, onEvent: suspend (ServerStemEvent) -> Unit) {
        client.prepareGet(UrlConfig.getServerStemStreamUrl(musicId)) {
            headers {
                append(HttpHeaders.Accept, "text/event-stream")
            }
        }.execute { response ->
            if (response.status.value != 200) {
                val message = runCatching {
                    json.parseToJsonElement(response.bodyAsText()).jsonObject.string("message")
                }.getOrDefault("")
                throw ServerStemHttpException(
                    response.status.value,
                    message.ifBlank { "HTTP ${response.status.value}" },
                )
            }

            val channel = response.bodyAsChannel()
            var eventName = ""
            val payload = StringBuilder()
            while (true) {
                val line = try {
                    withTimeout(120_000L) { channel.readLine() }
                } catch (timeout: TimeoutCancellationException) {
                    throw IOException("分离事件等待超时", timeout)
                } ?: break
                when {
                    line.isEmpty() -> {
                        if (payload.isNotEmpty()) {
                            if (decodeEvent(eventName.ifEmpty { "message" }, payload.toString(), onEvent)) {
                                return@execute
                            }
                        }
                        eventName = ""
                        payload.setLength(0)
                    }
                    line.startsWith(":") -> Unit
                    line.startsWith("event:") -> eventName = line.substring(6).trim()
                    line.startsWith("data:") -> {
                        if (payload.isNotEmpty()) payload.append('\n')
                        payload.append(line.substring(5).trim())
                    }
                }
            }
            if (payload.isNotEmpty()) {
                decodeEvent(eventName.ifEmpty { "message" }, payload.toString(), onEvent)
            }
        }
    }

    suspend fun download(url: String, destination: File) {
        try {
            withTimeout(120_000L) { downloadOnce(url, destination) }
        } catch (timeout: TimeoutCancellationException) {
            throw IOException("分离分片下载超时", timeout)
        }
    }

    private suspend fun downloadOnce(url: String, destination: File) {
        destination.parentFile?.mkdirs()
        client.prepareGet(UrlConfig.buildFullUrl(url)).execute { response ->
            check(response.status.value == 200) { "分片下载失败 HTTP ${response.status.value}" }
            val channel = response.bodyAsChannel()
            val temporary = File(destination.parentFile, ".${UUID.randomUUID()}.part")
            try {
                var total = 0L
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = channel.readAvailable(buffer, 0, buffer.size)
                        if (count < 0) break
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        total += count
                    }
                }
                check(total > 0L) { "分离分片为空" }
                val expected = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                check(expected == null || expected == total) { "分离分片下载不完整" }
                currentCoroutineContext().ensureActive()
                check(temporary.renameTo(destination)) { "分离分片保存失败" }
            } finally {
                temporary.delete()
            }
        }
    }

    override fun close() {
        client.close()
    }

    private suspend fun decodeEvent(
        name: String,
        raw: String,
        onEvent: suspend (ServerStemEvent) -> Unit,
    ): Boolean {
        val obj = json.parseToJsonElement(raw).jsonObject
        val event = when (name) {
            "queued" -> ServerStemEvent.Accepted(obj.long("queuePosition"))
            "start", "meta" -> ServerStemEvent.Started(
                totalChunks = obj.int("totalChunks"),
                device = obj.string("device"),
                chunkSeconds = obj.double("chunkSeconds"),
            )
            "chunk" -> ServerStemEvent.Chunk(
                index = obj.int("index"),
                startSec = obj.double("startSec"),
                endSec = obj.double("endSec"),
                vocalsUrl = obj.string("vocalsUrl"),
                accompanimentUrl = obj.string("accompanimentUrl"),
            )
            "done" -> ServerStemEvent.Done(
                vocalsUrl = obj.string("vocalsUrl"),
                accompanimentUrl = obj.string("accompanimentUrl"),
            )
            "error" -> ServerStemEvent.Failed(obj.string("message").ifBlank { "人声伴奏分离失败" })
            else -> return false
        }
        onEvent(event)
        return event is ServerStemEvent.Done || event is ServerStemEvent.Failed
    }

    private fun JsonObject.string(name: String): String =
        runCatching { this[name]!!.jsonPrimitive.content }.getOrDefault("")

    private fun JsonObject.int(name: String): Int =
        runCatching { this[name]!!.jsonPrimitive.intOrNull ?: 0 }.getOrDefault(0)

    private fun JsonObject.long(name: String): Long =
        runCatching { this[name]!!.jsonPrimitive.content.toLongOrNull() ?: 0L }.getOrDefault(0L)

    private fun JsonObject.double(name: String): Double =
        runCatching { this[name]!!.jsonPrimitive.doubleOrNull ?: 0.0 }.getOrDefault(0.0)

    companion object {
        private const val TAG = "ServerStemApi"

        fun logFailure(error: Throwable) {
            Log.w(TAG, "服务端分离失败${error.protocolLogSuffixOrEmpty()}", error)
        }
    }
}
