package com.neko.music.data.api

import android.util.Log
import com.neko.music.util.preferHttp2AlpnOverHttp1
import com.neko.music.util.protocolLogSuffixOrEmpty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.IOException

object QqMusicPlaylistApi {
    private const val TAG = "QqMusicPlaylistImport"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = HttpClient(OkHttp) {
        engine {
            config { preferHttp2AlpnOverHttp1() }
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    data class QqSongListSummary(
        val disstid: String,
        val code: Int,
        val cdnum: Int,
        val cdlistSize: Int,
        val dissname: String?,
        val totalBySongList: Int,
        val totalBySongNum: Int,
        val totalSongs: Int,
    )

    /**
     * 只做“先统计歌单有多少首歌”的步骤：
     * 从 `cdlist[].songlist` 统计长度；如果 songlist 为空则回退 songnum。
     */
    suspend fun fetchSongListSummary(disstid: String): Result<QqSongListSummary> {
        return try {
            val url = "https://music.cnmsb.xin/loser1/getSongListDetail?disstid=$disstid"
            Log.d(TAG, "请求 QQ 歌单详情: disstid=$disstid url=$url")
            val response: HttpResponse = client.get(url)
            val responseText = response.body<String>().trim()
            if (responseText.isEmpty()) {
                return Result.failure(IOException("QQ 歌单响应为空"))
            }
            val rootEl = json.parseToJsonElement(responseText)
            val responseObj = (rootEl as? JsonObject)?.get("response") as? JsonObject
                ?: return Result.failure(IllegalStateException("QQ 响应缺少 response 字段"))

            val code = responseObj["code"]?.jsonPrimitive?.intOrNull ?: -1
            val cdnum = responseObj["cdnum"]?.jsonPrimitive?.intOrNull ?: 0
            val cdlist = responseObj["cdlist"] as? JsonArray
                ?: JsonArray(emptyList())

            val dissname: String? = (cdlist.firstOrNull() as? JsonObject)
                ?.get("dissname")
                ?.jsonPrimitive
                ?.contentOrNull()

            var totalBySongList = 0
            var totalBySongNum = 0
            cdlist.forEach { cdEl ->
                val cdObj = cdEl as? JsonObject ?: return@forEach
                val songlistEl = cdObj["songlist"]
                val songlistArray = songlistEl as? JsonArray
                if (songlistArray != null) {
                    totalBySongList += songlistArray.size
                }
                val songnum = cdObj["songnum"]?.jsonPrimitive?.intOrNull ?: 0
                totalBySongNum += songnum
            }

            val totalSongs = if (totalBySongList > 0) totalBySongList else totalBySongNum
            val summary = QqSongListSummary(
                disstid = disstid,
                code = code,
                cdnum = cdnum,
                cdlistSize = cdlist.size,
                dissname = dissname,
                totalBySongList = totalBySongList,
                totalBySongNum = totalBySongNum,
                totalSongs = totalSongs,
            )

            if (responseObj.isNotEmpty() && code != 0) {
                Log.d(TAG, "QQ 歌单接口 code != 0: code=$code")
            }
            Result.success(summary)
        } catch (e: Exception) {
            Log.e(TAG, "获取 QQ 歌单失败${e.protocolLogSuffixOrEmpty()}", e)
            Result.failure(e)
        }
    }

    fun logSongCount(summary: QqSongListSummary) {
        Log.d(
            TAG,
            "QQ歌单: disstid=${summary.disstid}, name=${summary.dissname ?: "-"}, cdnum=${summary.cdnum}, cdlistSize=${summary.cdlistSize}, songCount=${summary.totalSongs} (by songlist=${summary.totalBySongList}, by songnum=${summary.totalBySongNum})"
        )
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? = try {
        content
    } catch (_: Exception) {
        null
    }
}

