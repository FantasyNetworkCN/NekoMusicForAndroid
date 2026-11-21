package com.neko.music.data.cache

import android.content.Context
import android.os.StatFs
import java.io.File

enum class AppStorageKind {
    INSTALL, MUSIC, MEDIA, AI_MODEL, AI_CACHE, DATABASE, PREFERENCES,
    LOCAL_FILES, OTHER_CACHE, OTHER_DATA, EXTERNAL_CACHE,
}

data class AppStorageItem(val kind: AppStorageKind, val bytes: Long)

data class AppStorageUsage(
    val items: List<AppStorageItem> = emptyList(),
    val deviceTotal: Long = 0L,
    val deviceAvailable: Long = 0L,
) {
    val total: Long get() = items.sumOf { it.bytes }
    val deviceUsed: Long get() = (deviceTotal - deviceAvailable).coerceAtLeast(0L)

    fun fraction(item: AppStorageItem): Float =
        if (total <= 0L) 0f else item.bytes.toFloat() / total
}

internal data class AppStorageRoots(
    val installFiles: List<File>,
    val dataDir: File,
    val filesDir: File,
    val noBackupDir: File,
    val cacheDir: File,
    val codeCacheDir: File,
    val databasesDir: File,
    val preferencesDir: File,
    val externalCacheDir: File?,
)

/** 统计应用自身全部持久数据和系统缓存，分类互斥，避免百分比重复计算。 */
object AppStorageManager {
    fun calculate(context: Context): AppStorageUsage {
        val dataDir = File(context.applicationInfo.dataDir)
        val sourceFiles = buildList {
            add(File(context.applicationInfo.sourceDir))
            context.applicationInfo.splitSourceDirs?.forEach { add(File(it)) }
        }.distinctBy { it.absolutePath }
        val roots = AppStorageRoots(
            installFiles = sourceFiles,
            dataDir = dataDir,
            filesDir = context.filesDir,
            noBackupDir = context.noBackupFilesDir,
            cacheDir = context.cacheDir,
            codeCacheDir = context.codeCacheDir,
            databasesDir = File(dataDir, "databases"),
            preferencesDir = File(dataDir, "shared_prefs"),
            externalCacheDir = context.externalCacheDir?.takeIf { it.absolutePath !in dataDir.absolutePath },
        )
        val stat = runCatching { StatFs(dataDir.path) }.getOrNull()
        return calculate(roots, stat?.totalBytes ?: 0L, stat?.availableBytes ?: 0L)
    }

    internal fun calculate(roots: AppStorageRoots, deviceTotal: Long = 0L, deviceAvailable: Long = 0L): AppStorageUsage {
        val install = roots.installFiles.sumOf { it.byteCount() }
        val musicCache = File(roots.cacheDir, "music_cache/music").byteCount()
        val mediaCache = File(roots.cacheDir, "music_cache/cover").byteCount() +
            File(roots.cacheDir, "music_cache/lyrics").byteCount()
        val aiCache = File(roots.cacheDir, "separated-audio-v1").byteCount()
        val aiModel = File(roots.noBackupDir, "audio-models").byteCount()
        val database = roots.databasesDir.byteCount()
        val preferences = roots.preferencesDir.byteCount()
        val localFiles = roots.filesDir.byteCount()
        val noBackup = roots.noBackupDir.byteCount()
        val cache = roots.cacheDir.byteCount()
        val codeCache = roots.codeCacheDir.byteCount()
        val data = roots.dataDir.byteCount()
        val knownCache = musicCache + mediaCache + aiCache
        val otherCache = (cache - knownCache + codeCache).coerceAtLeast(0L)
        val nonFileData = cache + codeCache + localFiles + noBackup + database + preferences
        val otherData = (data - nonFileData).coerceAtLeast(0L)
        val externalCache = roots.externalCacheDir?.byteCount() ?: 0L
        val items = listOf(
            AppStorageItem(AppStorageKind.INSTALL, install),
            AppStorageItem(AppStorageKind.MUSIC, musicCache),
            AppStorageItem(AppStorageKind.MEDIA, mediaCache),
            AppStorageItem(AppStorageKind.AI_MODEL, aiModel),
            AppStorageItem(AppStorageKind.AI_CACHE, aiCache),
            AppStorageItem(AppStorageKind.DATABASE, database),
            AppStorageItem(AppStorageKind.PREFERENCES, preferences),
            AppStorageItem(AppStorageKind.LOCAL_FILES, localFiles),
            AppStorageItem(AppStorageKind.OTHER_CACHE, otherCache),
            AppStorageItem(AppStorageKind.OTHER_DATA, otherData),
            AppStorageItem(AppStorageKind.EXTERNAL_CACHE, externalCache),
        ).filter { it.bytes > 0L }
        return AppStorageUsage(items, deviceTotal, deviceAvailable)
    }

    fun format(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> "%.2f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000L -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}

private fun File.byteCount(): Long = when {
    isFile -> length()
    isDirectory -> listFiles()?.sumOf { it.byteCount() } ?: 0L
    else -> 0L
}
