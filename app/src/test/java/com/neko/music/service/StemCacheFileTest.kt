package com.neko.music.service

import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class StemCacheFileTest {
    @Test fun onlyCompleteFourChannelWavCanBeActivated() {
        val file = Files.createTempFile("neko-cache-", ".wav").toFile()
        try {
            RandomAccessFile(file, "rw").use { output ->
                output.setLength(44 + 100 * 8)
                assertFalse(StemCacheFile.isComplete(file))
                StemCacheFile.writeHeader(output, 44_100, 100)
                assertTrue(StemCacheFile.isComplete(file))
                output.setLength(file.length() - 8)
                assertFalse(StemCacheFile.isComplete(file))
            }
        } finally { file.delete() }
    }

    @Test fun cacheKeySeparatesTracksAndQualities() {
        val source = "https://example.invalid/api/music/file/1?quality=hq"
        assertEquals(StemCacheFile.key(source), StemCacheFile.key(source))
        assertNotEquals(StemCacheFile.key(source), StemCacheFile.key(source.replace("hq", "sq")))
        assertNotEquals(StemCacheFile.key(source), StemCacheFile.key(source.replace("/1?", "/2?")))
        assertEquals(64, StemCacheFile.key(source).length)
    }

    @Test fun restartKeepsOnlyCompleteFramesAndDoesNotRestartFromZero() {
        assertEquals(0L, StemCacheFile.resumableFrames(0, 100))
        assertEquals(0L, StemCacheFile.resumableFrames(44, 100))
        assertEquals(57L, StemCacheFile.resumableFrames(44 + 57 * 8, 100))
        assertEquals(57L, StemCacheFile.resumableFrames(44 + 57 * 8 + 5, 100))
        assertEquals(100L, StemCacheFile.resumableFrames(44 + 120 * 8, 100))
    }
}
