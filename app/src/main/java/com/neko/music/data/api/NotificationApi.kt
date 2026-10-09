package com.neko.music.data.api

import android.util.Log
import com.neko.music.util.NEKO_REQUEST_HEADERS
import com.neko.music.util.UrlConfig
import com.neko.music.util.installNekoClientHeader
import com.neko.music.util.preferHttp2AlpnOverHttp1
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 站内消息单条（与收件箱列表、SSE `message` 帧一致） */
@Serializable
data class NotificationItem(
    val id: Int = 0,
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val link: String = "",
    val read: Boolean = false,
    val createdAt: String? = null,
)

@Serializable
data class NotificationPage(
    val items: List<NotificationItem> = emptyList(),
    val unread: Int = 0,
    val hasMore: Boolean = false,
    val latestId: Int = 0,
)

@Serializable
data class MarkReadResult(
    val updated: Int = 0,
    val unread: Int = 0,
)

/** SSE 帧：连上先来一帧 [Ready]，此后每来一条新消息一帧 [Message] */
sealed interface NotificationStreamEvent {
    data class Ready(val unread: Int, val latestId: Int) : NotificationStreamEvent
    data class Message(val item: NotificationItem) : NotificationStreamEvent
}

/** SSE 建连被拒（非 2xx）：带上状态码与 `Retry-After`，由调用方决定退避 */
class NotificationStreamHttpException(
    val code: Int,
    val retryAfterSeconds: Int?,
) : IOException("消息流连接失败: HTTP $code")

@Serializable
private data class ApiEnvelope<T>(
    val success: Boolean = false,
    val message: String = "",
    val data: T? = null,
)

/**
 * 站内消息接口。
 *
 * `/stream` 是 SSE 长连接，这里刻意不复用 Ktor：需要「读到帧就回调」的流式读取，以及把读超时
 * 当作心跳探活（服务端每 15 秒一次 `: ping`，读超时设成它的数倍即可发现死链），用 OkHttp 的
 * `BufferedSource` 逐行解析最直接。
 */
class NotificationApi(private val token: String?) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient(OkHttp) {
        engine { config { preferHttp2AlpnOverHttp1() } }
        installNekoClientHeader()
        install(ContentNegotiation) { json(json) }
    }

    /** 长连接专用：读超时按心跳间隔放大，既不做「整包超时」也能发现死链 */
    private val streamClient by lazy {
        OkHttpClient.Builder()
            .apply { preferHttp2AlpnOverHttp1() }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val baseUrl = UrlConfig.getBaseUrl()

    /** 收件箱列表；失败返回 null（调用方按「这次没补拉成功」处理即可） */
    suspend fun list(since: Int? = null, before: Int? = null, limit: Int = 20): NotificationPage? {
        val auth = token?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val response = client.get(buildListUrl(since, before, limit)) {
                header("Authorization", auth)
            }
            response.body<ApiEnvelope<NotificationPage>>().data
        } catch (e: Exception) {
            Log.w(TAG, "获取站内消息列表失败", e)
            null
        }
    }

    /**
     * 标记已读；`ids` 为空表示全部已读。
     * 返回服务端算出的未读数，失败返回 null。
     */
    suspend fun markRead(ids: List<Int>): Int? {
        val auth = token?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val response = client.post("$baseUrl/api/user/notifications/read") {
                header("Authorization", auth)
                contentType(ContentType.Application.Json)
                setBody(ReadRequest(ids))
            }
            response.body<ApiEnvelope<MarkReadResult>>().data?.unread
        } catch (e: Exception) {
            Log.w(TAG, "标记站内消息已读失败", e)
            null
        }
    }

    /**
     * 建立 SSE 长连接并逐帧回调，直到对端关闭或协程被取消。
     *
     * 取消会连带 `Call.cancel()`，否则阻塞在 socket 读上不会返回。
     */
    suspend fun stream(onEvent: suspend (NotificationStreamEvent) -> Unit) =
        withContext(Dispatchers.IO) {
            val auth = token?.takeIf { it.isNotBlank() } ?: throw IOException("未登录")
            val request = Request.Builder()
                .url("$baseUrl/api/user/notifications/stream")
                .apply { NEKO_REQUEST_HEADERS.forEach { (name, value) -> header(name, value) } }
                .header("Authorization", auth)
                .header("Accept", "text/event-stream")
                .build()

            val call = streamClient.newCall(request)
            currentCoroutineContext().job.invokeOnCompletion { call.cancel() }

            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw NotificationStreamHttpException(
                        response.code,
                        response.header("Retry-After")?.trim()?.toIntOrNull(),
                    )
                }
                val source = response.body.source()
                var eventName = ""
                val data = StringBuilder()

                while (true) {
                    ensureActive()
                    // 对端关闭时返回 null；心跳是 `: ping` 注释行，照常读得到
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.isEmpty() -> {
                            if (data.isNotEmpty()) {
                                decodeEvent(eventName, data.toString(), onEvent)
                                data.clear()
                            }
                            eventName = ""
                        }
                        line.startsWith(":") -> Unit // 注释（心跳），忽略
                        line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> {
                            val payload = line.removePrefix("data:").removePrefix(" ")
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(payload)
                        }
                        // 其余字段（retry / id）客户端不需要
                    }
                }
            }
        }

    private suspend fun decodeEvent(
        eventName: String,
        payload: String,
        onEvent: suspend (NotificationStreamEvent) -> Unit,
    ) {
        when (eventName) {
            "ready" -> {
                val obj = runCatching { json.parseToJsonElement(payload) }.getOrNull() ?: return
                onEvent(
                    NotificationStreamEvent.Ready(
                        unread = obj.intField("unread"),
                        latestId = obj.intField("latestId"),
                    )
                )
            }
            "message" -> {
                val item = runCatching { json.decodeFromString<NotificationItem>(payload) }
                    .getOrElse {
                        Log.w(TAG, "解析站内消息帧失败", it)
                        return
                    }
                onEvent(NotificationStreamEvent.Message(item))
            }
        }
    }

    private fun kotlinx.serialization.json.JsonElement.intField(name: String): Int =
        (this as? kotlinx.serialization.json.JsonObject)
            ?.get(name)
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() }
            ?: 0

    private fun buildListUrl(since: Int?, before: Int?, limit: Int): String {
        val params = buildList {
            since?.takeIf { it > 0 }?.let { add("since=$it") }
            before?.takeIf { it > 0 }?.let { add("before=$it") }
            add("limit=$limit")
        }
        return "$baseUrl/api/user/notifications?" + params.joinToString("&")
    }

    @Serializable
    private data class ReadRequest(val ids: List<Int>)

    private companion object {
        const val TAG = "NotificationApi"
    }
}
