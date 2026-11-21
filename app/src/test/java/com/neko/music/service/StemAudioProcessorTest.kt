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
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
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
        val worker = StemInferenceWorker {
            created.incrementAndGet()
            engine
        }
        repeat(3) {
            assertEquals(engine, worker.submit(FloatArray(16), 8)!!.get(2, TimeUnit.SECONDS).let { engine })
        }
        assertEquals(1, created.get())
        worker.close()
        assertTrue(closed.await(2, TimeUnit.SECONDS))
    }

    @Test fun slowInferenceFallsBackInOrderAndResetCancelsOldOutput() {
        var now = 0L
        val pending = mutableListOf<CompletableFuture<FloatArray>>()
        val phases = mutableListOf<StemStreamPhase>()
        val mixer = StemStreamingMixer(2, StemChunkInference { _, _ ->
            CompletableFuture<FloatArray>().also(pending::add)
        }, { phases.add(it) }, { throw it }, 4, 1, { now }, 2)
        val allocate = { size: Int -> ByteBuffer.allocate(size).order(ByteOrder.nativeOrder()) }
        val input = pcm(13)
        mixer.queueInput(input)
        assertTrue(input.hasRemaining())
        assertEquals(2, pending.size)
        assertNull(mixer.output(settings, allocate))
        now = 2
        val original = mixer.output(settings.copy(vocals = 0f, instrumental = 0f), allocate)!!
        assertEquals(6000, original.short.toInt())
        assertEquals(StemStreamPhase.FALLBACK, phases.last())
        mixer.reset()
        assertTrue(pending.all { it.isCancelled })
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
