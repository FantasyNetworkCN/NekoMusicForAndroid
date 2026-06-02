package com.neko.music.data.api

import android.util.Log
import com.neko.music.data.model.Music
import com.neko.music.data.model.SearchItem
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
data class QqSongListDetailResponse(
    val response: QqSongListResponseBody? = null,
)

@Serializable
data class QqSongListResponseBody(
    val code: Int = 0,
    val cdnum: Int = 0,
    val cdlist: List<QqCd>? = null,
)

@Serializable
data class QqCd(
    val disstid: String = "",
    val dissname: String = "",
    val songnum: Int = 0,
    val songlist: List<QqTrack>? = null,
)

@Serializable
data class QqTrack(
    val name: String = "",
    val title: String = "",
    val singer: List<QqSinger>? = null,
)

@Serializable
data class QqSinger(
    val name: String = "",
)

data class QqPlaylist(
    val disstid: String,
    val name: String,
    val trackCount: Int,
    val tracks: List<QqTrack>,
)

class QqMusicPlaylistApi {

    companion object {
        private const val TAG = "QqMusicPlaylistImport"
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

    data class MatchTracksResult(
        val successCount: Int,
        val failCount: Int,
        val apiMessage: String,
        val failedItems: List<SearchItem>,
        val matchedMusicIds: List<Int>,
    )

    suspend fun fetchPlaylistDetail(disstid: String): Result<QqSongListDetailResponse> {
        return try {
            val url = "https://music.cnmsb.xin/loser1/getSongListDetail?disstid=$disstid"
            Log.d(TAG, "请求 QQ 歌单: disstid=$disstid url=$url")
            val response = client.get(url).body<QqSongListDetailResponse>()
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "获取 QQ 歌单失败${e.protocolLogSuffixOrEmpty()}", e)
            Result.failure(e)
        }
    }

    fun toPlaylist(disstid: String, body: QqSongListResponseBody): QqPlaylist? {
        val cdlist = body.cdlist.orEmpty()
        if (cdlist.isEmpty()) return null
        val tracks = cdlist.flatMap { it.songlist.orEmpty() }
        val name = cdlist.firstNotNullOfOrNull { it.dissname.trim().takeIf { n -> n.isNotEmpty() } }
            ?: ""
        val trackCount = tracks.size.takeIf { it > 0 }
            ?: cdlist.sumOf { it.songnum }
        return QqPlaylist(
            disstid = disstid,
            name = name,
            trackCount = trackCount,
            tracks = tracks,
        )
    }

    suspend fun matchTracksInLibrary(
        playlist: QqPlaylist,
        musicApi: MusicApi,
    ): Result<MatchTracksResult> {
        val items = playlist.tracks.mapNotNull { trackToSearchItem(it) }
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

    private fun trackToSearchItem(track: QqTrack): SearchItem? {
        val title = track.name.trim().ifEmpty { track.title.trim() }
        if (title.isEmpty()) return null
        val artist = track.singer
            ?.joinToString(" / ") { it.name.trim() }
            ?.trim()
            .orEmpty()
        return SearchItem(title = title, artist = artist)
    }

    private fun logBatchSearchResults(
        items: List<SearchItem>,
        results: List<Music?>,
        stats: MatchTracksResult,
    ) {
        val paired = items.indices.map { index ->
            items[index] to results.getOrNull(index)
        }
        Log.d(TAG, "批量搜索完成: 成功 ${stats.successCount} 首, 失败 ${stats.failCount} 首")
        if (stats.apiMessage.isNotBlank()) {
            Log.d(TAG, "搜索接口: ${stats.apiMessage}")
        }
        if (stats.failCount > 0) {
            paired.forEach { (searchItem, music) ->
                if (music == null) {
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

    fun logPlaylistTracks(playlist: QqPlaylist) {
        Log.d(TAG, "QQ歌单: ${playlist.name} (disstid=${playlist.disstid})")
        Log.d(TAG, "共找到 ${playlist.tracks.size} 首音乐")
        if (playlist.trackCount > 0 && playlist.trackCount != playlist.tracks.size) {
            Log.d(TAG, "歌单标注曲目数: ${playlist.trackCount}")
        }
        if (playlist.tracks.isEmpty()) {
            Log.d(TAG, "(无曲目)")
        }
    }

    fun errorMessage(response: QqSongListDetailResponse): String {
        val code = response.response?.code ?: -1
        return if (code == 0) "歌单不存在" else "拉取歌单失败 (code=$code)"
    }
}
