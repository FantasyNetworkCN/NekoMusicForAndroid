@file:Suppress("DEPRECATION")

package com.neko.music.service

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlinx.coroutines.flow.StateFlow

internal interface StemProcessingControl {
    val state: StateFlow<StemSeparationState>
    val stemSource: StemChunkSource? get() = null
    fun reportStemPhase(source: StemChunkSource?, phase: StemStreamPhase) {}
    fun setPlaybackSampleRate(sampleRate: Int) {}
}

/** 服务端伴奏分片混音；音频线程只做缓存采样读取和加减，不发网络请求。 */
internal class StemAudioProcessor(private val controller: StemProcessingControl) : BaseAudioProcessor() {
    private var enabled = false
    private var streaming: StemStreamingMixer? = null
    private var source: StemChunkSource? = null
    private var inputTimestampUs = 0L
    private var nextSourceFrame: Long? = null

    fun setInputTimestampUs(presentationTimeUs: Long) {
        inputTimestampUs = presentationTimeUs.coerceAtLeast(0L)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        enabled = controller.state.value.enabled
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            if (enabled) controller.reportStemPhase(source, StemStreamPhase.UNSUPPORTED)
            return AudioProcessor.AudioFormat.NOT_SET
        }
        controller.setPlaybackSampleRate(inputAudioFormat.sampleRate)
        source = controller.stemSource
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val stateEnabled = controller.state.value.enabled
        if (!stateEnabled) {
            if (enabled) {
                streaming?.reset()
                streaming = null
                nextSourceFrame = null
            }
            enabled = false
            replaceOutputBuffer(inputBuffer.remaining()).apply { put(inputBuffer); flip() }
            return
        }
        enabled = true

        val currentSource = controller.stemSource
        if (currentSource !== source) {
            streaming?.reset()
            streaming = null
            source = currentSource
        }
        if (nextSourceFrame == null) {
            nextSourceFrame = (inputTimestampUs * inputAudioFormat.sampleRate + 500_000L) / 1_000_000L
        }
        if (streaming == null && currentSource != null) {
            val rate = inputAudioFormat.sampleRate
            streaming = StemStreamingMixer(
                channels = inputAudioFormat.channelCount,
                source = currentSource,
                report = { controller.reportStemPhase(currentSource, it) },
                sampleRate = rate,
                chunkFrames = (SERVER_STEM_CHUNK_SECONDS * rate).toInt().coerceAtLeast(1),
                startFrame = nextSourceFrame!!,
            )
            controller.reportStemPhase(currentSource, StemStreamPhase.PROCESSING)
        }
        val current = streaming
        if (current == null) {
            nextSourceFrame = nextSourceFrame!! +
                inputBuffer.remaining() / (inputAudioFormat.channelCount * Short.SIZE_BYTES)
            replaceOutputBuffer(inputBuffer.remaining()).apply { put(inputBuffer); flip() }
            return
        }
        current.queueInput(inputBuffer)
    }

    override fun onFlush() {
        streaming?.reset()
        streaming = null
        source = null
        nextSourceFrame = null
        if (enabled && outputAudioFormat != AudioProcessor.AudioFormat.NOT_SET) {
            controller.reportStemPhase(controller.stemSource, StemStreamPhase.WAITING)
        }
    }

    override fun onQueueEndOfStream() {
        streaming?.finish()
    }

    override fun getOutput(): ByteBuffer {
        val output = super.getOutput()
        if (output.hasRemaining()) return output
        if (enabled && streaming?.output(controller.state.value, ::replaceOutputBuffer) != null) {
            return super.getOutput()
        }
        return AudioProcessor.EMPTY_BUFFER
    }

    override fun isEnded(): Boolean = super.isEnded() && streaming?.isDrained != false

    override fun onReset() {
        streaming?.reset()
        streaming = null
        source = null
        nextSourceFrame = null
        inputTimestampUs = 0L
        enabled = false
    }

    companion object {
        /** 与服务端 30 秒分片对齐；仅用于缺失区间按片回退原曲。 */
        const val SERVER_STEM_CHUNK_SECONDS = 30.0
    }
}
