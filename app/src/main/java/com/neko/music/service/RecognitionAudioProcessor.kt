package com.neko.music.service

import java.io.Closeable
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Frame-based DSP for the 16 kHz mono PCM16 recognition stream.
 *
 * The processor deliberately avoids the platform NoiseSuppressor because that
 * effect is speech-oriented and can aggressively remove quiet music. Instead:
 * - an 80 Hz high-pass removes rumble and DC offset;
 * - a low-threshold AGC keeps quiet speaker playback audible;
 * - a gentle adaptive gate lowers steady background noise;
 * - short high-crest frames are treated as keyboard/mouse transients;
 * - a soft limiter prevents clipping after gain.
 */
internal class RecognitionAudioProcessor : Closeable {
    // TODO(recognition): 降噪和增益还需优化，重点处理低音量音乐与键鼠瞬态噪声。
    private var previousInput = 0f
    private var previousOutput = 0f
    private var noiseFloorRms = 90f
    private var agcGain = 1f
    private var appliedGain = 1f
    private var closed = false

    @Synchronized
    fun process(buffer: ByteArray, length: Int) {
        if (closed) return
        val sampleBytes = (length.coerceAtMost(buffer.size) / 2) * 2
        var offset = 0
        while (offset < sampleBytes) {
            val frameEnd = (offset + FRAME_BYTES).coerceAtMost(sampleBytes)
            var sumSquares = 0.0
            var peak = 0f
            var sampleIndex = 0
            var cursor = offset
            while (cursor < frameEnd) {
                val low = buffer[cursor].toInt() and 0xFF
                val high = buffer[cursor + 1].toInt()
                val sample = ((high shl 8) or low).toShort().toFloat()
                val filtered = HIGH_PASS_ALPHA * (previousOutput + sample - previousInput)
                previousInput = sample
                previousOutput = filtered
                val filteredInt = filtered.toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                buffer[cursor] = (filteredInt and 0xFF).toByte()
                buffer[cursor + 1] = ((filteredInt ushr 8) and 0xFF).toByte()
                val magnitude = abs(filtered)
                if (magnitude > peak) peak = magnitude
                sumSquares += filtered * filtered
                cursor += 2
                sampleIndex++
            }

            if (sampleIndex == 0) break
            val rms = sqrt(sumSquares / sampleIndex).toFloat()
            val isTransient = rms > noiseFloorRms * 1.6f &&
                peak > TRANSIENT_PEAK &&
                peak > rms * TRANSIENT_CREST_FACTOR

            if (isTransient) {
                noiseFloorRms += (rms - noiseFloorRms) * 0.0005f
            } else if (rms < noiseFloorRms * 1.5f) {
                noiseFloorRms += (rms - noiseFloorRms) * 0.06f
            } else {
                noiseFloorRms += (rms - noiseFloorRms) * 0.0015f
            }
            noiseFloorRms = noiseFloorRms.coerceIn(MIN_NOISE_FLOOR, MAX_NOISE_FLOOR)

            val gateThreshold = (noiseFloorRms * 1.35f).coerceAtLeast(MIN_GATE_RMS)
            val gateRatio = (rms / gateThreshold).coerceIn(0f, 1f)
            val gateGain = if (gateRatio >= 1f) {
                1f
            } else {
                (0.25f + 0.75f * gateRatio * gateRatio).coerceIn(0.25f, 1f)
            }

            // Do not update AGC from a click frame; otherwise every key press
            // ducks the following music frames.
            if (!isTransient && rms >= maxOf(MIN_AGC_RMS, gateThreshold * 0.9f)) {
                val desiredGain = (TARGET_RMS / rms).coerceIn(MIN_AGC_GAIN, MAX_AGC_GAIN)
                val coefficient = if (desiredGain < agcGain) {
                    AGC_FAST_RELEASE
                } else {
                    AGC_SLOW_ATTACK
                }
                agcGain += (desiredGain - agcGain) * coefficient
            }

            val transientGain = if (isTransient) TRANSIENT_ATTENUATION else 1f
            val targetGain = (agcGain * gateGain * transientGain)
                .coerceIn(MIN_TOTAL_GAIN, MAX_TOTAL_GAIN)
            val startGain = appliedGain
            cursor = offset
            sampleIndex = 0
            val frameSamples = (frameEnd - offset) / 2
            while (cursor < frameEnd) {
                val low = buffer[cursor].toInt() and 0xFF
                val high = buffer[cursor + 1].toInt()
                val sample = ((high shl 8) or low).toShort().toFloat()
                val progress = if (frameSamples <= 1) 1f else sampleIndex.toFloat() / (frameSamples - 1)
                val gain = startGain + (targetGain - startGain) * progress
                val outputInt = softLimit(sample * gain).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                buffer[cursor] = (outputInt and 0xFF).toByte()
                buffer[cursor + 1] = ((outputInt ushr 8) and 0xFF).toByte()
                cursor += 2
                sampleIndex++
            }
            appliedGain = targetGain
            offset = frameEnd
        }
    }

    private fun softLimit(value: Float): Float {
        val magnitude = abs(value)
        if (magnitude <= LIMIT_KNEE) return value
        val compressed = LIMIT_KNEE +
            (magnitude - LIMIT_KNEE) / (1f + (magnitude - LIMIT_KNEE) / LIMIT_HEADROOM)
        return if (value < 0f) -compressed.coerceAtMost(SHORT_MAX) else compressed.coerceAtMost(SHORT_MAX)
    }

    @Synchronized
    override fun close() {
        closed = true
    }

    private companion object {
        const val SAMPLE_RATE = 16_000f
        const val HIGH_PASS_HZ = 80f
        const val FRAME_SAMPLES = 160
        const val FRAME_BYTES = FRAME_SAMPLES * 2
        val HIGH_PASS_ALPHA = run {
            val rc = 1f / (2f * Math.PI.toFloat() * HIGH_PASS_HZ)
            val dt = 1f / SAMPLE_RATE
            rc / (rc + dt)
        }

        const val MIN_NOISE_FLOOR = 50f
        const val MAX_NOISE_FLOOR = 1_200f
        const val MIN_GATE_RMS = 90f
        const val MIN_AGC_RMS = 50f
        const val TARGET_RMS = 3_800f
        const val MIN_AGC_GAIN = 0.62f
        const val MAX_AGC_GAIN = 5.5f
        const val AGC_FAST_RELEASE = 0.34f
        const val AGC_SLOW_ATTACK = 0.045f
        const val MIN_TOTAL_GAIN = 0.28f
        const val MAX_TOTAL_GAIN = 6f

        const val TRANSIENT_PEAK = 5_500f
        const val TRANSIENT_CREST_FACTOR = 7f
        const val TRANSIENT_ATTENUATION = 0.1f

        const val LIMIT_KNEE = 27_000f
        const val LIMIT_HEADROOM = 8_000f
        const val SHORT_MAX = 32_767f
    }
}
