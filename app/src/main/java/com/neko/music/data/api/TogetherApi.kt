package com.neko.music.data.api

import android.util.Log
import com.neko.music.util.UrlConfig
import com.neko.music.util.installNekoClientHeader
import com.neko.music.util.preferHttp2AlpnOverHttp1
import com.neko.music.util.protocolLogSuffix
import com.neko.music.util.protocolLogSuffixOrEmpty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TogetherTrack(
    val id: Int,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Int = 0,
    val maxQuality: String? = null,
)

@Serializable
data class TogetherPlaylistItem(
    val entryId: String,
    val track: TogetherTrack,
    val addedByUserId: Int = 0,
    val addedAt: Long = 0,
)

@Serializable
data class TogetherPlaylist(
    val revision: Long = 0,
    val items: List<TogetherPlaylistItem> = emptyList(),
    val currentEntryId: String? = null,
    val repeatMode: String = "list_repeat",
)

@Serializable
data class TogetherPlayback(
    val currentEntryId: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val effectiveAt: Long = 0,
    val repeatMode: String = "list_repeat",
)

@Serializable
data class TogetherMember(
    val userId: Int,
    val nickname: String = "听众",
    val owner: Boolean = false,
    val online: Boolean = false,
)

@Serializable
data class TogetherActor(
    val userId: Int = 0,
    val nickname: String = "",
)

@Serializable
data class TogetherEvent(
    val type: String,
    val revision: Long = 0,
    val token: String? = null,
    val ownerUserId: Int = 0,
    val memberCount: Int = 0,
    val maxMembers: Int = 10,
    val serverTime: Long = 0,
    val members: List<TogetherMember>? = null,
    val playlist: TogetherPlaylist? = null,
    val playback: TogetherPlayback? = null,
    val actor: TogetherActor? = null,
    val code: String? = null,
    val message: String? = null,
    val opId: String? = null,
)

@Serializable
private data class TogetherCreateData(val url: String)

@Serializable
data class TogetherJoinData(
    val ticket: String,
    val room: TogetherEvent,
)

@Serializable
private data class TogetherTicketData(val ticket: String)

@Serializable
private data class TogetherEnvelope<T>(
    val success: Boolean = false,
    val message: String = "",
    val data: T? = null,
)

class TogetherApi {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = HttpClient(OkHttp) {
        installNekoClientHeader()
        engine {
            config { preferHttp2AlpnOverHttp1() }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(WebSockets)
    }

    fun close() {
        client.close()
    }

    suspend fun createRoom(userToken: String): Result<String> = runCatching {
        val response = client.post(UrlConfig.getApiUrl("/api/listen/rooms")) {
            header("Authorization", "Bearer $userToken")
        }
        val text = response.body<String>()
        Log.d("TogetherApi", "createRoom ${response.status} $text${response.protocolLogSuffix()}")
        val envelope = json.decodeFromString<TogetherEnvelope<TogetherCreateData>>(text)
        if (!response.status.isSuccess() || !envelope.success) {
            error(envelope.message.ifBlank { "创建一起听房间失败" })
        }
        envelope.data?.url ?: error("创建一起听房间失败")
    }

    suspend fun joinRoom(roomToken: String, userToken: String): Result<TogetherJoinData> = runCatching {
        val response = client.post(UrlConfig.getApiUrl("/api/listen/rooms/$roomToken/join")) {
            header("Authorization", "Bearer $userToken")
        }
        val text = response.body<String>()
        Log.d("TogetherApi", "joinRoom ${response.status} $text${response.protocolLogSuffix()}")
        val envelope = json.decodeFromString<TogetherEnvelope<TogetherJoinData>>(text)
        if (!response.status.isSuccess() || !envelope.success) {
            error(envelope.message.ifBlank { "加入一起听房间失败" })
        }
        envelope.data ?: error("加入一起听房间失败")
    }

    suspend fun issueTicket(roomToken: String, userToken: String): Result<String> = runCatching {
        val response = client.post(UrlConfig.getApiUrl("/api/listen/rooms/$roomToken/ticket")) {
            header("Authorization", "Bearer $userToken")
        }
        val text = response.body<String>()
        val envelope = json.decodeFromString<TogetherEnvelope<TogetherTicketData>>(text)
        if (!response.status.isSuccess() || !envelope.success) {
            error(envelope.message.ifBlank { "获取实时连接凭证失败" })
        }
        envelope.data?.ticket ?: error("获取实时连接凭证失败")
    }

    suspend fun leaveRoom(roomToken: String, userToken: String) {
        try {
            client.delete(UrlConfig.getApiUrl("/api/listen/rooms/$roomToken/members/me")) {
                header("Authorization", "Bearer $userToken")
            }
        } catch (e: Exception) {
            Log.w("TogetherApi", "leaveRoom failed${e.protocolLogSuffixOrEmpty()}", e)
        }
    }

    suspend fun closeRoom(roomToken: String, userToken: String): Result<Unit> = runCatching {
        val response = client.delete(UrlConfig.getApiUrl("/api/listen/rooms/$roomToken")) {
            header("Authorization", "Bearer $userToken")
        }
        if (!response.status.isSuccess()) {
            error("关闭一起听房间失败")
        }
    }

    /**
     * 保持一条房间 WebSocket，直到对端关闭或协程取消。
     * 调用方通过 outgoing 发送控制 JSON；事件按服务端到达顺序回调。
     */
    suspend fun runSocket(
        ticket: String,
        outgoing: ReceiveChannel<String>,
        onEvent: (TogetherEvent) -> Unit,
    ) {
        client.webSocket(urlString = UrlConfig.getTogetherWsUrl(ticket)) {
            val sender = launch {
                for (text in outgoing) {
                    send(Frame.Text(text))
                }
            }
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val text = frame.readText()
                    val event = runCatching {
                        json.decodeFromString<TogetherEvent>(text)
                    }.getOrNull()
                    if (event != null) {
                        onEvent(event)
                    } else {
                        Log.w("TogetherApi", "无法解析一起听 WS 消息: $text")
                    }
                }
            } finally {
                sender.cancel()
            }
        }
    }
}
