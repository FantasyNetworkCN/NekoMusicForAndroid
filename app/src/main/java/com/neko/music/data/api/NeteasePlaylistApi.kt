package com.neko.music.data.api

import android.util.Log
import com.neko.music.data.model.Music
import com.neko.music.data.model.SearchItem
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

    data class MatchTracksResult(
        val successCount: Int,
        val failCount: Int,
        val apiMessage: String,
        val failedItems: List<SearchItem>,
        val matchedMusicIds: List<Int>,
    )

    suspend fun matchTracksInLibrary(
        playlist: NeteasePlaylist,
        musicApi: MusicApi,
    ): Result<MatchTracksResult> {
        val items = playlist.tracks.orEmpty().mapNotNull { trackToSearchItem(it) }
        if (items.isEmpty()) {
            Log.d(TAG, "无可搜索曲目")
            return Result.failure(IllegalStateException("无可搜索曲目"))
        }
        Log.d(TAG, "开始批量搜索，共 ${items.size} 首")
        return musicApi.searchMusicBatch(items).mapCatching { batch ->
            val stats = summarizeBatchSearch(items, batch.results, batch.message)
            logBatchSearchResults(items, batch.results, stats)
            stats
        }.onFailure { e ->
            Log.e(TAG, "批量搜索请求失败${e.message?.let { ": $it" } ?: ""}", e)
        }
    }

    private fun summarizeBatchSearch(
        items: List<SearchItem>,
        results: List<Music?>,
        apiMessage: String,
    ): MatchTracksResult {
        val paired = items.indices.map { index -> items[index] to results.getOrNull(index) }
        val successCount = paired.count { it.second != null }
        val failedItems = paired.mapNotNull { (item, music) -> item.takeIf { music == null } }
        val matchedMusicIds = paired.mapNotNull { (_, music) -> music?.id }
        return MatchTracksResult(
            successCount = successCount,
            failCount = failedItems.size,
            apiMessage = apiMessage,
            failedItems = failedItems,
            matchedMusicIds = matchedMusicIds,
        )
    }

    private fun trackToSearchItem(track: NeteaseTrack): SearchItem? {
        val title = track.name.trim()
        if (title.isEmpty()) return null
        val artist = track.ar?.joinToString(" / ") { it.name.trim() }?.trim().orEmpty()
        return SearchItem(title = title, artist = artist)
    }

    private fun logBatchSearchResults(
        items: List<SearchItem>,
        results: List<Music?>,
        stats: MatchTracksResult,
    ) {
        val apiMessage = stats.apiMessage
        val paired = items.indices.map { index ->
            items[index] to results.getOrNull(index)
        }
        val successCount = stats.successCount
        val failCount = stats.failCount
        Log.d(TAG, "批量搜索完成: 成功 $successCount 首, 失败 $failCount 首")
        if (apiMessage.isNotBlank()) {
            Log.d(TAG, "搜索接口: $apiMessage")
        }
        if (failCount > 0) {
            paired.forEach { pair ->
                if (pair.second == null) {
                    val searchItem = pair.first
                    val label = if (searchItem.artist.isNotBlank()) {
                        "${searchItem.title} — ${searchItem.artist}"
                    } else {
                        searchItem.title
                    }
                    Log.d(TAG, "搜索失败: $label")
                }
            }
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
