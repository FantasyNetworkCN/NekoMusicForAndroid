package com.neko.music.service

import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class StemCacheRecoveryTest {
    private fun samples(value: Short, frames: Int = 441) =
        StemCachedSamples.fromShorts(ShortArray(frames * 2) { value })

    @Test
    fun fullDecodeKeepsFastTrackUntilSlowerTrackArrives() {
        val published = mutableListOf<StemCachedChunk>()
        val assembler = StemPairAssembler { published += it }
        repeat(12) { assembler.put(it * 441L, samples(1000), vocal = true) }
        assertTrue(published.isEmpty())
        (0 until 12).reversed().forEach { assembler.put(it * 441L, samples(2000), vocal = false) }
        assertEquals(12, published.size)
        assertEquals(12, published.map { it.startFrame }.toSet().size)
        published.forEach {
            assertEquals(1000 / 32768f, it.vocals.sample(0, 0), 0f)
            assertEquals(2000 / 32768f, it.accompaniment.sample(0, 1), 0f)
        }
    }

    @Test
    fun concurrentDualTrackCompletionPublishesEveryPairOnce() {
        val published = mutableListOf<StemCachedChunk>()
        val assembler = StemPairAssembler { published += it }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val voice = pool.submit { repeat(40) { assembler.put(it * 441L, samples(100), true) } }
            val backing = pool.submit { repeat(40) { assembler.put(it * 441L, samples(200), false) } }
            voice.get(5, TimeUnit.SECONDS)
            backing.get(5, TimeUnit.SECONDS)
            assertEquals(40, published.size)
        } finally { pool.shutdownNow() }
    }

    @Test
    fun completePcmCacheReopensOfflineAndDamagedChunkRemainsAHole() {
        val root = Files.createTempDirectory("stem-pcm-test").toFile()
        try {
            val cache = StemDiskCache(root)
            repeat(4) { cache.write(72, StemCachedChunk(it * 441L, samples(1000), samples(2000))) }
            cache.complete(72, (0..3).map { it * 441L })
            val track = StemTrackCache("offline")
            assertTrue(StemDiskCache(root).restore(72) { track.put(STEM_SAMPLE_RATE, it) })
            assertEquals(4, track.chunkCount)
            assertNotNull(track.chunkAt(48_000, 1_500))

            root.resolve("72/pcm-v2/441.pcm").writeBytes(byteArrayOf(1))
            val remaining = mutableListOf<StemCachedChunk>()
            assertFalse(cache.restore(72) { remaining += it })
            assertEquals(listOf(0L, 882L, 1323L), remaining.map { it.startFrame })
        } finally { root.deleteRecursively() }
    }

    @Test
    fun canonicalPairsCoverSeekLoopAndSampleRateChangesWithoutReDecode() {
        val track = StemTrackCache("sample-rates")
        repeat(4) { track.put(STEM_SAMPLE_RATE,
            StemCachedChunk(it * 441L, samples(1000), samples(2000))) }
        for (rate in listOf(44_100, 48_000, 96_000, 192_000)) {
            val length = rate / 100 * 4
            for (frame in listOf(0, rate / 100 - 1, rate / 100, length - 1, 0)) {
                val chunk = track.chunkAt(rate, frame.toLong())
                assertNotNull("rate=$rate frame=$frame", chunk)
                assertEquals(1000 / 32768f, chunk!!.vocals.sample((frame - chunk.startFrame).toInt(), 0), 0f)
                assertEquals(2000 / 32768f, chunk.accompaniment.sample((frame - chunk.startFrame).toInt(), 1), 0f)
            }
            assertNull(track.chunkAt(rate, length.toLong()))
        }
        assertEquals(4, track.chunkCount)
        track.release()
        assertNull(track.chunkAt(48_000, 0))
    }

    @Test
    fun evictedPairsReloadForSeekAndLoopWithoutNetworkOrDecoder() {
        val root = Files.createTempDirectory("stem-seek-test").toFile()
        try {
            val disk = StemDiskCache(root)
            val track = StemTrackCache("seek")
            val step = 30L * STEM_SAMPLE_RATE
            repeat(12) { index ->
                val chunk = StemCachedChunk(index * step, samples(1000), samples(2000))
                disk.write(19, chunk)
                track.put(STEM_SAMPLE_RATE, chunk)
                track.retainAround(0)
            }
            assertTrue(track.chunkCount <= 4)
            assertNull(track.chunkAt(STEM_SAMPLE_RATE, step * 10))
            for (target in listOf(step * 10, 0L)) {
                track.retainAround(target)
                disk.loadNear(19, target,
                    needed = { track.chunkAt(STEM_SAMPLE_RATE, it) == null },
                    publish = { track.put(STEM_SAMPLE_RATE, it) })
                val pair = track.chunkAt(STEM_SAMPLE_RATE, target)
                assertNotNull(pair)
                assertEquals(2000 / 32768f, pair!!.accompaniment.sample(0, 0), 0f)
                assertEquals(1000 / 32768f, pair.vocals.sample(0, 1), 0f)
            }
        } finally { root.deleteRecursively() }
    }

    @Test
    fun concatenatedMp3RemovesPaddingAtEveryBoundaryWithoutTimelineDrift() {
        val layout = StemMp3ChunkLayout(30 * STEM_SAMPLE_RATE)
        assertEquals(1_324_800, layout.encodedFrames)
        var audioFrames = 0
        var position = 0
        val chunks = 20
        // 模拟解码器任意大小输出块跨过分片边界。
        repeat(chunks * layout.encodedFrames / 512 + 1) {
            var count = minOf(512, chunks * layout.encodedFrames - position)
            while (count > 0) {
                val offset = position % layout.encodedFrames
                val take = minOf(count, layout.nextBoundary(offset) - offset)
                if (layout.isAudio(offset)) audioFrames += take
                position += take
                count -= take
            }
        }
        assertEquals(chunks * layout.audioFrames, audioFrames)
    }
}
