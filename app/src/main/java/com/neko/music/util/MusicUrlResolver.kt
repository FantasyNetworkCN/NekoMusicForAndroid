package com.neko.music.util

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 音质解析：把 `GET /api/music/file/{id}?quality=` 换成站内固定媒体地址 `/media/music/...`。
 *
 * 服务端该接口已由 `302` 重定向改为 `200` + JSON `data.url`（并受防重放保护），因此播放 / 下载
 * 都不能再把接口地址直接交给 ExoPlayer 或 DownloadManager：必须先用带 nonce 的普通请求换取真实
 * 媒体地址。真实媒体地址是固定且可被 CDN 缓存的，解析结果在本进程内缓存一段时间，避免每次
 * seek / 重试都回源。
 *
 * 网络请求复用 [installNekoClientHeader] 安装的 Ktor 客户端（自动携带 `X-Neko-Nonce` 与客户端标识），
 * 因此本文件不直接构造裸连接。
 */
object MusicUrlResolver {
    private const val TAG = "MusicUrlResolver"
    private const val REQUEST_TIMEOUT_MS = 15_000L

    /** 媒体地址在客户端缓存时长；地址固定，过期只是重新解析一次。 */
    private const val CACHE_TTL_MS = 10 * 60_000L

    private const val API_FILE_PREFIX = "/api/music/file/"

    private class Cached(val url: String, val at: Long)

    private val cache = ConcurrentHashMap<String, Cached>()

    private val client by lazy {
        HttpClient(OkHttp) {
            installNekoClientHeader()
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = REQUEST_TIMEOUT_MS
                socketTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }
    }

    /** 该地址是否为需要解析的音质接口地址。 */
    fun isMusicFileApiUrl(url: String?): Boolean =
        !url.isNullOrBlank() && url.contains(API_FILE_PREFIX)

    /**
     * 阻塞式解析，供 ExoPlayer 加载线程与下载流程调用（这些场景本来就不在主线程）。
     *
     * 解析失败时返回原地址，让上层沿用既有错误路径，而不是在这里抛出。
     */
    fun resolveBlocking(url: String): String {
        if (!isMusicFileApiUrl(url)) return url
        cache[url]?.let { cached ->
            if (System.currentTimeMillis() - cached.at < CACHE_TTL_MS) return cached.url
        }
        val resolved = try {
            runBlocking { fetch(url) }
        } catch (e: Exception) {
            Log.w(TAG, "解析音质地址异常: $url", e)
            null
        }
        if (resolved.isNullOrBlank()) {
            Log.w(TAG, "解析音质地址失败，回退原地址: $url")
            return url
        }
        cache[url] = Cached(resolved, System.currentTimeMillis())
        return resolved
    }

    private suspend fun fetch(url: String): String? {
        val response = client.get(url)
        if (response.status.value != 200) {
            Log.w(TAG, "解析音质地址失败: HTTP ${response.status.value} $url")
            return null
        }
        val body = JSONObject(response.bodyAsText())
        if (!body.optBoolean("success", false)) {
            Log.w(TAG, "解析音质地址失败: ${body.optString("message")}")
            return null
        }
        val path = body.optJSONObject("data")?.optString("url").orEmpty()
        if (path.isBlank()) return null
        return absoluteUrl(path)
    }

    private fun absoluteUrl(path: String): String = when {
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("/") -> UrlConfig.getBaseUrl() + path
        else -> UrlConfig.getBaseUrl() + "/" + path
    }
}
