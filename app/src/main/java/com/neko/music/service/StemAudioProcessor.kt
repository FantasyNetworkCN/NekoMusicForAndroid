@file:Suppress("DEPRECATION")

package com.neko.music.service

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

internal interface StemProcessingControl {
    val state: StateFlow<StemSeparationState>
    val cachedPlayback: Boolean
}

/** 轻量实时近似分离或高质量缓存混音，播放线程不运行神经网络。 */
internal class StemAudioProcessor(private val controller: StemProcessingControl) : BaseAudioProcessor() {
    private var realtime = false
    private var mixer: RealtimeStemMixer? = null
    private var tail = AudioProcessor.EMPTY_BUFFER

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val state = controller.state.value
        realtime = state.enabled && state.realtime && !state.roomPlaybackActive
        if (realtime) {
            if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
                return AudioProcessor.AudioFormat.NOT_SET
            }
            return inputAudioFormat
        }
        if (!controller.cachedPlayback) return AudioProcessor.AudioFormat.NOT_SET
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 4) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val realtimeMixer = mixer
        if (realtimeMixer != null) {
            val channels = inputAudioFormat.channelCount
            val frames = inputBuffer.remaining() / (channels * 2)
            val output = replaceOutputBuffer((frames + 1024) * channels * 2)
            repeat(frames) {
                val left = inputBuffer.short / 32768f
                val right = if (channels == 2) inputBuffer.short / 32768f else left
                realtimeMixer.append(left, right) { l, r, vocal -> mixRealtime(output, l, r, vocal) }
            }
            output.flip()
            return
        }
        val frames = inputBuffer.remaining() / 8
        val output = replaceOutputBuffer(frames * 4)
        val state = controller.state.value
        repeat(frames) {
            val originalLeft = inputBuffer.short / 32768f
            val originalRight = inputBuffer.short / 32768f
            val backingLeft = inputBuffer.short / 32768f
            val backingRight = inputBuffer.short / 32768f
            val left = mixStemSample(originalLeft, backingLeft, state.vocals, state.instrumental)
            val right = mixStemSample(originalRight, backingRight, state.vocals, state.instrumental)
            output.putShort((left * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
            output.putShort((right * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
        }
        output.flip()
    }

    private fun mixRealtime(output: ByteBuffer, left: Float, right: Float, vocal: Float) {
        val state = controller.state.value
        fun write(original: Float) {
            val sample = original * state.instrumental + vocal * (state.vocals - state.instrumental)
            output.putShort((sample * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
        }
        write(left)
        if (inputAudioFormat.channelCount == 2) write(right)
    }

    override fun onFlush() {
        tail = AudioProcessor.EMPTY_BUFFER
        mixer = if (realtime && inputAudioFormat.sampleRate > 0) {
            RealtimeStemMixer(inputAudioFormat.sampleRate, inputAudioFormat.channelCount == 1)
        } else null
    }

    override fun onQueueEndOfStream() {
        val realtimeMixer = mixer ?: return
        val output = ByteBuffer.allocateDirect(2048 * inputAudioFormat.channelCount * 2).order(ByteOrder.nativeOrder())
        realtimeMixer.finish { left, right, vocal -> mixRealtime(output, left, right, vocal) }
        output.flip()
        tail = output
    }

    override fun getOutput(): ByteBuffer {
        val output = super.getOutput()
        if (output.hasRemaining()) return output
        return tail.also { tail = AudioProcessor.EMPTY_BUFFER }
    }

    override fun isEnded(): Boolean = super.isEnded() && !tail.hasRemaining()

    override fun onReset() {
        mixer = null
        tail = AudioProcessor.EMPTY_BUFFER
        realtime = false
    }
}
