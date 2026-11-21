@file:Suppress("DEPRECATION")

package com.neko.music.service

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** 独立于系统音效的 10 段双声道参数均衡器。 */
internal class EqualizerAudioProcessor(
    private val controller: EqualizerProcessingControl,
) : BaseAudioProcessor() {
    private class Biquad {
        var b0 = 1f
        var b1 = 0f
        var b2 = 0f
        var a1 = 0f
        var a2 = 0f
        var x1 = 0f
        var x2 = 0f
        var y1 = 0f
        var y2 = 0f

        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }

        fun resetState() {
            x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
        }
    }

    private val filters = Array(2) { Array(EqualizerFrequencies.values.size) { Biquad() } }
    private var appliedSignature = ""

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        appliedSignature = ""
        filters.forEach { channel -> channel.forEach(Biquad::resetState) }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val state = controller.state.value
        if (!state.enabled) {
            val output = replaceOutputBuffer(inputBuffer.remaining())
            output.put(inputBuffer).flip()
            return
        }
        val signature = "${inputAudioFormat.sampleRate}|${state.bandGainsDb}|${state.preampDb}"
        if (signature != appliedSignature) {
            configureFilters(inputAudioFormat.sampleRate, state)
            appliedSignature = signature
        }
        val frames = inputBuffer.remaining() / (inputAudioFormat.channelCount * 2)
        val output = replaceOutputBuffer(frames * inputAudioFormat.channelCount * 2)
        val preamp = 10f.pow(state.preampDb / 20f)
        repeat(frames) {
            repeat(inputAudioFormat.channelCount) { channel ->
                var value = inputBuffer.short / 32768f * preamp
                for (band in filters[channel]) value = band.process(value)
                output.putShort((value * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
            }
        }
        output.flip()
    }

    override fun onReset() {
        appliedSignature = ""
        filters.forEach { channel -> channel.forEach(Biquad::resetState) }
    }

    private fun configureFilters(sampleRate: Int, state: EqualizerState) {
        val nyquistGuard = sampleRate * 0.45f
        EqualizerFrequencies.values.forEachIndexed { index, frequency ->
            val gain = state.bandGainsDb[index]
            if (gain == 0f || frequency >= nyquistGuard) {
                filters.forEach { channel ->
                    val filter = channel[index]
                    filter.b0 = 1f; filter.b1 = 0f; filter.b2 = 0f; filter.a1 = 0f; filter.a2 = 0f
                }
                return@forEachIndexed
            }
            val a = 10f.pow(gain / 40f)
            val w0 = 2f * PI.toFloat() * frequency / sampleRate
            val alpha = sin(w0) / (2f * 1.4f)
            val b0 = 1f + alpha * a
            val b1 = -2f * cos(w0)
            val b2 = 1f - alpha * a
            val a0 = 1f + alpha / a
            val a1 = -2f * cos(w0)
            val a2 = 1f - alpha / a
            filters.forEach { channel ->
                val filter = channel[index]
                filter.b0 = b0 / a0
                filter.b1 = b1 / a0
                filter.b2 = b2 / a0
                filter.a1 = a1 / a0
                filter.a2 = a2 / a0
            }
        }
    }
}
