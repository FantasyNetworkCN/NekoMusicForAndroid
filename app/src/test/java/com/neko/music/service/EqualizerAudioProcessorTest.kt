@file:Suppress("DEPRECATION")

package com.neko.music.service

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerAudioProcessorTest {
    private class Control : EqualizerProcessingControl {
        override val state = MutableStateFlow(EqualizerState())
    }

    private fun pcm(frames: Int, frequency: Double = 62.0): ByteBuffer =
        ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder()).apply {
            repeat(frames) {
                val value = (sin(2 * PI * frequency * it / 44_100) * 8_000).toInt().toShort()
                putShort(value).putShort(value)
            }
            flip()
        }

    private fun process(control: Control, frequency: Double = 62.0): ShortArray {
        val processor = EqualizerAudioProcessor(control)
        processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        val input = pcm(16_384, frequency)
        processor.queueInput(input)
        assertFalse(input.hasRemaining())
        val output = processor.output
        return ShortArray(output.remaining() / 2).also { result ->
            for (index in result.indices) result[index] = output.short
        }
    }

    @Test fun disabledEqualizerLeavesPipelineInactive() {
        val processor = EqualizerAudioProcessor(Control())
        processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        assertFalse(processor.isActive)
    }

    @Test fun flatPresetPreservesPcmExactly() {
        val control = Control()
        control.state.value = EqualizerState(enabled = true)
        val input = pcm(1_024)
        val expected = ByteArray(input.remaining()).also(input.duplicate()::get)
        val processor = EqualizerAudioProcessor(control)
        processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.queueInput(input)
        val output = processor.output
        assertArrayEquals(expected, ByteArray(output.remaining()).also(output::get))
    }

    @Test fun bassBoostAndCutChangeEnergyInExpectedDirection() {
        fun energy(gain: Float): Double {
            val control = Control()
            control.state.value = EqualizerState(
                enabled = true,
                bandGainsDb = List(10) { index -> if (index == 1) gain else 0f },
            )
            val samples = process(control)
            var sum = 0.0
            var count = 0
            for (index in 2_000 until samples.size) {
                val value = samples[index].toDouble()
                sum += value * value
                count++
            }
            return sqrt(sum / count.coerceAtLeast(1))
        }
        val flat = energy(0f)
        val boosted = energy(10f)
        val cut = energy(-10f)
        assertTrue("flat=$flat boosted=$boosted", boosted > flat)
        assertTrue("flat=$flat cut=$cut", cut < flat)
    }

    @Test fun preampReducesOutput() {
        val control = Control()
        control.state.value = EqualizerState(enabled = true, preampDb = -6f)
        val processed = process(control)
        val expected = pcm(16_384)
        var reference = 0.0
        repeat(4_000) { expected.short }
        while (expected.remaining() >= 2) {
            val sample = expected.short.toDouble()
            reference += sample * sample
        }
        val outputEnergy = processed.drop(2_000).sumOf { it.toDouble() * it * it }
        assertTrue(outputEnergy < reference)
    }

    @Test fun presetsAlwaysContainTenGains() {
        assertTrue(EqualizerPreset.all.all { EqualizerPreset.gains[it]?.size == EqualizerFrequencies.values.size })
    }
}
