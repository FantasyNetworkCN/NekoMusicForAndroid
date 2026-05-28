package com.neko.music.data.api

import android.content.Context
import android.util.Log
import com.neko.music.data.cache.MusicCacheManager
import com.neko.music.data.model.BatchSearchRequest
import com.neko.music.data.model.Music
import com.neko.music.data.model.SearchItem
import com.neko.music.data.model.SearchRequest
import com.neko.music.util.UrlConfig
import com.neko.music.util.preferHttp2AlpnOverHttp1
import com.neko.music.util.protocolLogSuffix
import com.neko.music.util.protocolLogSuffixOrEmpty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.serialization.SerializationException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

class MusicApi(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
    
    private val client = HttpClient(OkHttp) {
        engine {
            config { preferHttp2AlpnOverHttp1() }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = BATCH_SEARCH_TIMEOUT_MS
            connectTimeoutMillis = BATCH_SEARCH_TIMEOUT_MS
            socketTimeoutMillis = BATCH_SEARCH_TIMEOUT_MS
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    // 禁用HTTP请求日志，避免大量日志输出
                    // Log.d("MusicApi", message)
                }
            }
            level = LogLevel.NONE
        }
    }
    
    private val baseUrl = UrlConfig.getBaseUrl()
    private val cacheManager = MusicCacheManager.getInstance(context)

    private companion object {
        const val BATCH_SEARCH_TIMEOUT_MS = 600_000L
        const val BATCH_SEARCH_CHUNK_SIZE = 40
    }
    
    data class BatchSearchResult(
        val success: Boolean,
        val message: String,
        val results: List<Music?>,
    )

    suspend fun searchMusicBatch(items: List<SearchItem>): Result<BatchSearchResult> {
        if (items.isEmpty()) {
            return Result.success(BatchSearchResult(success = true, message = "无曲目", results = emptyList()))
        }
        if (items.size <= BATCH_SEARCH_CHUNK_SIZE) {
            return searchMusicBatchOnce(items)
        }
        val mergedResults = ArrayList<Music?>(items.size)
        var lastMessage = ""
        var anySuccess = false
        val chunks = items.chunked(BATCH_SEARCH_CHUNK_SIZE)
        chunks.forEachIndexed { index, chunk ->
            val chunkResult = searchMusicBatchOnce(chunk)
            chunkResult.fold(
                onSuccess = { batch ->
                    mergedResults.addAll(batch.results)
                    if (batch.message.isNotBlank()) lastMessage = batch.message
                    if (batch.success) anySuccess = true
                    Log.d(
                        "MusicApi",
                        "Batch search chunk ${index + 1}/${chunks.size} ok, ${chunk.size} items",
                    )
                },
                onFailure = { return Result.failure(it) },
            )
        }
        val foundCount = mergedResults.count { it != null }
        val message = lastMessage.ifBlank {
            if (foundCount > 0) "搜索成功（$foundCount/${items.size} 已找到）" else "未找到匹配的音乐"
        }
        return Result.success(
            BatchSearchResult(
                success = anySuccess || foundCount > 0,
                message = message,
                results = mergedResults,
            ),
        )
    }

    private suspend fun searchMusicBatchOnce(items: List<SearchItem>): Result<BatchSearchResult> {
        return try {
            val requestBody = json.encodeToString(BatchSearchRequest(items))
            val response = client.post("$baseUrl/api/music/search") {
                contentType(Json)
                setBody(requestBody)
            }
            if (!response.status.isSuccess()) {
                Log.e("MusicApi", "Batch search HTTP ${response.status.value}")
                return Result.failure(MusicSearchBusyException())
            }
            val responseText = response.body<String>().trim()
            if (responseText.isEmpty()) {
                Log.e("MusicApi", "Batch search empty response body")
                return Result.failure(MusicSearchBusyException())
            }
            val jsonResponse = try {
                json.parseToJsonElement(responseText) as JsonObject
            } catch (e: SerializationException) {
                Log.e("MusicApi", "Batch search invalid JSON, len=${responseText.length}", e)
                return Result.failure(MusicSearchBusyException(e))
            }
            val success = jsonResponse["success"]?.toString()?.toBoolean() ?: false
            val message = jsonResponse["message"]?.toString()?.removeSurrounding("\"") ?: ""
            val results = parseBatchSearchResults(jsonResponse["results"])
            if (results.size != items.size) {
                Log.w(
                    "MusicApi",
                    "Batch search result count mismatch: expected=${items.size}, got=${results.size}",
                )
            }
            Result.success(BatchSearchResult(success, message, results))
        } catch (e: MusicSearchBusyException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("MusicApi", "Batch search error${e.protocolLogSuffixOrEmpty()}", e)
            Result.failure(if (shouldTreatAsBusy(e)) MusicSearchBusyException(e) else e)
        }
    }

    private fun shouldTreatAsBusy(e: Throwable): Boolean {
        var current: Throwable? = e
        while (current != null) {
            if (current is SerializationException) return true
            if (current.message?.contains("unexpected end of the input", ignoreCase = true) == true) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun parseBatchSearchResults(element: JsonElement?): List<Music?> {
        if (element == null || element is JsonNull) return emptyList()
        val array = element as? JsonArray ?: return emptyList()
        return array.map { el ->
            if (el is JsonNull) {
                null
            } else {
                try {
                    json.decodeFromJsonElement<Music>(el)
                } catch (e: Exception) {
                    Log.w("MusicApi", "Failed to parse batch search result item", e)
                    null
                }
            }
        }
    }

    suspend fun searchMusic(query: String): Result<List<Music>> {
        return try {
            Log.d("MusicApi", "Searching for: $query")
            val searchRequest = SearchRequest(query)
            val requestBody = json.encodeToString(searchRequest)
            Log.d("MusicApi", "Request body JSON: $requestBody")
            
            val response = client.post("$baseUrl/api/music/search") {
                contentType(Json)
                setBody(requestBody)
            }
            
            Log.d("MusicApi", "Response status: ${response.status}${response.protocolLogSuffix()}")
            val responseText = response.body<String>()
            Log.d("MusicApi", "Response raw text: $responseText${response.protocolLogSuffix()}")
            
            // 手动解析响应
            val jsonResponse = json.parseToJsonElement(responseText) as JsonObject
            val success = jsonResponse["success"]?.toString()?.toBoolean() ?: false
            val message = jsonResponse["message"]?.toString()?.removeSurrounding("\"") ?: ""
            val resultsArray = jsonResponse["results"]
            
            Log.d("MusicApi", "Parsed response - success: $success, message: $message, results: $resultsArray${response.protocolLogSuffix()}")
            
            if (success && resultsArray != null) {
                val results = json.decodeFromJsonElement<List<Music>>(resultsArray)
                Log.d("MusicApi", "Found ${results.size} results${response.protocolLogSuffix()}")
                Result.success(results)
            } else {
                Log.e("MusicApi", "Search failed: $message${response.protocolLogSuffix()}")
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e("MusicApi", "Search error${e.protocolLogSuffixOrEmpty()}", e)
            Result.failure(e)
        }
    }
    
    suspend fun getMusicCoverUrl(music: Music): String {
        // 直接返回网络 URL，不使用本地缓存
        val url = UrlConfig.buildMusicCoverUrl(music.id, music.coverFilePath)
        Log.d("MusicApi", "使用网络URL: $url")
        return url
    }
    
    suspend fun getMusicFileUrl(music: Music): String {
        // 直接返回网络 URL，不使用本地缓存
        val url = UrlConfig.getMusicFileUrl(music.id)
        Log.d("MusicApi", "使用网络URL: $url")
        return url
    }
    
    suspend fun getMusicLyrics(music: Music): Result<String> {
        // 优先使用缓存（仅在缓存启用时）
        if (cacheManager.isCacheEnabled()) {
            val cachedLyrics = cacheManager.getCachedLyricsContent(music.id)
            if (cachedLyrics != null) {
                Log.d("MusicApi", "使用缓存歌词: ${music.id}")
                return Result.success(sanitizeLyricsJsonEscapes(cachedLyrics))
            }
        }

        // 没有缓存或缓存未启用，从服务器获取
        return try {
            Log.d("MusicApi", "Fetching lyrics for music: ${music.id}")
            val response = client.get("$baseUrl/api/music/lyrics/${music.id}")
            Log.d("MusicApi", "Response status: ${response.status}${response.protocolLogSuffix()}")
            val responseText = response.body<String>()
            Log.d("MusicApi", "Response raw text: $responseText${response.protocolLogSuffix()}")

            val jsonResponse = json.parseToJsonElement(responseText) as JsonObject
            val success = jsonResponse["success"]?.toString()?.toBoolean() ?: false
            val message = jsonResponse["message"]?.toString()?.removeSurrounding("\"") ?: ""
            val data = extractLyricsStringFromJson(jsonResponse)

            Log.d("MusicApi", "Parsed lyrics: $data${response.protocolLogSuffix()}")

            if (success) {
                // 仅在缓存启用时缓存歌词
                if (cacheManager.isCacheEnabled()) {
                    cacheManager.cacheLyrics(music.id, data)
                }
                Result.success(data)
            } else {
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e("MusicApi", "Fetch lyrics error${e.protocolLogSuffixOrEmpty()}", e)
            Result.failure(e)
        }
    }

    fun getMusicDownloadUrl(music: Music): String {
        return "$baseUrl/api/music/file/${music.id}"
    }
    
    suspend fun getRanking(limit: Int = 8): Result<List<Music>> {
        return try {
            Log.d("MusicApi", "Fetching ranking with limit: $limit")
            val response = client.get("$baseUrl/api/music/ranking?limit=$limit")
            Log.d("MusicApi", "Response status: ${response.status}${response.protocolLogSuffix()}")
            val responseText = response.body<String>()
            Log.d("MusicApi", "Response raw text: $responseText${response.protocolLogSuffix()}")
            
            val jsonResponse = json.parseToJsonElement(responseText) as JsonObject
            val success = jsonResponse["success"]?.toString()?.toBoolean() ?: false
            val message = jsonResponse["message"]?.toString()?.removeSurrounding("\"") ?: ""
            val data = jsonResponse["data"]
            
            Log.d("MusicApi", "Parsed response - success: $success, message: $message, data: $data${response.protocolLogSuffix()}")
            
            if (success && data != null) {
                val results = json.decodeFromJsonElement<List<Music>>(data)
                Log.d("MusicApi", "Found ${results.size} ranking music${response.protocolLogSuffix()}")
                Result.success(results)
            } else {
                Log.e("MusicApi", "Ranking fetch failed: $message${response.protocolLogSuffix()}")
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e("MusicApi", "Ranking fetch error${e.protocolLogSuffixOrEmpty()}", e)
            Result.failure(e)
        }
    }
    
    suspend fun getLatest(limit: Int = 300): Result<List<Music>> {
        return try {
            Log.d("MusicApi", "Fetching latest music with limit: $limit")
            val response = client.get("$baseUrl/api/music/latest?limit=$limit")
            Log.d("MusicApi", "Response status: ${response.status}${response.protocolLogSuffix()}")
            val responseText = response.body<String>()
            Log.d("MusicApi", "Response raw text: $responseText${response.protocolLogSuffix()}")
            
            val jsonResponse = json.parseToJsonElement(responseText) as JsonObject
            val success = jsonResponse["success"]?.toString()?.toBoolean() ?: false
            val message = jsonResponse["message"]?.toString()?.removeSurrounding("\"") ?: ""
            val data = jsonResponse["data"]
            
            Log.d("MusicApi", "Parsed response - success: $success, message: $message, data: $data${response.protocolLogSuffix()}")
            
            if (success && data != null) {
                val results = json.decodeFromJsonElement<List<Music>>(data)
                Log.d("MusicApi", "Found ${results.size} latest music${response.protocolLogSuffix()}")
                Result.success(results)
            } else {
                Log.e("MusicApi", "Latest music fetch failed: $message${response.protocolLogSuffix()}")
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e("MusicApi", "Latest music fetch error${e.protocolLogSuffixOrEmpty()}", e)
            Result.failure(e)
        }
    }

    suspend fun getDailyRecommendations(token: String): Result<List<Music>> {
        if (token.isBlank()) return Result.success(emptyList())
        return try {
            val response = client.get("$baseUrl/api/user/recommendations/daily") {
                header("Authorization", token)
            }
            val responseText = response.body<String>()
            val jsonResponse = json.parseToJsonElement(responseText) as JsonObject
            val success = jsonResponse["success"]?.toString()?.toBoolean() ?: false
            val data = jsonResponse["data"]
            if (success && data is JsonArray) {
                val results = data.mapNotNull { item ->
                    val obj = item as? JsonObject ?: return@mapNotNull null
                    val musicId = (obj["musicId"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return@mapNotNull null
                    Music(
                        id = musicId,
                        title = (obj["title"] as? JsonPrimitive)?.content ?: "未知歌曲",
                        artist = (obj["artist"] as? JsonPrimitive)?.content ?: "未知歌手",
                        album = (obj["album"] as? JsonPrimitive)?.content ?: "",
                        duration = 0,
                    )
                }
                Result.success(results)
            } else {
                Result.failure(Exception("获取每日推荐失败"))
            }
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            Result.failure(e)
        }
    }

    /**
     * 根据音乐ID获取详情
     * 端点: GET /api/music/info/{id}
     * @param id 音乐ID
     */
    suspend fun getMusicInfo(id: Int): Result<Music> {
        return try {
            Log.d("MusicApi", "Fetching music info for id: $id")
            val response = client.get("$baseUrl/api/music/info/$id")
            Log.d("MusicApi", "Response status: ${response.status}${response.protocolLogSuffix()}")
            val responseText = response.body<String>()
            Log.d("MusicApi", "Response raw text: $responseText${response.protocolLogSuffix()}")

            val jsonResponse = json.parseToJsonElement(responseText) as JsonObject
            val success = jsonResponse["success"]?.toString()?.toBoolean() ?: false
            val message = jsonResponse["message"]?.toString()?.removeSurrounding("\"") ?: ""
            val data = jsonResponse["data"]

            if (success && data != null) {
                val music = json.decodeFromJsonElement<Music>(data)
                Log.d("MusicApi", "Music info loaded: ${music.title} - ${music.artist}${response.protocolLogSuffix()}")
                Result.success(music)
            } else {
                Log.e("MusicApi", "Failed to get music info: $message${response.protocolLogSuffix()}")
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e("MusicApi", "Get music info error${e.protocolLogSuffixOrEmpty()}", e)
            Result.failure(e)
        }
    }

    /**
     * 从歌词接口 JSON 的 `data` 字段取出字符串。
     * 必须使用 [JsonPrimitive.content] 解码，不能对 [JsonElement.toString] 再 `removeSurrounding("\"")`，
     * 否则 JSON 合法转义（如 `\/`、`\"`、`\n`）会残留在 LRC 正文中（下载到本地的 .lrc 亦同）。
     */
    private fun extractLyricsStringFromJson(jsonResponse: JsonObject): String {
        return when (val el = jsonResponse["data"]) {
            null, JsonNull -> ""
            is JsonPrimitive -> el.content
            else -> try {
                json.decodeFromJsonElement<String>(el)
            } catch (e: Exception) {
                Log.w("MusicApi", "Lyrics data is not a JSON string, using fallback", e)
                el.toString().removeSurrounding("\"")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\/", "/")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }
        }
    }

    /** 旧缓存中可能仍含 JSON 字面量 `\/`，读缓存时顺带规范化。 */
    private fun sanitizeLyricsJsonEscapes(text: String): String =
        text.replace("\\/", "/")
}