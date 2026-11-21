package com.neko.music.service

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class StemChunkCacheTest {
    @Test fun diskCachePreservesFloatStemsIncludingResidualsOutsidePcm16Range() {
        val directory = Files.createTempDirectory("stem-cache").toFile()
        try {
            val context = FloatArray(128) { (it % 23 - 11) / 32f }
            val output = FloatArray(64) { (it % 17 - 8) / 4f }
            val cache = StemChunkCache(directory, version = "v1")
            val key = cache.key(context)
            cache.write(key, StemCachedSamples.fromFloats(output), null, 44_100, 0)
            val reopened = StemChunkCache(directory, version = "v1")
            assertArrayEquals(output, reopened.read(key, 32)!!.toFloatArray(), 0f)
            val v2 = StemChunkCache(directory, version = "v2")
            assertNotEquals(key, v2.key(context))
            assertNull(v2.read(v2.key(context), 32))
            assertNull(reopened.read(key, 31))
        } finally { directory.deleteRecursively() }
    }

    @Test fun changedChunkContentUsesDifferentKey() {
        val directory = Files.createTempDirectory("stem-cache").toFile()
        try {
            val cache = StemChunkCache(directory)
            val first = FloatArray(16) { 0.1f }
            val second = first.copyOf().also { it[4] = 0.2f }
            assertNotEquals(cache.key(first), cache.key(second))
        } finally { directory.deleteRecursively() }
    }

    @Test fun wholeTrackPreparationLoadsEveryChunkBeforePlaybackAndNeverEvictsEarlyChunks() {
        val directory = Files.createTempDirectory("stem-whole-track").toFile()
        try {
            val cache = StemChunkCache(directory)
            val track = StemTrackCache("music:7:hq")
            repeat(520) { index ->
                val output = FloatArray(16) { index / 1024f }
                cache.write(cache.key(output), StemCachedSamples.fromFloats(output), track, 48_000, index * 8L)
            }
            val reopened = StemTrackCache(track.identity)
            assertFalse(reopened.ready.isDone)
            cache.prepareTrack(reopened)
            assertTrue(reopened.ready.isDone)
            assertEquals(520, reopened.chunkCount)
            // 已准备的数据不依赖原文件，首片到末片均应仍可读取。
            directory.listFiles()!!.filter { it.extension == "f32" }.forEach { assertTrue(it.delete()) }
            repeat(520) { index ->
                val chunk = reopened.chunkAt(48_000, index * 8L + 3)!!
                assertEquals(index / 1024f, chunk.samples.sample(3, 0), 0f)
            }
            assertNull(reopened.chunkAt(44_100, 0))
        } finally { directory.deleteRecursively() }
    }

    @Test fun oldTrackPublicationCannotPolluteTheNewTrackOrReviveReleasedMemory() {
        val old = StemTrackCache("old")
        val next = StemTrackCache("next")
        old.put(44_100, StemCachedChunk(0, StemCachedSamples.fromFloats(FloatArray(16))))
        old.release()
        old.put(44_100, StemCachedChunk(8, StemCachedSamples.fromFloats(FloatArray(16))))
        assertEquals(0, old.chunkCount)
        assertEquals(0, next.chunkCount)
        assertTrue(old.ready.isDone)
    }

    @Test fun legacyIndexAndPcmArePreparedAtTheirAbsolutePositions() {
        val directory = Files.createTempDirectory("stem-legacy").toFile()
        try {
            val cache = StemChunkCache(directory)
            val track = StemTrackCache("music:legacy:hq")
            val key = cache.key(FloatArray(4) { 0.3f })
            val frames = MdxStemEngine.OUTPUT_FRAMES
            val bytes = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
            repeat(frames * 2) { bytes.putShort(4000) }
            File(directory, "$key.pcm").writeBytes(bytes.array())
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("${StemChunkCache.MODEL_VERSION}|${track.identity}".toByteArray())
                .joinToString("") { "%02x".format(it) }
            File(directory, "$digest.idx").writeText("0,$key\n1,$key\n")
            cache.prepareTrack(track)
            assertEquals(2, track.chunkCount)
            assertEquals(4000 / 32768f, track.chunkAt(44_100, frames + 10L)!!.samples.sample(10, 0), 0f)
        } finally { directory.deleteRecursively() }
    }

    @Test fun damagedOrMissingChunksRemainHolesAndPreparationStillCompletes() {
        val directory = Files.createTempDirectory("stem-damaged").toFile()
        try {
            val cache = StemChunkCache(directory)
            val track = StemTrackCache("music:damaged:hq")
            val first = FloatArray(16) { 0.1f }
            val second = FloatArray(16) { 0.2f }
            val firstKey = cache.key(first)
            val secondKey = cache.key(second)
            cache.write(firstKey, StemCachedSamples.fromFloats(first), track, 44_100, 0)
            cache.write(secondKey, StemCachedSamples.fromFloats(second), track, 44_100, 8)
            File(directory, "$firstKey.f32").writeBytes(byteArrayOf(1))
            File(directory, "$secondKey.f32").delete()
            val prepared = StemTrackCache(track.identity)
            cache.prepareTrack(prepared)
            assertTrue(prepared.ready.isDone)
            assertEquals(0, prepared.chunkCount)
            assertNull(cache.read("../../outside"))
        } finally { directory.deleteRecursively() }
    }
}
