package com.neko.music.data.api

import android.util.Log
import com.neko.music.util.UrlConfig
import com.neko.music.util.preferHttp2AlpnOverHttp1
import com.neko.music.util.protocolLogSuffixOrEmpty
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.concurrent.TimeUnit

data class ExternalPullStart(
    val source: String,
    val total: Int,
    val targetPlaylistId: Int,
    val targetPlaylistCreated: Boolean,
)

data class ExternalPullTrack(
    val index: Int,
    val total: Int,
    val sourceId: String,
    val title: String,
    val artist: String,
    val status: String,
    val musicId: Int?,
    val playlistAdded: Boolean,
    val message: String?,
)

data class ExternalPullProgress(
    val index: Int,
    val total: Int,
    val bytes: Long,
    val totalBytes: Long,
    val percent: Int,
)

data class ExternalPullSummary(
    val total: Int,
    val imported: Int,
    val existed: Int,
    val failed: Int,
)

class ExternalPullCallbacks(
    val onStart: (ExternalPullStart) -> Unit = {},
    val onTrack: (ExternalPullTrack) -> Unit = {},
    val onProgress: (ExternalPullProgress) -> Unit = {},
    val onDone: (ExternalPullSummary) -> Unit = {},
    val onError: (String) -> Unit = {},
)

/**
 * 站外歌单导入：调用 `/loser/{source}/pull`，后端完成站外匹配、下载入库并加入指定歌单，
 * 进度通过 SSE 事件（start / track / progress / done / error）回报。
 */
class ExternalPlaylistPullApi {

    companion object {
        private const val TAG = "ExternalPlaylistPull"

        const val SOURCE_NETEASE = "netease"
        const val SOURCE_QQ = "qq"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        engine {
            config {
                preferHttp2AlpnOverHttp1()
                // 导入可能持续很久且事件间隔不定，禁用读超时/整体超时，避免 SSE 连接被中断
                readTimeout(0, TimeUnit.MILLISECONDS)
                callTimeout(0, TimeUnit.MILLISECONDS)
            }
        }
    }

    /**
     * @param externalPlaylistId 网易云歌单 ID 或 QQ disstid
     * @param targetPlaylistId   导入到的已有站内歌单 ID（与 targetPlaylistName 二选一）
     * @param targetPlaylistName 新建站内歌单名称（非空时后端会新建歌单）
     */
    suspend fun pull(
        source: String,
        externalPlaylistId: String,
        targetPlaylistId: Int?,
        targetPlaylistName: String?,
        token: String,
        callbacks: ExternalPullCallbacks = ExternalPullCallbacks(),
    ): Result<Unit> {
        val url = buildUrl(source, externalPlaylistId, targetPlaylistId, targetPlaylistName, token)
        Log.d(TAG, "开始导入: source=$source externalId=$externalPlaylistId url=$url")

        return try {
            val response = client.get(url) {
                headers {
                    append(HttpHeaders.Accept, "text/event-stream")
                    append(HttpHeaders.Authorization, token)
                }
            }

            if (response.status.value >= 400) {
                val body = runCatching { response.bodyAsText() }.getOrDefault("")
                val message = parseErrorMessage(body) ?: "HTTP ${response.status.value}"
                Log.e(TAG, "导入失败: $message")
                callbacks.onError(message)
                return Result.failure(IllegalStateException(message))
            }

            val channel = response.bodyAsChannel()
            var eventName = ""
            val data = StringBuilder()
            var completed = false

            while (true) {
                val line = channel.readUTF8Line() ?: break
                when {
                    line.isEmpty() -> {
                        if (dispatchEvent(eventName, data.toString(), callbacks)) completed = true
                        eventName = ""
                        data.setLength(0)
                    }
                    line.startsWith(":") -> Unit // 注释/心跳帧
                    line.startsWith("event:") -> eventName = line.substring(6).trim()
                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.substring(5).trim())
                    }
                }
            }
            if (dispatchEvent(eventName, data.toString(), callbacks)) completed = true

            if (!completed) {
                val message = "连接已中断"
                Log.e(TAG, "导入中断: source=$source")
                callbacks.onError(message)
                return Result.failure(IllegalStateException(message))
            }
            Log.d(TAG, "导入结束: source=$source")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "导入异常${e.protocolLogSuffixOrEmpty()}", e)
            callbacks.onError(e.message ?: "导入失败")
            Result.failure(e)
        }
    }

    /** @return 是否为终态事件（done / error） */
    private fun dispatchEvent(
        name: String,
        payload: String,
        callbacks: ExternalPullCallbacks,
    ): Boolean {
        if (payload.isBlank()) return false
        val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return false
        return when (name.ifEmpty { "message" }) {
            "start" -> {
                callbacks.onStart(
                    ExternalPullStart(
                        source = obj.string("source"),
                        total = obj.int("total"),
                        targetPlaylistId = obj.int("targetPlaylistId"),
                        targetPlaylistCreated = obj.bool("targetPlaylistCreated"),
                    ),
                )
                false
            }
            "track" -> {
                callbacks.onTrack(
                    ExternalPullTrack(
                        index = obj.int("index"),
                        total = obj.int("total"),
                        sourceId = obj.string("sourceId"),
                        title = obj.string("title"),
                        artist = obj.string("artist"),
                        status = obj.string("status"),
                        musicId = obj.intOrNull("musicId"),
                        playlistAdded = obj.bool("playlistAdded"),
                        message = obj.stringOrNull("message"),
                    ),
                )
                false
            }
            "progress" -> {
                callbacks.onProgress(
                    ExternalPullProgress(
                        index = obj.int("index"),
                        total = obj.int("total"),
                        bytes = obj.long("bytes"),
                        totalBytes = obj.long("totalBytes", -1L),
                        percent = obj.int("percent", -1),
                    ),
                )
                false
            }
            "done" -> {
                callbacks.onDone(
                    ExternalPullSummary(
                        total = obj.int("total"),
                        imported = obj.int("imported"),
                        existed = obj.int("existed"),
                        failed = obj.int("failed"),
                    ),
                )
                true
            }
            "error" -> {
                callbacks.onError(obj.string("message").ifBlank { "导入失败" })
                true
            }
            else -> false
        }
    }

    private fun buildUrl(
        source: String,
        externalPlaylistId: String,
        targetPlaylistId: Int?,
        targetPlaylistName: String?,
        token: String,
    ): String {
        return URLBuilder().takeFrom("${UrlConfig.getBaseUrl()}/loser/$source/pull").apply {
            parameters.append(
                if (source == SOURCE_QQ) "disstid" else "playlistId",
                externalPlaylistId,
            )
            val name = targetPlaylistName?.trim().orEmpty()
            if (name.isNotEmpty()) {
                parameters.append("targetPlaylistName", name)
            } else if (targetPlaylistId != null) {
                parameters.append("targetPlaylistId", targetPlaylistId.toString())
            }
            // EventSource 无法自定义请求头，后端同时支持 token 查询参数
            parameters.append("token", token)
        }.buildString()
    }

    private fun parseErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        return obj.stringOrNull("error") ?: obj.stringOrNull("message") ?: obj.stringOrNull("msg")
    }

    private fun JsonObject.string(key: String): String = stringOrNull(key).orEmpty()

    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String, default: Int = 0): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: default

    private fun JsonObject.intOrNull(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.long(key: String, default: Long = 0L): Long =
        this[key]?.jsonPrimitive?.longOrNull ?: default

    private fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull ?: default
}
