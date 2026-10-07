package com.neko.music.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.getSystemService
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.data.model.Music
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class DownloadHelper(private val context: Context) {

    private val downloadManager = context.getSystemService<DownloadManager>()
    private val musicApi = MusicApi(context)

    suspend fun downloadMusic(music: Music): Result<String> {
        // 扩展名探测与音质解析都是阻塞网络操作，放到 IO 线程执行，
        // 避免调用方（多为主线程的 scope.launch）被卡住。
        val extension: String
        val downloadUri: Uri
        try {
            // 优先从缓存管理器获取扩展名
            val cacheManager = com.neko.music.data.cache.MusicCacheManager.getInstance(context)
            val cachePrefs = context.getSharedPreferences("music_cache", Context.MODE_PRIVATE)
            val cachedExtension = cachePrefs.getString("music_${music.id}_ext", null)

            extension = if (cachedExtension != null) {
                Log.d("DownloadHelper", "使用缓存的扩展名: $cachedExtension")
                cachedExtension
            } else {
                Log.d("DownloadHelper", "从服务器获取扩展名")
                withContext(Dispatchers.IO) { getFileExtensionFromUrl(music.id) }
            }

            // 音质接口现在返回 200 + JSON，必须先解析出固定媒体地址再交给 DownloadManager
            downloadUri = Uri.parse(
                withContext(Dispatchers.IO) {
                    MusicUrlResolver.resolveBlocking(UrlConfig.getMusicFileUrl(music.id))
                }
            )
        } catch (e: Exception) {
            Log.e("DownloadHelper", "下载失败", e)
            return Result.failure(e)
        }

        return suspendCancellableCoroutine { continuation ->
            try {
                val downloadDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "NekoMusic"
                )

                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }

                val fileName = "${music.artist} - ${music.title}.$extension"
                    .replace(Regex("[/\\:*?\"<>|]"), "_")

                Log.d("DownloadHelper", "下载文件名: $fileName, 扩展名: $extension")

                val request = DownloadManager.Request(downloadUri).apply {
                    withNekoClientHeaders()
                    setAllowedNetworkTypes(
                        DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                    )
                    setTitle(music.title)
                    setDescription(
                        context.getString(R.string.downloading_progress, music.artist, music.title)
                    )
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "NekoMusic/$fileName"
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        setRequiresCharging(false)
                    }
                }

                val downloadId = downloadManager?.enqueue(request)

                if (downloadId != null) {
                    continuation.resume(Result.success(context.getString(R.string.download_started)))
                } else {
                    continuation.resume(
                        Result.failure(Exception(context.getString(R.string.download_manager_unavailable)))
                    )
                }
            } catch (e: Exception) {
                Log.e("DownloadHelper", "下载失败", e)
                continuation.resume(Result.failure(e))
            }
        }
    }

    suspend fun downloadMusicWithLyrics(music: Music): Result<String> {
        return try {
            downloadMusic(music)

            delay(1000)

            val lyricsResult = musicApi.getMusicLyrics(music)
            lyricsResult.fold(
                onSuccess = { lyrics ->
                    if (lyrics.isNotEmpty()) {
                        saveLyrics(music, lyrics)
                    }
                    Result.success(context.getString(R.string.download_started_with_lyrics))
                },
                onFailure = {
                    Result.success(context.getString(R.string.download_started))
                }
            )
        } catch (e: Exception) {
            Log.e("DownloadHelper", "下载失败", e)
            Result.failure(e)
        }
    }

    private fun saveLyrics(music: Music, lyrics: String) {
        try {
            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "NekoMusic"
            )

            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val fileName = "${music.artist} - ${music.title}.lrc"
                .replace(Regex("[/\\:*?\"<>|]"), "_")

            val lyricsFile = File(downloadDir, fileName)
            lyricsFile.writeText(lyrics)

            Log.d("DownloadHelper", "歌词已保存: ${lyricsFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("DownloadHelper", "保存歌词失败", e)
        }
    }

    /**
     * 从服务器获取文件扩展名
     */
    private fun getFileExtensionFromUrl(musicId: Int): String {
        return try {
            // 先解析为固定媒体地址，再探测其 Content-Type
            val resolved = MusicUrlResolver.resolveBlocking(UrlConfig.getMusicFileUrl(musicId))
            val url = java.net.URL(resolved)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.setRequestProperty(NEKO_CLIENT_HEADER, NEKO_CLIENT_VALUE)
            connection.setRequestProperty("Range", "bytes=0-0")  // 只请求第一个字节
            
            val contentType = connection.contentType ?: "audio/mpeg"
            val responseCode = connection.responseCode
            
            Log.d("DownloadHelper", "Content-Type: $contentType, Response Code: $responseCode")
            connection.disconnect()
            
            mapContentTypeToExtension(contentType)
        } catch (e: Exception) {
            Log.e("DownloadHelper", "获取文件扩展名失败", e)
            "mp3"  // 默认使用 mp3
        }
    }

    /**
     * 将 Content-Type 映射到文件扩展名
     */
    private fun mapContentTypeToExtension(contentType: String): String {
        return when {
            contentType.contains("flac") -> "flac"
            contentType.contains("wav") -> "wav"
            contentType.contains("ogg") -> "ogg"
            contentType.contains("aac") -> "aac"
            contentType.contains("m4a") || contentType.contains("mp4") -> "m4a"
            contentType.contains("wma") -> "wma"
            contentType.contains("ape") -> "ape"
            contentType.contains("mpeg") || contentType.contains("mp3") -> "mp3"
            else -> {
                Log.w("DownloadHelper", "未知的 Content-Type: $contentType，使用 mp3")
                "mp3"
            }
        }
    }
}
