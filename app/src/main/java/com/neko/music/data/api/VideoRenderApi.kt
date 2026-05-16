package com.neko.music.data.api

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import com.neko.music.util.UrlConfig
import com.neko.music.util.preferHttp2AlpnOverHttp1
import com.neko.music.util.protocolLogSuffixOrEmpty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.app.DownloadManager
import androidx.core.content.getSystemService

@Serializable
data class VideoRenderCreateRequest(
    val musicId: Int,
    val startSec: Double = 0.0,
    val watermarked: Boolean = true
)

@Serializable
data class VideoRenderCreateData(
    val jobId: String = "",
    val status: String = "",
    val isVip: Boolean = false,
    val durationSec: Double = 0.0,
    val watermarked: Boolean = false,
    val musicId: Int = 0,
    val remainingToday: Int? = null
)

@Serializable
data class VideoRenderCreateEnvelope(
    val success: Boolean,
    val message: String = "",
    val data: VideoRenderCreateData? = null
)

@Serializable
data class VideoRenderStatusData(
    val jobId: String = "",
    val status: String = "",
    val musicId: Int = 0,
    val durationSec: Double = 0.0,
    val watermarked: Boolean = false,
    val error: String? = null,
    val downloadUrl: String? = null
)

@Serializable
data class VideoRenderStatusEnvelope(
    val success: Boolean,
    val message: String = "",
    val data: VideoRenderStatusData? = null
)

class VideoRenderApi(private val bearerToken: String) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient(OkHttp) {
        engine { config { preferHttp2AlpnOverHttp1() } }
        install(ContentNegotiation) { json(json) }
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
    }

    private val baseUrl = UrlConfig.getBaseUrl()

    private fun authHeader(): String {
        val t = bearerToken.trim()
        return if (t.startsWith("Bearer ", ignoreCase = true)) t else "Bearer $t"
    }

    suspend fun createJob(musicId: Int, startSec: Double, watermarked: Boolean): VideoRenderCreateEnvelope {
        return try {
            val response = client.post("$baseUrl/api/video/render/create") {
                contentType(ContentType.Application.Json)
                header("Authorization", authHeader())
                setBody(VideoRenderCreateRequest(musicId, startSec, watermarked))
            }
            json.decodeFromString<VideoRenderCreateEnvelope>(response.bodyAsText())
        } catch (e: Exception) {
            Log.e("VideoRenderApi", "创建渲染任务失败${e.protocolLogSuffixOrEmpty()}", e)
            VideoRenderCreateEnvelope(success = false, message = e.message ?: "网络错误")
        }
    }

    suspend fun fetchStatus(jobId: String): VideoRenderStatusEnvelope {
        return try {
            val response = client.get("$baseUrl/api/video/render/$jobId") {
                header("Authorization", authHeader())
            }
            json.decodeFromString<VideoRenderStatusEnvelope>(response.bodyAsText())
        } catch (e: Exception) {
            Log.e("VideoRenderApi", "查询渲染状态失败${e.protocolLogSuffixOrEmpty()}", e)
            VideoRenderStatusEnvelope(success = false, message = e.message ?: "网络错误")
        }
    }

    companion object {
        const val NON_VIP_CLIP_SEC = 15

        fun enqueueDownload(context: Context, jobId: String, fileName: String): Result<Unit> {
            return try {
                val dm = context.getSystemService<DownloadManager>()
                    ?: return Result.failure(IllegalStateException("DownloadManager unavailable"))
                val safeName = fileName.replace(Regex("[/\\\\?%*:|\"<>]"), "_")
                val url = "${UrlConfig.getBaseUrl()}/api/video/render/$jobId/download"
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setAllowedNetworkTypes(
                        DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                    )
                    setTitle(safeName)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "NekoMusic/$safeName"
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        setRequiresCharging(false)
                    }
                }
                dm.enqueue(request)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("VideoRenderApi", "下载视频失败", e)
                Result.failure(e)
            }
        }
    }
}
