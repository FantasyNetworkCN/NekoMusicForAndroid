package com.neko.music.util

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.getSystemService
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.data.model.Music
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DownloadHelper(private val context: Context) {

    private val downloadManager = context.getSystemService<DownloadManager>()
    private val musicApi = MusicApi(context)

    suspend fun downloadMusic(music: Music): Result<String> = suspendCancellableCoroutine { continuation ->
        try {
            // 优先从缓存管理器获取扩展名
            val cacheManager = com.neko.music.data.cache.MusicCacheManager.getInstance(context)
            val cachePrefs = context.getSharedPreferences("music_cache", Context.MODE_PRIVATE)
            val cachedExtension = cachePrefs.getString("music_${music.id}_ext", null)
            
            val extension = if (cachedExtension != null) {
                Log.d("DownloadHelper", "使用缓存的扩展名: $cachedExtension")
                cachedExtension
            } else {
                Log.d("DownloadHelper", "从服务器获取扩展名")
                getFileExtensionFromUrl(music.id)
            }
            
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

            val downloadUri = Uri.parse("https://music.cnmsb.xin/api/music/file/${music.id}")

            val request = DownloadManager.Request(downloadUri).apply {
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
                setTitle(music.title)
                setDescription(context.getString(R.string.downloading_progress, music.artist, music.title))
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
                continuation.resume(Result.failure(Exception(context.getString(R.string.download_manager_unavailable))))
            }
        } catch (e: Exception) {
            Log.e("DownloadHelper", "下载失败", e)
            continuation.resume(Result.failure(e))
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
            val url = java.net.URL("https://music.cnmsb.xin/api/music/file/$musicId")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
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
