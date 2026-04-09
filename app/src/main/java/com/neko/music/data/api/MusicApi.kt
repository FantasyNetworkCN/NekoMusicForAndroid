package com.neko.music.data.api

import android.content.Context
import android.util.Log
import com.neko.music.data.cache.MusicCacheManager
import com.neko.music.data.model.ErrorResponse
import com.neko.music.data.model.Music
import com.neko.music.data.model.SearchRequest
import com.neko.music.data.model.SearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class MusicApi(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
    
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d("MusicApi", message)
                }
            }
            level = LogLevel.ALL
        }
    }
    
    private val baseUrl = "https://music.cnmsb.xin"
    private val cacheManager = MusicCacheManager.getInstance(context)
    
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
            
            Log.d("MusicApi", "Response status: ${response.status}")
            val responseText = response.body<String>()
            Log.d("MusicApi", "Response raw text: $responseText")
            
            // 手动解析响应
            val jsonResponse = json.parseToJsonElement(responseText) as JsonObject
            val success = jsonResponse["success"]?.toString()?.toBoolean() ?: false
            val message = jsonResponse["message"]?.toString()?.removeSurrounding("\"") ?: ""
            val resultsArray = jsonResponse["results"]
            
            Log.d("MusicApi", "Parsed response - success: $success, message: $message, results: $resultsArray")
            
            if (success && resultsArray != null) {
                val results = json.decodeFromJsonElement<List<Music>>(resultsArray)
                Log.d("MusicApi", "Found ${results.size} results")
                Result.success(results)
            } else {
                Log.e("MusicApi", "Search failed: $message")
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e("MusicApi", "Search error", e)
            Result.failure(e)
        }
    }
    
    suspend fun getMusicCoverUrl(music: Music): String {
        // 直接返回网络 URL，不使用本地缓存
        val url = "$baseUrl/api/music/cover/${music.id}"
        Log.d("MusicApi", "使用网络URL: $url")
        return url
    }
    
    suspend fun getMusicFileUrl(music: Music): String {
        // 直接返回网络 URL，不使用本地缓存
        val url = "$baseUrl/api/music/file/${music.id}"
        Log.d("MusicApi", "使用网络URL: $url")
        return url
    }
    
    suspend fun getMusicLyrics(music: Music): Result<String> {
        // 优先使用缓存（仅在缓存启用时）
        if (cacheManager.isCacheEnabled()) {
            val cachedLyrics = cacheManager.getCachedLyricsContent(music.id)
            if (cachedLyrics != null) {
                Log.d("MusicApi", "使用缓存歌词: ${music.id}")
                return Result.success(cachedLyrics)
            }
        }

        // 没有缓存或缓存未启用，从服务器获取
        return try {
            Log.d("MusicApi", "Fetching lyrics for music: ${music.id}")
            val response = client.get("$baseUrl/api/music/lyrics/${music.id}?t=${System.currentTimeMillis()}")
            Log.d("MusicApi", "Response status: ${response.status}")
            val responseText = response.body<String>()
            Log.d("MusicApi", "Response raw text: $responseText")

            val jsonResponse = json.parseToJsonElement(responseText) as JsonObject
            val success = jsonResponse["success"]?.toString()?.toBoolean() ?: false
            val message = jsonResponse["message"]?.toString()?.removeSurrounding("\"") ?: ""
            val data = jsonResponse["data"]?.toString()?.removeSurrounding("\"")?.replace("\\n", "\n") ?: ""

            Log.d("MusicApi", "Parsed lyrics: $data")

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
            Log.e("MusicApi", "Fetch lyrics error", e)
            Result.failure(e)
        }
    }

    fun getMusicDownloadUrl(music: Music): String {
        return "$baseUrl/api/music/file/${music.id}"
    }
    
    suspend fun getRanking(limit: Int = 8): Result<List<Music>> {
        return try {
            Log.d("MusicApi", "Fetching ranking with limit: $limit")
            val response = client.get("$baseUrl/api/music/ranking?limit=$limit&t=${System.currentTimeMillis()}")
            Log.d("MusicApi", "Response status: ${response.status}")
            val responseText = response.body<String>()
            Log.d("MusicApi", "Response raw text: $responseText")
            
            val jsonResponse = json.parseToJsonElement(responseText) as JsonObject
            val success = jsonResponse["success"]?.toString()?.toBoolean() ?: false
            val message = jsonResponse["message"]?.toString()?.removeSurrounding("\"") ?: ""
            val data = jsonResponse["data"]
            
            Log.d("MusicApi", "Parsed response - success: $success, message: $message, data: $data")
            
            if (success && data != null) {
                val results = json.decodeFromJsonElement<List<Music>>(data)
                Log.d("MusicApi", "Found ${results.size} ranking music")
                Result.success(results)
            } else {
                Log.e("MusicApi", "Ranking fetch failed: $message")
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e("MusicApi", "Ranking fetch error", e)
            Result.failure(e)
        }
    }
    
    suspend fun getLatest(limit: Int = 300): Result<List<Music>> {
        return try {
            Log.d("MusicApi", "Fetching latest music with limit: $limit")
            val response = client.get("$baseUrl/api/music/latest?limit=$limit&t=${System.currentTimeMillis()}")
            Log.d("MusicApi", "Response status: ${response.status}")
            val responseText = response.body<String>()
            Log.d("MusicApi", "Response raw text: $responseText")
            
            val jsonResponse = json.parseToJsonElement(responseText) as JsonObject
            val success = jsonResponse["success"]?.toString()?.toBoolean() ?: false
            val message = jsonResponse["message"]?.toString()?.removeSurrounding("\"") ?: ""
            val data = jsonResponse["data"]
            
            Log.d("MusicApi", "Parsed response - success: $success, message: $message, data: $data")
            
            if (success && data != null) {
                val results = json.decodeFromJsonElement<List<Music>>(data)
                Log.d("MusicApi", "Found ${results.size} latest music")
                Result.success(results)
            } else {
                Log.e("MusicApi", "Latest music fetch failed: $message")
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e("MusicApi", "Latest music fetch error", e)
            Result.failure(e)
        }
    }
}