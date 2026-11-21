package com.neko.music.service

import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

internal enum class StemStreamPhase { WAITING, PROCESSING, ACTIVE, FALLBACK, FAILED, UNSUPPORTED }

internal fun interface StemChunkInference {
    fun submit(context: FloatArray, frames: Int): Future<FloatArray>?
}

/** 单线程复用已加载模型，保留一个运行中的推理和一个候选任务。 */
internal class StemInferenceWorker(createEngine: () -> StemInference) : StemChunkInference, Closeable {
    private val engine = AtomicReference<StemInference?>()
    private val executor = ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(1)) { run ->
        Thread(run, "NekoStemInference").apply { isDaemon = true }
    }
    private val factory = createEngine

    override fun submit(context: FloatArray, frames: Int): Future<FloatArray>? {
        executor.purge()
        return try {
            executor.submit<FloatArray> {
                val current = engine.get() ?: factory().also { engine.compareAndSet(null, it) }
                current.instrumental(context, frames)
            }
        } catch (_: RejectedExecutionException) {
            null
        }
    }

    override fun close() {
        executor.shutdownNow().forEach { (it as? Future<*>)?.cancel(false) }
        engine.getAndSet(null)?.let { inference ->
            // 模型推理可能仍在播放线程外的任务中运行，关闭放到后台等待，避免切模式卡住界面。
            Thread({
                runCatching { inference.close() }
            }, "NekoStemInferenceClose").apply { isDaemon = true }.start()
        }
    }
}

/** 消费播放器的 PCM，按固定顺序混音。推理结果暂存到对应片段播放结束。 */
internal class StemStreamingMixer(
    private val channels: Int,
    private val inference: StemChunkInference,
    private val report: (StemStreamPhase) -> Unit,
    private val reportError: (Throwable) -> Unit,
    private val chunkFrames: Int,
    private val trimFrames: Int,
    private val clock: () -> Long = System::nanoTime,
    private val maxWaitNanos: Long = TimeUnit.MILLISECONDS.toNanos(120),
) {
    private class Chunk(val original: FloatArray, val result: Future<FloatArray>?) {
        var waitStarted: Long? = null
        var offset = 0
        var backing: FloatArray? = null
        var decided = false
    }

    private val chunks = ArrayDeque<Chunk>(2)
    private val input = FloatArray((chunkFrames + trimFrames) * 2)
    private val history = FloatArray(trimFrames * 2)
    private var collectedFrames = 0
    private var failed = false
    private var ended = false
    val isDrained: Boolean get() = chunks.isEmpty() && collectedFrames == 0

    fun queueInput(buffer: ByteBuffer) {
        val frameBytes = channels * 2
        while (buffer.remaining() >= frameBytes && chunks.size < 2) {
            val left = buffer.short / 32768f
            val right = if (channels == 2) buffer.short / 32768f else left
            input[collectedFrames * 2] = left
            input[collectedFrames * 2 + 1] = right
            collectedFrames++
            if (collectedFrames == chunkFrames + trimFrames) enqueue(chunkFrames)
        }
    }

    private fun enqueue(frames: Int) {
        val original = input.copyOf(frames * 2)
        val result = if (failed) null else {
            val context = FloatArray((chunkFrames + trimFrames * 2) * 2)
            history.copyInto(context)
            input.copyInto(context, history.size, endIndex = collectedFrames * 2)
            inference.submit(context, chunkFrames)
        }
        val previousSamples = minOf(history.size, original.size)
        original.copyInto(history, history.size - previousSamples, original.size - previousSamples)
        input.copyInto(input, startIndex = frames * 2, endIndex = collectedFrames * 2)
        collectedFrames -= frames
        chunks.addLast(Chunk(original, result))
    }

    fun finish() {
        ended = true
    }

    fun output(gains: StemSeparationState, allocate: (Int) -> ByteBuffer): ByteBuffer? {
        if (ended && collectedFrames > 0 && chunks.size < 2) enqueue(minOf(collectedFrames, chunkFrames))
        val chunk = chunks.firstOrNull() ?: return null
        if (!chunk.decided) {
            val result = chunk.result
            if (result != null && !result.isDone) {
                val now = clock()
                val started = chunk.waitStarted ?: now.also { chunk.waitStarted = it }
                if (now - started < maxWaitNanos) {
                    report(StemStreamPhase.PROCESSING)
                    return null
                }
                result.cancel(false)
            } else if (result != null && !result.isCancelled) {
                try {
                    chunk.backing = result.get().also { check(it.size >= chunk.original.size) }
                } catch (error: ExecutionException) {
                    failed = true
                    reportError(error.cause ?: error)
                } catch (error: IllegalStateException) {
                    failed = true
                    reportError(error)
                } catch (_: java.util.concurrent.CancellationException) {
                    // 切换模式时可能与推理完成同时发生，继续输出原曲。
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            chunk.decided = true
            report(when {
                failed -> StemStreamPhase.FAILED
                chunk.backing != null -> StemStreamPhase.ACTIVE
                else -> StemStreamPhase.FALLBACK
            })
        }
        val backing = chunk.backing
        val frames = minOf(1024, chunk.original.size / 2 - chunk.offset)
        val output = allocate(frames * channels * 2)
        for (frame in chunk.offset until chunk.offset + frames) {
            repeat(channels) { channel ->
                val sample = frame * 2 + channel
                val original = chunk.original[sample]
                val mixed = if (backing == null) original else
                    mixStemSample(original, backing[sample], gains.vocals, gains.instrumental)
                output.putShort((mixed * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
            }
        }
        output.flip()
        chunk.offset += frames
        if (chunk.offset == chunk.original.size / 2) chunks.removeFirst()
        return output
    }

    fun reset() {
        chunks.forEach { it.result?.cancel(false) }
        chunks.clear()
        collectedFrames = 0
    }
}
