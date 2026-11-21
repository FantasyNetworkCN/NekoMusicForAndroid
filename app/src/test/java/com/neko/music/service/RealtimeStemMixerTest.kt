@file:Suppress("DEPRECATION")

package com.neko.music.service

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessingPipeline
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.common.collect.ImmutableList
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class RealtimeStemMixerTest {
    @Test fun togetherPlaybackKeepsRealtimeSeparationActiveAndLocalGainsEffective() {
        val state = MutableStateFlow(StemSeparationState(enabled = true, roomPlaybackActive = true))
        val control = object : StemProcessingControl {
            override val state = state
        }
        val pipeline = AudioProcessingPipeline(ImmutableList.of<AudioProcessor>(StemAudioProcessor(control)))
        pipeline.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        pipeline.flush()
        try {
            assertTrue("一起听期间仍应启用本地实时分离", pipeline.isOperational)
            val audible = ByteArrayOutputStream()
            repeat(4) {
                val input = ByteBuffer.allocateDirect(1024 * 4).order(ByteOrder.nativeOrder())
                repeat(1024) { input.putShort(4000).putShort(-4000) }
                input.flip()
                pipeline.queueInput(input)
                val output = pipeline.output
                audible.write(ByteArray(output.remaining()).also(output::get))
            }
            assertTrue(audible.toByteArray().any { it != 0.toByte() })
            state.value = state.value.copy(vocals = 0f, instrumental = 0f)
            val input = ByteBuffer.allocateDirect(1024 * 4).order(ByteOrder.nativeOrder())
            repeat(1024) { input.putShort(4000).putShort(-4000) }
            input.flip()
            pipeline.queueInput(input)
            val output = pipeline.output
            assertTrue(output.hasRemaining())
            assertTrue(ByteArray(output.remaining()).also(output::get).all { it == 0.toByte() })
            assertTrue(state.value.roomPlaybackActive)
        } finally { pipeline.reset() }
    }

    @Test fun realtimePipelinePreservesEverySampleAtUnityForMonoAndStereo() {
        for (channels in 1..2) {
            val state = MutableStateFlow(StemSeparationState(enabled = true))
            val control = object : StemProcessingControl {
                override val state = state
            }
            val pipeline = AudioProcessingPipeline(ImmutableList.of<AudioProcessor>(StemAudioProcessor(control)))
            pipeline.configure(AudioProcessor.AudioFormat(48_000, channels, C.ENCODING_PCM_16BIT))
            pipeline.flush()
            try {
                val expected = ByteArrayOutputStream()
                val actual = ByteArrayOutputStream()
                repeat(12) { block ->
                    val input = ByteBuffer.allocateDirect((17 + block * 53) * channels * 2).order(ByteOrder.nativeOrder())
                    while (input.hasRemaining()) input.putShort((input.position() * 13 - 5000).toShort())
                    input.flip()
                    expected.write(ByteArray(input.remaining()).also(input.duplicate()::get))
                    pipeline.queueInput(input)
                    assertFalse(input.hasRemaining())
                    val output = pipeline.output
                    actual.write(ByteArray(output.remaining()).also(output::get))
                }
                pipeline.queueEndOfStream()
                repeat(10) {
                    val output = pipeline.output
                    actual.write(ByteArray(output.remaining()).also(output::get))
                }
                assertTrue(pipeline.isEnded)
                assertArrayEquals(expected.toByteArray(), actual.toByteArray())
            } finally { pipeline.reset() }
        }
    }

    @Test fun centerVocalEstimateAndResidualReconstructOriginal() {
        val mixer = RealtimeStemMixer(48_000, false)
        var count = 0
        var vocalPower = 0.0
        val consume: (Float, Float, Float) -> Unit = { left, right, vocal ->
            assertEquals(left, (left - vocal) + vocal, 0.000001f)
            assertEquals(right, (right - vocal) + vocal, 0.000001f)
            vocalPower += vocal * vocal
            count++
        }
        repeat(48_000) { frame ->
            val sample = (sin(2 * PI * 1000 * frame / 48_000) * 0.4).toFloat()
            mixer.append(sample, sample, consume)
        }
        mixer.finish(consume)
        assertEquals(48_000, count)
        assertTrue(vocalPower / count > 0.04)
    }

    @Test fun realtimeOutputStartsWithinOneWindowAndFlushDropsOldAudio() {
        val mixer = RealtimeStemMixer(44_100, false)
        var frames = 0
        repeat(1024) { mixer.append(0.3f, -0.3f) { _, _, vocal ->
            assertEquals(0f, vocal, 0.00001f)
            frames++
        } }
        assertTrue(frames > 0)
    }
}
