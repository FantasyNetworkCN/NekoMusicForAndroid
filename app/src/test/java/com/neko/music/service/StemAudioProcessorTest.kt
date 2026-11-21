@file:Suppress("DEPRECATION")

package com.neko.music.service

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessingPipeline
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.common.collect.ImmutableList
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StemAudioProcessorTest {
    private class Control(override val engine: StemInference?) : StemProcessingControl {
        override val state = MutableStateFlow(StemSeparationState(enabled = engine != null))
        var failure: Exception? = null
        override fun fail(expectedEngine: StemInference, messageRes: Int, error: Exception?) {
            failure = error ?: IllegalStateException("音源分离意外停止")
        }
    }

    private fun processor(control: Control, channels: Int = 2, rate: Int = 44_100) =
        StemAudioProcessor(control).apply {
            configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT))
            flush()
        }

    private fun pcm(frames: Int, channels: Int = 2): ByteBuffer =
        ByteBuffer.allocateDirect(frames * channels * 2).order(ByteOrder.nativeOrder()).apply {
            repeat(frames * channels) { putShort((it % 14000 - 7000).toShort()) }
            flip()
        }

    @Test
    fun disabledProcessingPassesPcmThroughUnchanged() {
        val processor = processor(Control(null))
        val source = pcm(4096)
        val expected = ByteArray(source.remaining()).also { source.duplicate().get(it) }
        processor.queueInput(source)
        val result = processor.output
        assertArrayEquals(expected, ByteArray(result.remaining()).also(result::get))
        processor.queueEndOfStream()
        assertTrue(processor.isEnded)
        processor.reset()
    }

    @Test
    fun emptyInputBeforeFirstAudioDoesNotCreateOutput() {
        val processor = processor(Control(null))
        repeat(3) {
            processor.queueInput(AudioProcessor.EMPTY_BUFFER)
            assertFalse(processor.output.hasRemaining())
        }
        processor.queueEndOfStream()
        assertTrue(processor.isEnded)
        processor.reset()
    }

    @Test
    fun emptyInputDoesNotDiscardPendingOriginalAudio() {
        val processor = processor(Control(null))
        val source = pcm(2048)
        val expected = ByteArray(source.remaining()).also { source.duplicate().get(it) }
        processor.queueInput(source)
        processor.queueInput(AudioProcessor.EMPTY_BUFFER)
        val result = processor.output
        assertArrayEquals(expected, ByteArray(result.remaining()).also(result::get))
        processor.reset()
    }

    @Test
    fun originalPlaybackHandlesRealPipelinePollingAndReset() {
        val processor = StemAudioProcessor(Control(null))
        val pipeline = AudioProcessingPipeline(ImmutableList.of<AudioProcessor>(processor))
        try {
            for (rate in listOf(44_100, 48_000, 96_000)) {
                pipeline.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
                pipeline.flush()
                repeat(3) { assertFalse(pipeline.output.hasRemaining()) }
                repeat(3) {
                    val source = pcm(1024 + it)
                    val expected = ByteArray(source.remaining()).also { source.duplicate().get(it) }
                    pipeline.queueInput(source)
                    assertFalse(source.hasRemaining())
                    val output = pipeline.output
                    assertArrayEquals(expected, ByteArray(output.remaining()).also(output::get))
                    repeat(3) { assertFalse(pipeline.output.hasRemaining()) }
                }
                pipeline.queueEndOfStream()
                assertFalse(pipeline.output.hasRemaining())
                assertTrue(pipeline.isEnded)
                pipeline.reset()
            }
        } finally {
            pipeline.reset()
        }
    }

    @Test
    fun shortFinalChunkAndResampledMonoPreserveEverySample() {
        for (channels in 1..2) {
            val engine = object : StemInference {
                override fun instrumental(context: FloatArray, outputFrames: Int) = FloatArray(outputFrames * 2) { 0.2f }
                override fun close() {}
            }
            val control = Control(engine)
            val processor = processor(control, channels, 48_000)
            val source = pcm(1201, channels)
            val expected = ByteArray(source.remaining()).also { source.duplicate().get(it) }
            processor.queueInput(source)
            processor.queueEndOfStream()
            val output = drain(processor)
            assertArrayEquals(expected, output)
            assertEquals(null, control.failure)
            processor.reset()
        }
    }

    @Test
    fun boundedStreamingKeepsChunkBoundariesAndFinalLookahead() {
        val control = Control(object : StemInference {
            override fun instrumental(context: FloatArray, outputFrames: Int) = FloatArray(outputFrames * 2) { 0.4f }
            override fun close() {}
        })
        val processor = processor(control)
        val source = pcm(MdxStemEngine.OUTPUT_FRAMES * 2 + 77)
        val expected = ByteArray(source.remaining()).also { source.duplicate().get(it) }
        val output = ByteArrayOutputStream()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (source.hasRemaining() && System.nanoTime() < deadline) {
            val limit = source.limit()
            source.limit(minOf(source.position() + 4096, limit))
            processor.queueInput(source)
            source.limit(limit)
            val buffer = processor.output
            output.write(ByteArray(buffer.remaining()).also(buffer::get))
            if (!buffer.hasRemaining()) Thread.yield()
        }
        assertFalse(source.hasRemaining())
        processor.queueEndOfStream()
        output.write(drain(processor))
        assertArrayEquals(expected, output.toByteArray())
        assertEquals(null, control.failure)
        processor.reset()
    }

    @Test
    fun liveGainsApplyToAlreadySeparatedOutput() {
        val control = Control(object : StemInference {
            override fun instrumental(context: FloatArray, outputFrames: Int) = FloatArray(outputFrames * 2)
            override fun close() {}
        })
        val processor = processor(control)
        processor.queueInput(pcm(2048))
        processor.queueEndOfStream()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        var first = processor.output
        while (!first.hasRemaining() && System.nanoTime() < deadline) {
            Thread.sleep(1)
            first = processor.output
        }
        assertTrue(first.hasRemaining())
        control.state.value = control.state.value.copy(vocals = 0f, instrumental = 0f)
        val rest = drain(processor)
        assertEquals(1024 * 4, rest.size)
        assertTrue(rest.all { it == 0.toByte() })
        processor.reset()
    }

    @Test
    fun flushDiscardsInferenceFromPreviousTrack() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val control = Control(object : StemInference {
            override fun instrumental(context: FloatArray, outputFrames: Int): FloatArray {
                started.countDown()
                check(release.await(3, TimeUnit.SECONDS))
                return FloatArray(outputFrames * 2)
            }
            override fun close() {}
        })
        val processor = processor(control)
        try {
            processor.queueInput(pcm(500))
            processor.queueEndOfStream()
            processor.output
            assertTrue(started.await(3, TimeUnit.SECONDS))
            processor.flush()
            release.countDown()
            processor.queueEndOfStream()
            assertTrue(drain(processor).isEmpty())
            assertFalse(processor.output.hasRemaining())
        } finally {
            release.countDown()
            processor.reset()
        }
    }

    private fun drain(processor: StemAudioProcessor): ByteArray {
        val result = ByteArrayOutputStream()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!processor.isEnded && System.nanoTime() < deadline) {
            val buffer = processor.output
            if (buffer.hasRemaining()) {
                result.write(ByteArray(buffer.remaining()).also(buffer::get))
            } else {
                Thread.sleep(1)
            }
        }
        assertTrue("音频尾部应当在处理完成后排空", processor.isEnded)
        return result.toByteArray()
    }
}
