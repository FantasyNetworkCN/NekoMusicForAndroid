package com.neko.music.data.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 音乐缓存管理器
 * 负责缓存音乐文件、封面、歌词等数据
 */
class MusicCacheManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MusicCacheManager"
        private const val CACHE_DIR_NAME = "music_cache"
        private const val MUSIC_DIR = "music"
        private const val COVER_DIR = "cover"
        private const val LYRICS_DIR = "lyrics"

        @Volatile
        private var instance: MusicCacheManager? = null

        fun getInstance(context: Context): MusicCacheManager {
            return instance ?: synchronized(this) {
                instance ?: MusicCacheManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val cacheDir: File by lazy {
    File(context.cacheDir, CACHE_DIR_NAME)
}

private val musicDir: File by lazy {
    File(cacheDir, MUSIC_DIR)
}

private val coverDir: File by lazy {
    File(cacheDir, COVER_DIR)
}

private val lyricsDir: File by lazy {
    File(cacheDir, LYRICS_DIR)
}

// 创建缓存目录（仅在缓存启用时调用）
private fun ensureCacheDirs() {
    if (!cacheDir.exists()) {
        cacheDir.mkdirs()
    }
    if (!musicDir.exists()) {
        musicDir.mkdirs()
    }
    if (!coverDir.exists()) {
        coverDir.mkdirs()
    }
    if (!lyricsDir.exists()) {
        lyricsDir.mkdirs()
    }
}

    private val prefs by lazy {
        context.getSharedPreferences("music_cache", Context.MODE_PRIVATE)
    }

    /**
     * 检查缓存是否启用
     */
    fun isCacheEnabled(): Boolean {
        return prefs.getBoolean("cache_enabled", true)
    }

    /**
     * 获取音乐文件缓存路径
     */
    fun getCachedMusicFile(musicId: Int): File? {
        if (!isCacheEnabled()) return null

        // 检查是否正在缓存中
        val isCaching = prefs.getBoolean("music_${musicId}_caching", false)
        if (isCaching) {
            Log.d(TAG, "音乐文件正在缓存中: $musicId")
            return null
        }

        // 从 SharedPreferences 获取该音乐的扩展名
        val extension = prefs.getString("music_${musicId}_ext", "mp3") ?: "mp3"
        val fileName = "music_$musicId.$extension"
        val file = File(musicDir, fileName)

        // 检查文件是否存在且大小合理（至少 1KB）
        if (file.exists() && file.length() > 1024) {
            return file
        }

        return null
    }

    /**
     * 获取封面缓存路径
     */
    fun getCachedCoverFile(musicId: Int): File? {
        if (!isCacheEnabled()) return null
        val fileName = "cover_$musicId.jpg"
        val file = File(coverDir, fileName)
        return if (file.exists()) file else null
    }

    /**
     * 获取歌词缓存路径
     */
    fun getCachedLyricsFile(musicId: Int): File? {
        if (!isCacheEnabled()) return null
        val fileName = "lyrics_$musicId.lrc"
        val file = File(lyricsDir, fileName)
        return if (file.exists()) file else null
    }

    /**
     * 缓存音乐文件
     */
    suspend fun cacheMusicFile(musicId: Int, url: String, title: String = ""): Result<File> = withContext(Dispatchers.IO) {
        if (!isCacheEnabled()) {
            return@withContext Result.failure(Exception("缓存未启用"))
        }

        try {
            // 确保缓存目录存在
            ensureCacheDirs()

            // 标记正在缓存
            prefs.edit()
                .putBoolean("music_${musicId}_caching", true)
                .apply()

            // 下载文件并获取扩展名
            val (extension, file) = downloadFileWithExtension(url, musicId)

            // 记录缓存信息
            prefs.edit()
                .putLong("music_${musicId}_time", System.currentTimeMillis())
                .putLong("music_${musicId}_size", file.length())
                .putString("music_${musicId}_title", title)
                .putString("music_${musicId}_ext", extension)
                .putBoolean("music_${musicId}_caching", false)
                .apply()

            Log.d(TAG, "音乐文件缓存成功: $musicId ($extension)")
            Result.success(file)
        } catch (e: Exception) {
            // 缓存失败，移除标记并删除不完整的文件
            prefs.edit()
                .putBoolean("music_${musicId}_caching", false)
                .apply()
            // 删除可能存在的不完整文件
            musicDir.listFiles()?.filter { it.name.startsWith("music_$musicId.") }?.forEach { it.delete() }
            Log.e(TAG, "缓存音乐文件失败: $musicId", e)
            Result.failure(e)
        }
    }

    /**
     * 缓存封面
     */
    suspend fun cacheCover(musicId: Int, url: String): Result<File> = withContext(Dispatchers.IO) {
        if (!isCacheEnabled()) {
            return@withContext Result.failure(Exception("缓存未启用"))
        }

        try {
            // 确保缓存目录存在
            ensureCacheDirs()

            val fileName = "cover_$musicId.jpg"
            val file = File(coverDir, fileName)

            // 下载文件
            downloadFile(url, file)

            // 记录缓存信息
            prefs.edit()
                .putLong("cover_${musicId}_time", System.currentTimeMillis())
                .putLong("cover_${musicId}_size", file.length())
                .apply()

            Log.d(TAG, "封面缓存成功: $musicId")
            Result.success(file)
        } catch (e: Exception) {
            Log.e(TAG, "缓存封面失败: $musicId", e)
            Result.failure(e)
        }
    }

    /**
     * 缓存歌词
     */
    suspend fun cacheLyrics(musicId: Int, content: String): Result<File> = withContext(Dispatchers.IO) {
        if (!isCacheEnabled()) {
            return@withContext Result.failure(Exception("缓存未启用"))
        }

        try {
            // 确保缓存目录存在
            ensureCacheDirs()

            val fileName = "lyrics_$musicId.lrc"
            val file = File(lyricsDir, fileName)

            // 写入文件
            FileOutputStream(file).use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            }

            // 记录缓存信息
            prefs.edit()
                .putLong("lyrics_${musicId}_time", System.currentTimeMillis())
                .putLong("lyrics_${musicId}_size", file.length())
                .apply()

            Log.d(TAG, "歌词缓存成功: $musicId")
            Result.success(file)
        } catch (e: Exception) {
            Log.e(TAG, "缓存歌词失败: $musicId", e)
            Result.failure(e)
        }
    }

    /**
     * 获取缓存的歌词内容
     */
    fun getCachedLyricsContent(musicId: Int): String? {
        if (!isCacheEnabled()) return null
        val file = getCachedLyricsFile(musicId) ?: return null
        return try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "读取缓存歌词失败: $musicId", e)
            null
        }
    }

    /**
     * 删除音乐缓存
     */
    fun deleteMusicCache(musicId: Int) {
        try {
            getCachedMusicFile(musicId)?.delete()
            getCachedCoverFile(musicId)?.delete()
            getCachedLyricsFile(musicId)?.delete()

            // 清除缓存记录
            prefs.edit()
                .remove("music_${musicId}_time")
                .remove("music_${musicId}_size")
                .remove("music_${musicId}_title")
                .remove("music_${musicId}_ext")
                .remove("music_${musicId}_caching")
                .remove("cover_${musicId}_time")
                .remove("cover_${musicId}_size")
                .remove("lyrics_${musicId}_time")
                .remove("lyrics_${musicId}_size")
                .apply()

            Log.d(TAG, "删除音乐缓存: $musicId")
        } catch (e: Exception) {
            Log.e(TAG, "删除音乐缓存失败: $musicId", e)
        }
    }

    /**
     * 更新已缓存音乐的标题
     */
    fun updateMusicTitle(musicId: Int, title: String) {
        prefs.edit()
            .putString("music_${musicId}_title", title)
            .apply()
        Log.d(TAG, "更新音乐标题: $musicId -> $title")
    }

    /**
     * 清空所有缓存
     */
    fun clearAllCache() {
        try {
            if (!isCacheEnabled()) {
                Log.d(TAG, "缓存未启用，跳过清空缓存")
                return
            }
            
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()
                musicDir.mkdirs()
                coverDir.mkdirs()
                lyricsDir.mkdirs()
            }

            // 清除所有缓存记录
            prefs.edit().clear().apply()

            Log.d(TAG, "清空所有缓存")
        } catch (e: Exception) {
            Log.e(TAG, "清空缓存失败", e)
        }
    }

    /**
     * 获取缓存大小（字节）
     */
    fun getCacheSize(): Long {
        if (!cacheDir.exists()) {
            return 0L
        }
        return cacheDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }

    /**
     * 获取缓存大小（格式化字符串）
     */
    fun getCacheSizeFormatted(): String {
        val size = getCacheSize()
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }

    /**
     * 获取缓存的音乐数量
     */
    fun getCachedMusicCount(): Int {
        return if (musicDir.exists()) {
            musicDir.listFiles()?.size ?: 0
        } else {
            0
        }
    }

    /**
     * 获取所有缓存的音乐项（音乐ID和标题）
     */
    fun getAllCachedItems(): List<Pair<String, String>> {
        val items = mutableListOf<Pair<String, String>>()
        if (musicDir.exists()) {
            musicDir.listFiles()?.forEach { file ->
                val musicId = file.nameWithoutExtension.replace("music_", "")
                val title = prefs.getString("music_${musicId}_title", "Unknown Song") ?: "Unknown Song"
                items.add(Pair(musicId, title))
            }
        }
        return items
    }

    /**
     * 下载文件
     */
    private fun downloadFile(urlString: String, outputFile: File) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.requestMethod = "GET"

        connection.inputStream.use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * 下载文件并从 Content-Type 获取扩展名
     */
    private fun downloadFileWithExtension(urlString: String, musicId: Int): Pair<String, File> {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.requestMethod = "GET"

        // 从 Content-Type 获取文件格式
        val contentType = connection.contentType ?: "audio/mpeg"
        val extension = mapContentTypeToExtension(contentType)
        
        Log.d(TAG, "Content-Type: $contentType, 扩展名: $extension")

        // 创建具有正确扩展名的文件
        val file = File(musicDir, "music_$musicId.$extension")

        connection.inputStream.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }

        return Pair(extension, file)
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
                Log.w(TAG, "未知的 Content-Type: $contentType，使用 mp3")
                "mp3"
            }
        }
    }
}