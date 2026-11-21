package com.neko.music.service

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StemDspTest {
    @Test
    fun stereoSpectrumRoundTripPreservesPhaseAndChannels() {
        val dsp = StemDsp(fftSize = 64, hop = 16, bins = 33, frames = 32)
        val original = FloatArray(dsp.sampleCount * 2) {
            val frame = it / 2
            if (it % 2 == 0) (sin(2 * PI * frame / 32) * 0.6).toFloat()
            else (sin(2 * PI * frame / 16 + 0.8) * 0.2).toFloat()
        }
        assertArrayEquals(original, dsp.decode(dsp.encode(original)), 0.00001f)
    }

    @Test
    fun silenceRemainsFiniteAndSilent() {
        val dsp = StemDsp(fftSize = 64, hop = 16, bins = 33, frames = 32)
        val output = dsp.decode(dsp.encode(FloatArray(dsp.sampleCount * 2)))
        assertTrue(output.all { it == 0f && it.isFinite() })
    }

    @Test
    fun originalMixIsExactEvenWhenEstimatedStemsContainArtifacts() {
        val originals = listOf(-1f, -0.7f, 0f, 0.3f, 0.9999695f)
        for (original in originals) {
            for (estimate in listOf(-2f, -0.2f, 0f, 0.8f, 2f)) {
                assertEquals(original, mixStemSample(original, estimate, 1f, 1f), 0f)
                assertEquals(0f, mixStemSample(original, estimate, 0f, 0f), 0f)
                assertEquals(estimate, mixStemSample(original, estimate, 0f, 1f), 0f)
                assertEquals(original - estimate, mixStemSample(original, estimate, 1f, 0f), 0f)
            }
        }
    }

    @Test
    fun resamplingKeepsStereoChannelsIndependent() {
        val original = FloatArray(96) { if (it % 2 == 0) 0.3f else -0.8f }
        for (frames in listOf(24, 48, 96)) {
            val output = resampleStereo(original, frames)
            assertEquals(frames * 2, output.size)
            for (frame in 0 until frames) {
                assertEquals(0.3f, output[frame * 2], 0.000001f)
                assertEquals(-0.8f, output[frame * 2 + 1], 0.000001f)
            }
        }
    }
}
