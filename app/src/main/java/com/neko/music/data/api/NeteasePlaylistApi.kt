package com.neko.music.data.api

import android.util.Log
import com.neko.music.util.UrlConfig
import com.neko.music.util.preferHttp2AlpnOverHttp1
import com.neko.music.util.protocolLogSuffixOrEmpty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class NeteasePlaylistDetailResponse(
    val code: Int = 0,
    val message: String? = null,
    val msg: String? = null,
    val playlist: NeteasePlaylist? = null,
)

@Serializable
data class NeteasePlaylist(
    val id: Long = 0,
    val name: String = "",
    val trackCount: Int = 0,
    val tracks: List<NeteaseTrack>? = null,
)

@Serializable
data class NeteaseTrack(
    val name: String = "",
    val ar: List<NeteaseArtist>? = null,
)

@Serializable
data class NeteaseArtist(
    val name: String = "",
)

class NeteasePlaylistApi {

    companion object {
        private const val TAG = "NeteasePlaylistImport"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        engine {
            config { preferHttp2AlpnOverHttp1() }
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun fetchPlaylistDetail(playlistId: Long): Result<NeteasePlaylistDetailResponse> {
        return try {
            val url = "${UrlConfig.getBaseUrl()}/loser/playlist/detail?id=$playlistId"
            Log.d(TAG, "请求网易云歌单: id=$playlistId url=$url")
            val response = client.get(url).body<NeteasePlaylistDetailResponse>()
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "获取网易云歌单失败${e.protocolLogSuffixOrEmpty()}", e)
            Result.failure(e)
        }
    }

    fun logPlaylistTracks(playlist: NeteasePlaylist) {
        val tracks = playlist.tracks.orEmpty()
        Log.d(TAG, "网易云歌单: ${playlist.name} (id=${playlist.id})")
        Log.d(TAG, "共找到 ${tracks.size} 首音乐")
        if (playlist.trackCount > 0 && playlist.trackCount != tracks.size) {
            Log.d(TAG, "歌单标注曲目数: ${playlist.trackCount}")
        }
        if (tracks.isEmpty()) {
            Log.d(TAG, "(无曲目)")
        }
    }

    fun errorMessage(response: NeteasePlaylistDetailResponse): String {
        return response.message?.takeIf { it.isNotBlank() }
            ?: response.msg?.takeIf { it.isNotBlank() }
            ?: "歌单不存在"
    }
}
