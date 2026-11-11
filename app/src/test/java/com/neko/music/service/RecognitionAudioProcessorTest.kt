package com.neko.music.service

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionAudioProcessorTest {
    @Test
    fun quietMusicIsBoostedByAgc() {
        val input = sineWave(amplitude = 300f, frequency = 1_000.0, seconds = 2.0)
        val inputRms = rms(input)
        val processor = RecognitionAudioProcessor()
        processor.process(input, input.size)
        val outputRms = rms(input)

        assertTrue("expected AGC boost, input=$inputRms output=$outputRms", outputRms > inputRms * 2f)
    }

    @Test
    fun steadyNoiseIsAttenuatedInsteadOfAmplified() {
        val input = sineWave(amplitude = 70f, frequency = 420.0, seconds = 2.0)
        val inputRms = rms(input)
        val processor = RecognitionAudioProcessor()
        processor.process(input, input.size)
        val outputRms = rms(input)

        assertTrue("expected noise gate attenuation, input=$inputRms output=$outputRms", outputRms < inputRms * 0.85f)
    }

    private fun sineWave(amplitude: Float, frequency: Double, seconds: Double): ByteArray {
        val sampleCount = (SAMPLE_RATE * seconds).toInt()
        val output = ByteArray(sampleCount * 2)
        for (index in 0 until sampleCount) {
            val value = (sin(2.0 * PI * frequency * index / SAMPLE_RATE) * amplitude)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output[index * 2] = (value and 0xFF).toByte()
            output[index * 2 + 1] = ((value ushr 8) and 0xFF).toByte()
        }
        return output
    }

    private fun rms(buffer: ByteArray): Double {
        var sum = 0.0
        var count = 0
        var index = 0
        while (index + 1 < buffer.size) {
            val value = (((buffer[index + 1].toInt() shl 8) or (buffer[index].toInt() and 0xFF)).toShort()).toDouble()
            sum += value * value
            count++
            index += 2
        }
        return sqrt(sum / count.coerceAtLeast(1))
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
