@file:Suppress("DEPRECATION")

package com.neko.music.service

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessingPipeline
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StemAudioProcessorTest {
    private val format = AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)
    private val settings = StemSeparationState(enabled = true, realtime = false)

    private fun pcm(frames: Int, sample: Short = 6000): ByteBuffer =
        ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder()).apply {
            repeat(frames * 2) { putShort(sample) }
            flip()
        }

    @Test fun localAiMixAppliesNewGainsToTheSameInferenceResult() {
        var requests = 0
        val mixer = StemStreamingMixer(2, StemChunkInference { _, frames ->
            requests++
            CompletableFuture.completedFuture(FloatArray(frames * 2) { 2000 / 32768f })
        }, {}, { throw it }, 2048, 3)
        mixer.queueInput(pcm(2051))
        val allocate = { size: Int -> ByteBuffer.allocate(size).order(ByteOrder.nativeOrder()) }
        val vocal = mixer.output(settings.copy(instrumental = 0f), allocate)!!
        val backing = mixer.output(settings.copy(vocals = 0f), allocate)!!
        assertEquals(4000, vocal.short.toInt())
        assertEquals(2000, backing.short.toInt())
        assertEquals(1, requests)
    }

    @Test fun inferenceWorkerReusesOneModelForAllChunksAndClosesItOnce() {
        val created = AtomicInteger()
        val closed = CountDownLatch(1)
        val engine = object : StemInference {
            override fun instrumental(context: FloatArray, outputFrames: Int) =
                FloatArray(outputFrames * 2) { 0.1f }
            override fun close() { closed.countDown() }
        }
        val worker = StemInferenceWorker(createEngine = {
            created.incrementAndGet()
            engine
        })
        repeat(3) {
            assertEquals(engine, worker.submit(FloatArray(16), 8).get(2, TimeUnit.SECONDS).let { engine })
        }
        assertEquals(1, created.get())
        worker.close()
        assertTrue(closed.await(2, TimeUnit.SECONDS))
    }

    @Test fun inferenceFailureDegradesGpuThenRecoversOnNextChunk() {
        val preferences = mutableListOf<StemBackendPreference>()
        val calls = AtomicInteger()
        val worker = StemInferenceWorker(createEngine = { preference ->
            preferences += preference
            object : StemInference {
                override fun instrumental(context: FloatArray, outputFrames: Int): FloatArray {
                    if (calls.incrementAndGet() == 1) error("GPU context lost")
                    return FloatArray(outputFrames * 2) { 0.2f }
                }
                override fun close() {}
            }
        })
        try {
            runCatching { worker.prewarm(8).get(3, TimeUnit.SECONDS) }
            assertEquals(StemBackendPreference.AUTO, preferences.firstOrNull())
            val recovered = worker.submit(FloatArray(16), 8).get(3, TimeUnit.SECONDS)
            assertTrue(recovered != null && recovered.isNotEmpty())
            assertTrue(preferences.contains(StemBackendPreference.NO_GPU))
        } finally {
            worker.close()
        }
    }

    @Test fun timeoutFallsBackButLeavesInferenceRunningToFillCache() {
        var now = 0L
        val pending = CompletableFuture<FloatArray>()
        val mixer = StemStreamingMixer(2, StemChunkInference { _, _ -> pending }, {}, {},
            4, 1, { now }, 10, 10)
        val allocate = { size: Int -> ByteBuffer.allocate(size).order(ByteOrder.nativeOrder()) }
        mixer.queueInput(pcm(6))
        assertNull(mixer.output(settings, allocate))
        now = 11
        assertTrue(mixer.output(settings.copy(vocals = 0f, instrumental = 0f), allocate)!!.hasRemaining())
        assertFalse("等待超时后仍应在后台补全缓存", pending.isCancelled)
        mixer.reset()
        assertFalse("已经播放过的分片仍应在后台补全缓存", pending.isCancelled)
    }

    @Test fun diskContentCacheHitSkipsModelCreationAndPublishesIntoCurrentTrack() {
        val directory = Files.createTempDirectory("stem-worker-cache").toFile()
        try {
            val cache = StemChunkCache(directory)
            val context = FloatArray(32) { it / 64f }
            val cached = FloatArray(16) { 0.2f }
            val key = cache.key(context)
            cache.write(key, StemCachedSamples.fromFloats(cached), null, 44_100, 0)
            val track = StemTrackCache("music:1:hq")
            var created = 0
            val worker = StemInferenceWorker(
                createEngine = { created++; error("缓存命中不应创建模型") },
                chunkCache = cache,
            )
            try {
                val result = worker.submitForTrack(context, 8, track, 44_100, 16).get(2, TimeUnit.SECONDS)
                assertArrayEquals(cached, result, 0f)
                assertEquals(0, created)
                assertEquals(0.2f, track.chunkAt(44_100, 16)!!.samples.sample(0, 0), 0f)
            } finally { worker.close() }
        } finally { directory.deleteRecursively() }
    }

    @Test fun oldInferenceOnlyPublishesToItsOwnTrackEvenAfterSwitch() {
        val directory = Files.createTempDirectory("stem-track-switch-cache").toFile()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            val cache = StemChunkCache(directory)
            val oldTrack = StemTrackCache("music:old:hq")
            val newTrack = StemTrackCache("music:new:hq")
            val worker = StemInferenceWorker(
                createEngine = {
                    object : StemInference {
                        override fun instrumental(context: FloatArray, outputFrames: Int): FloatArray {
                            started.countDown()
                            release.await(2, TimeUnit.SECONDS)
                            return FloatArray(outputFrames * 2) { 0.1f }
                        }
                        override fun close() {}
                    }
                },
                chunkCache = cache,
            )
            try {
                val old = worker.submitForTrack(FloatArray(32), 8, oldTrack, 44_100, 0)
                assertTrue(started.await(2, TimeUnit.SECONDS))
                oldTrack.release()
                release.countDown()
                old.get(2, TimeUnit.SECONDS)
                assertNull(newTrack.chunkAt(44_100, 0))
                assertEquals(0, oldTrack.chunkCount)
            } finally { worker.close() }
        } finally {
            release.countDown()
            directory.deleteRecursively()
        }
    }

    @Test fun slowInferenceFallsBackInOrderAndDrainsOnReset() {
        var now = 0L
        val pending = mutableListOf<CompletableFuture<FloatArray>>()
        val phases = mutableListOf<StemStreamPhase>()
        val mixer = StemStreamingMixer(2, StemChunkInference { _, _ ->
            CompletableFuture<FloatArray>().also(pending::add)
        }, { phases.add(it) }, { throw it }, 4, 1, { now }, 2, 2)
        val allocate = { size: Int -> ByteBuffer.allocate(size).order(ByteOrder.nativeOrder()) }
        val input = pcm(13)
        mixer.queueInput(input)
        assertFalse(input.hasRemaining())
        assertTrue(pending.size >= 2)
        assertNull(mixer.output(settings, allocate))
        now = 2
        val original = mixer.output(settings.copy(vocals = 0f, instrumental = 0f), allocate)!!
        assertEquals(6000, original.short.toInt())
        assertEquals(StemStreamPhase.FALLBACK, phases.last())
        mixer.reset()
        assertTrue(mixer.isDrained)
    }

    @Test fun exoPipelinePlaysOriginalWhileModelDownloadsAndDrainsLastAiChunk() {
        var inference: StemChunkInference? = null
        val control = object : StemProcessingControl {
            override val state = MutableStateFlow(settings.copy(roomPlaybackActive = true))
            override val aiInference get() = inference
        }
        val pipeline = AudioProcessingPipeline(ImmutableList.of<AudioProcessor>(StemAudioProcessor(control)))
        pipeline.configure(format)
        pipeline.flush()
        try {
            pipeline.queueInput(pcm(20))
            assertEquals(80, pipeline.output.remaining())
            pipeline.output.position(pipeline.output.limit())
            inference = StemChunkInference { _, frames ->
                CompletableFuture.completedFuture(FloatArray(frames * 2))
            }
            val second = pcm(33)
            pipeline.queueInput(second)
            assertFalse(second.hasRemaining())
            pipeline.queueEndOfStream()
            val output = java.io.ByteArrayOutputStream()
            repeat(20) {
                val buffer = pipeline.output
                output.write(ByteArray(buffer.remaining()).also(buffer::get))
                if (output.size() >= 132) return@repeat
            }
            assertEquals(132, output.size())
            val samples = ByteBuffer.wrap(output.toByteArray()).order(ByteOrder.nativeOrder())
            while (samples.hasRemaining()) assertEquals(6000, samples.short.toInt())
            assertTrue(pipeline.isEnded)
        } finally {
            pipeline.reset()
        }
    }
}
