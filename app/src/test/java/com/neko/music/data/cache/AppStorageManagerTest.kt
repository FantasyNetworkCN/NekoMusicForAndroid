package com.neko.music.data.cache

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStorageManagerTest {
    @Test fun storageUnitsUseDecimalMbAndGb() {
        assertEquals("999 B", AppStorageManager.format(999))
        assertEquals("1.5 KB", AppStorageManager.format(1_500))
        assertEquals("2.0 MB", AppStorageManager.format(2_000_000))
        assertEquals("3.00 GB", AppStorageManager.format(3_000_000_000))
    }

    @Test fun categoriesAreMutuallyExclusiveAndCoverAllAppData() {
        val root = Files.createTempDirectory("neko-storage").toFile()
        try {
            val install = file(root, "base.apk", 100)
            val data = dir(root, "data")
            val files = dir(data, "files")
            val noBackup = dir(data, "no_backup")
            val cache = dir(data, "cache")
            val code = dir(data, "code_cache")
            val db = dir(data, "databases")
            val prefs = dir(data, "shared_prefs")
            file(files, "background.jpg", 20)
            file(noBackup, "audio-models/model.onnx", 300)
            file(cache, "music_cache/music/a.mp3", 200)
            file(cache, "music_cache/cover/a.jpg", 10)
            file(cache, "music_cache/lyrics/a.lrc", 5)
            file(cache, "separated-audio-v1/a.wav", 30)
            file(cache, "other.bin", 15)
            file(code, "compiled.bin", 7)
            file(db, "music.db", 40)
            file(prefs, "settings.xml", 3)
            file(data, "misc.bin", 11)
            val usage = AppStorageManager.calculate(
                AppStorageRoots(
                    installFiles = listOf(install), dataDir = data, filesDir = files, noBackupDir = noBackup,
                    cacheDir = cache, codeCacheDir = code, databasesDir = db, preferencesDir = prefs,
                    externalCacheDir = null,
                ),
                deviceTotal = 1_000,
                deviceAvailable = 250,
            )
            val byKind = usage.items.associate { it.kind to it.bytes }
            assertEquals(100L, byKind[AppStorageKind.INSTALL])
            assertEquals(200L, byKind[AppStorageKind.MUSIC])
            assertEquals(15L, byKind[AppStorageKind.MEDIA])
            assertEquals(300L, byKind[AppStorageKind.AI_MODEL])
            assertEquals(30L, byKind[AppStorageKind.AI_CACHE])
            assertEquals(22L, byKind[AppStorageKind.OTHER_CACHE])
            assertEquals(741L, usage.total)
            assertEquals(750L, usage.deviceUsed)
            assertEquals(usage.total, usage.items.sumOf { it.bytes })
            assertTrue(usage.fraction(usage.items.first { it.kind == AppStorageKind.AI_MODEL }) > 0.4f)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun dir(parent: File, name: String) = File(parent, name).apply { mkdirs() }
    private fun file(parent: File, name: String, bytes: Int): File =
        File(parent, name).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(bytes))
        }
}
