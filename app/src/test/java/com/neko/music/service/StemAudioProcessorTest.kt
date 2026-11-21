@file:Suppress("DEPRECATION")

package com.neko.music.service

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessingPipeline
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.SonicAudioProcessor
import com.google.common.collect.ImmutableList
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class StemAudioProcessorTest {
    private class Control : StemProcessingControl {
        override val state = MutableStateFlow(StemSeparationState(enabled = true, ready = true, realtime = false))
        override var cachedPlayback = true
    }

    private fun source(frames: Int): ByteBuffer = ByteBuffer.allocateDirect(frames * 8)
        .order(ByteOrder.nativeOrder()).apply {
            repeat(frames) { putShort(6000).putShort(-4000).putShort(2000).putShort(-1000) }
            flip()
        }

    private fun pipeline(control: Control, speed: Float = 1f): AudioProcessingPipeline {
        val sonic = SonicAudioProcessor().apply { setSpeed(speed) }
        return AudioProcessingPipeline(ImmutableList.of<AudioProcessor>(StemAudioProcessor(control), sonic)).apply {
            configure(AudioProcessor.AudioFormat(44_100, 4, C.ENCODING_PCM_16BIT))
            flush()
        }
    }

    private fun drain(pipeline: AudioProcessingPipeline): ByteArray {
        val output = ByteArrayOutputStream()
        repeat(1000) {
            val buffer = pipeline.output
            if (buffer.hasRemaining()) output.write(ByteArray(buffer.remaining()).also(buffer::get))
            if (pipeline.isEnded) return output.toByteArray()
        }
        error("缓存音频应当立即排空，不能等待模型")
    }

    @Test fun unityMixPreservesOriginalAndConvertsToStereo() {
        val pipeline = pipeline(Control())
        try {
            repeat(3) { assertFalse(pipeline.output.hasRemaining()) }
            val input = source(1024)
            pipeline.queueInput(input)
            assertFalse(input.hasRemaining())
            pipeline.queueEndOfStream()
            val output = ByteBuffer.wrap(drain(pipeline)).order(ByteOrder.nativeOrder())
            assertEquals(4096, output.remaining())
            repeat(1024) { assertEquals(6000, output.short.toInt()); assertEquals(-4000, output.short.toInt()) }
        } finally { pipeline.reset() }
    }

    @Test fun gainsChangeWithoutAnyModelOrBuffering() {
        val control = Control()
        val pipeline = pipeline(control)
        try {
            for ((vocals, backing, left, right) in listOf(
                listOf(0, 1, 2000, -1000), listOf(1, 0, 4000, -3000), listOf(0, 0, 0, 0)
            )) {
                control.state.value = control.state.value.copy(vocals = vocals.toFloat(), instrumental = backing.toFloat())
                pipeline.queueInput(source(1))
                val output = pipeline.output
                assertEquals(left, output.short.toInt())
                assertEquals(right, output.short.toInt())
            }
        } finally { pipeline.reset() }
    }

    @Test fun cachedPlaybackSupportsSpeedAndEndOfStream() {
        val pipeline = pipeline(Control(), 1.25f)
        try {
            pipeline.queueInput(source(20_000))
            pipeline.queueEndOfStream()
            val output = drain(pipeline)
            assertTrue(output.isNotEmpty())
            assertTrue(output.any { it != 0.toByte() })
            assertTrue(output.size < 20_000 * 4)
        } finally { pipeline.reset() }
    }

    @Test fun disablingAndSeekingFlushOldCacheAndRestoreOriginalPipeline() {
        val control = Control()
        val pipeline = pipeline(control)
        try {
            pipeline.queueInput(source(1024))
            pipeline.flush()
            assertFalse(pipeline.output.hasRemaining())
            control.cachedPlayback = false
            pipeline.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
            pipeline.flush()
            assertFalse(pipeline.isOperational)
        } finally { pipeline.reset() }
    }

    @Test fun activeCacheRejectsWrongChannelLayout() {
        val processor = StemAudioProcessor(Control())
        try {
            processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
            fail("双声道原曲不能当作四声道缓存混音")
        } catch (_: AudioProcessor.UnhandledAudioFormatException) {}
    }
}
