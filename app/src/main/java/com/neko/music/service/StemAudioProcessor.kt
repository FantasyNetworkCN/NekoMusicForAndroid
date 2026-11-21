@file:Suppress("DEPRECATION")

package com.neko.music.service

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.BaseAudioProcessor
import com.neko.music.debug.NekoLog as Log
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

internal interface StemProcessingControl {
    val state: StateFlow<StemSeparationState>
    val aiInference: StemChunkInference? get() = null
    fun reportAiPhase(inference: StemChunkInference?, phase: StemStreamPhase) {}
}

/** AI 片段混音，播放线程不运行神经网络。 */
internal class StemAudioProcessor(private val controller: StemProcessingControl) : BaseAudioProcessor() {
    private var ai = false
    private var streaming: StemStreamingMixer? = null
    private var inputTimestampUs = 0L
    private var nextSourceFrame: Long? = null

    /** 音频接收器提供解码采样时间，跳转或循环后不再依赖 AI 提交次数定位缓存。 */
    fun setInputTimestampUs(presentationTimeUs: Long) {
        inputTimestampUs = presentationTimeUs.coerceAtLeast(0L)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val state = controller.state.value
        ai = state.enabled
        if (!state.enabled) return AudioProcessor.AudioFormat.NOT_SET
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            if (ai) controller.reportAiPhase(controller.aiInference, StemStreamPhase.UNSUPPORTED)
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        if (ai) {
            if (nextSourceFrame == null) {
                nextSourceFrame = (inputTimestampUs * inputAudioFormat.sampleRate + 500_000L) / 1_000_000L
            }
            val inference = controller.aiInference
            if (streaming == null && inference != null) {
                val rate = inputAudioFormat.sampleRate
                streaming = StemStreamingMixer(
                    channels = inputAudioFormat.channelCount,
                    inference = inference,
                    report = { controller.reportAiPhase(inference, it) },
                    reportError = { Log.w("StemAudioProcessor", "AI 片段推理失败，恢复原曲", it) },
                    chunkFrames = (MdxStemEngine.OUTPUT_FRAMES * (rate.toDouble() / MdxStemEngine.SAMPLE_RATE)).roundToInt().coerceAtLeast(1),
                    trimFrames = (MdxStemEngine.TRIM_FRAMES * (rate.toDouble() / MdxStemEngine.SAMPLE_RATE)).roundToInt().coerceAtLeast(1),
                    sampleRate = rate,
                    startFrame = nextSourceFrame!!,
                )
                controller.reportAiPhase(inference, StemStreamPhase.PROCESSING)
            }
            val current = streaming
            if (current != null) current.queueInput(inputBuffer)
            else {
                nextSourceFrame = nextSourceFrame!! + inputBuffer.remaining() / (inputAudioFormat.channelCount * 2)
                replaceOutputBuffer(inputBuffer.remaining()).apply { put(inputBuffer); flip() }
            }
            return
        }
    }

    override fun onFlush() {
        streaming?.reset()
        streaming = null
        nextSourceFrame = null
        if (ai && outputAudioFormat != AudioProcessor.AudioFormat.NOT_SET) {
            controller.reportAiPhase(controller.aiInference, StemStreamPhase.WAITING)
        }
    }

    override fun onQueueEndOfStream() {
        streaming?.finish()
    }

    override fun getOutput(): ByteBuffer {
        val output = super.getOutput()
        if (output.hasRemaining()) return output
        if (ai && streaming?.output(controller.state.value, ::replaceOutputBuffer) != null) return super.getOutput()
        return AudioProcessor.EMPTY_BUFFER
    }

    override fun isEnded(): Boolean = super.isEnded() && streaming?.isDrained != false

    override fun onReset() {
        streaming?.reset()
        streaming = null
        nextSourceFrame = null
        inputTimestampUs = 0L
        ai = false
    }
}
