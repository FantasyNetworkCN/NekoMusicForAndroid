@file:Suppress("DEPRECATION")

package com.neko.music.service

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.BaseAudioProcessor
import com.neko.music.R
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** 解码线程只负责收集与混音，模型推理在单独的有界后台任务中执行。 */
internal class StemAudioProcessor(private val controller: StemProcessingControl) : BaseAudioProcessor() {
    private data class SeparatedChunk(val original: FloatArray, val instrumental: FloatArray)
    private val executor = ThreadPoolExecutor(
        0, 1, 5, TimeUnit.SECONDS, LinkedBlockingQueue(),
        { task -> Thread(task, "Neko音源分离").apply { isDaemon = true } },
    )
    private val ready = ArrayBlockingQueue<SeparatedChunk>(2)
    @Volatile private var generation = 0
    @Volatile private var pendingJobs = 0
    private var pending = FloatArray(0)
    private var previous = FloatArray(0)
    private var collectedFrames = 0
    private var chunkFrames = 0
    private var trimFrames = 0
    private var current: SeparatedChunk? = null
    private var outputFrame = 0
    private var ending = false
    private var slowChunks = 0
    private var streamEngine: StemInference? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        synchronized(this) {
            generation++
            executor.queue.clear()
            pendingJobs = 0
            ready.clear()
        }
        current = null
        outputFrame = 0
        collectedFrames = 0
        ending = false
        slowChunks = 0
        streamEngine = controller.engine.takeIf { inputAudioFormat.sampleRate > 0 }
        if (streamEngine != null && inputAudioFormat.channelCount !in 1..2) {
            controller.fail(streamEngine!!, R.string.stem_format_unsupported)
            streamEngine = null
        }
        chunkFrames = (MdxStemEngine.OUTPUT_FRAMES.toDouble() * inputAudioFormat.sampleRate / MdxStemEngine.SAMPLE_RATE)
            .roundToInt().coerceAtLeast(1)
        trimFrames = (MdxStemEngine.TRIM_FRAMES.toDouble() * inputAudioFormat.sampleRate / MdxStemEngine.SAMPLE_RATE)
            .roundToInt().coerceAtLeast(1)
        pending = if (streamEngine != null) FloatArray((chunkFrames + trimFrames) * 2) else FloatArray(0)
        previous = if (streamEngine != null) FloatArray(trimFrames * 2) else FloatArray(0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // 管线会用空输入轮询输出，不能重置待输出数据或向共享空缓冲区自身写入。
        if (!inputBuffer.hasRemaining()) return
        if (streamEngine == null) {
            val output = replaceOutputBuffer(inputBuffer.remaining())
            output.put(inputBuffer).flip()
            return
        }
        // 最多保留两个分块；不消费输入即向 ExoPlayer 施加背压。
        if (pendingJobs + ready.size + (if (current != null) 1 else 0) >= 2) return
        val channels = inputAudioFormat.channelCount
        while (inputBuffer.remaining() >= channels * 2 && collectedFrames < chunkFrames + trimFrames) {
            val left = inputBuffer.short / 32768f
            val right = if (channels == 2) inputBuffer.short / 32768f else left
            pending[collectedFrames * 2] = left
            pending[collectedFrames * 2 + 1] = right
            collectedFrames++
        }
        if (collectedFrames == chunkFrames + trimFrames) submitChunk(chunkFrames)
    }

    private fun submitChunk(frames: Int) {
        val engine = streamEngine ?: return
        val context = FloatArray((trimFrames * 2 + chunkFrames) * 2)
        previous.copyInto(context)
        pending.copyInto(context, previous.size, 0, collectedFrames * 2)
        val original = pending.copyOf(frames * 2)
        val padding = trimFrames.coerceAtMost(frames)
        previous.fill(0f)
        pending.copyInto(previous, previous.size - padding * 2, (frames - padding) * 2, frames * 2)
        val remaining = collectedFrames - frames
        pending.copyInto(pending, 0, frames * 2, collectedFrames * 2)
        collectedFrames = remaining
        val token: Int
        val duration = chunkFrames.toDouble() / inputAudioFormat.sampleRate
        val expectedOutputFrames = chunkFrames
        synchronized(this) {
            token = generation
            pendingJobs++
        }
        executor.execute {
            try {
                if (token != generation || controller.engine !== engine) return@execute
                val started = System.nanoTime()
                val backing = engine.instrumental(context, expectedOutputFrames).copyOf(frames * 2)
                val elapsed = (System.nanoTime() - started) / 1_000_000_000.0
                synchronized(this) {
                    if (token != generation) return@execute
                    slowChunks = if (elapsed > duration / controller.playbackSpeed.coerceAtLeast(0.1f)) slowChunks + 1 else 0
                    if (slowChunks >= 2) {
                        controller.fail(engine, R.string.stem_device_too_slow)
                    }
                    ready.offer(SeparatedChunk(original, backing))
                }
            } catch (error: Exception) {
                synchronized(this) {
                    if (token == generation) {
                        ready.offer(SeparatedChunk(original, FloatArray(original.size)))
                        controller.fail(engine, R.string.stem_processing_failed, error)
                    }
                }
            } finally {
                synchronized(this) { if (token == generation) pendingJobs-- }
            }
        }
    }

    override fun getOutput(): ByteBuffer {
        if (streamEngine == null) return super.getOutput()
        if (current == null) current = ready.poll()
        if (ending && collectedFrames > 0 && pendingJobs + ready.size < 2) {
            submitChunk(collectedFrames.coerceAtMost(chunkFrames))
        }
        val chunk = current ?: return AudioProcessor.EMPTY_BUFFER
        val frames = minOf(1024, chunk.original.size / 2 - outputFrame)
        val channels = inputAudioFormat.channelCount
        val output = replaceOutputBuffer(frames * channels * 2)
        val state = controller.state.value
        for (frame in outputFrame until outputFrame + frames) {
            for (channel in 0 until channels) {
                val index = frame * 2 + channel
                val value = mixStemSample(chunk.original[index], chunk.instrumental[index], state.vocals, state.instrumental)
                output.putShort((value * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
            }
        }
        output.flip()
        outputFrame += frames
        if (outputFrame == chunk.original.size / 2) {
            current = null
            outputFrame = 0
        }
        return super.getOutput()
    }

    override fun onQueueEndOfStream() {
        ending = true
    }

    override fun isEnded(): Boolean = super.isEnded() &&
        (streamEngine == null || (collectedFrames == 0 && pendingJobs == 0 && ready.isEmpty() && current == null))

    override fun onReset() {
        pending = FloatArray(0)
        previous = FloatArray(0)
        streamEngine = null
    }
}
