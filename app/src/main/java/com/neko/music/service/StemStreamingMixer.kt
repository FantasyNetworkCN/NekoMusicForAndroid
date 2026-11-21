package com.neko.music.service

import android.os.Process
import com.neko.music.debug.NekoLog as Log
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

internal enum class StemStreamPhase { WAITING, PROCESSING, ACTIVE, FALLBACK, FAILED, UNSUPPORTED }

internal fun interface StemChunkInference {
    fun submit(context: FloatArray, frames: Int): Future<FloatArray>?
    val cachePrepared: Boolean get() = true
    val canInfer: Boolean get() = true
    fun cachedAt(sampleRate: Int, frame: Long): StemCachedChunk? = null
    fun submitAt(context: FloatArray, frames: Int, sampleRate: Int, startFrame: Long): Future<FloatArray>? =
        submit(context, frames)
}

/** 曲目引用不可变，模型晚于缓存就绪时也能立即播放已有分离数据。 */
internal class StemTrackInference(
    private val track: StemTrackCache,
    private val worker: () -> StemInferenceWorker?,
) : StemChunkInference {
    override val cachePrepared: Boolean get() = track.ready.isDone
    override val canInfer: Boolean get() = worker() != null
    override fun cachedAt(sampleRate: Int, frame: Long): StemCachedChunk? = track.chunkAt(sampleRate, frame)
    override fun submit(context: FloatArray, frames: Int): Future<FloatArray>? = worker()?.submit(context, frames)
    override fun submitAt(context: FloatArray, frames: Int, sampleRate: Int, startFrame: Long): Future<FloatArray>? =
        worker()?.submitForTrack(context, frames, track, sampleRate, startFrame)
}

/** 推理、查盘和写盘独立于音频线程；结果先交付播放，再在后台保存。 */
internal class StemInferenceWorker(
    createEngine: (StemBackendPreference) -> StemInference,
    private val chunkCache: StemChunkCache? = null,
) : StemChunkInference, Closeable {
    private val engine = AtomicReference<StemInference?>()
    @Volatile private var preference = StemBackendPreference.AUTO
    private val executor = ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(5)) { run ->
        Thread({
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE) }
            run.run()
        }, "NekoStemInference").apply { isDaemon = true }
    }
    private val cacheExecutor = Executors.newFixedThreadPool(2) { run ->
        Thread(run, "NekoStemCache").apply { isDaemon = true }
    }
    private val pending = ConcurrentHashMap.newKeySet<CompletableFuture<FloatArray>>()
    private val factory = createEngine
    private val submitted = java.util.concurrent.atomic.AtomicLong()

    override fun submit(context: FloatArray, frames: Int): Future<FloatArray> =
        submitForTrack(context, frames, null, 0, 0)

    fun submitForTrack(
        context: FloatArray,
        frames: Int,
        track: StemTrackCache?,
        sampleRate: Int,
        startFrame: Long,
    ): Future<FloatArray> {
        val result = CompletableFuture<FloatArray>()
        pending.add(result)
        result.whenComplete { _, _ -> pending.remove(result) }
        try {
            cacheExecutor.execute {
                try {
                    if (result.isDone) return@execute
                    val key = chunkCache?.key(context)
                    val cached = key?.let { chunkCache.read(it, frames) }
                    if (cached != null) {
                        track?.put(sampleRate, StemCachedChunk(startFrame, cached))
                        result.complete(cached.toFloatArray())
                        persist(key, cached, track, sampleRate, startFrame)
                    } else {
                        submitInference(context, frames, key, track, sampleRate, startFrame, result)
                    }
                } catch (error: Exception) {
                    result.completeExceptionally(error)
                }
            }
        } catch (error: RejectedExecutionException) {
            result.completeExceptionally(error)
        }
        return result
    }

    private fun submitInference(
        context: FloatArray,
        frames: Int,
        key: String?,
        track: StemTrackCache?,
        sampleRate: Int,
        startFrame: Long,
        result: CompletableFuture<FloatArray>,
    ) {
        if (result.isDone) return
        val sequence = submitted.incrementAndGet()
        try {
            executor.execute {
                if (result.isDone) return@execute
                var current: StemInference? = null
                try {
                    val started = System.nanoTime()
                    current = engine.get() ?: factory(preference).also { engine.set(it) }
                    val output = current.instrumental(context, frames)
                    val samples = StemCachedSamples.fromFloats(output.copyOf(frames * 2))
                    track?.put(sampleRate, StemCachedChunk(startFrame, samples))
                    result.complete(samples.toFloatArray())
                    if (key != null) persist(key, samples, track, sampleRate, startFrame)
                    runCatching {
                        val elapsedMs = (System.nanoTime() - started) / 1_000_000
                        Log.d("StemInferenceWorker", "AI 分片 #$sequence 完成，耗时=${elapsedMs}ms，后端=${current.backendName}")
                    }
                } catch (error: Exception) {
                    if (current != null && engine.compareAndSet(current, null)) {
                        preference = when (preference) {
                            StemBackendPreference.AUTO -> StemBackendPreference.NO_GPU
                            else -> StemBackendPreference.CPU_ONLY
                        }
                        runCatching { current.close() }
                    }
                    result.completeExceptionally(error)
                    runCatching { Log.w("StemInferenceWorker", "AI 分片 #$sequence 失败，将降级后端", error) }
                }
            }
        } catch (error: RejectedExecutionException) {
            result.completeExceptionally(error)
        }
    }

    private fun persist(key: String, samples: StemCachedSamples, track: StemTrackCache?, rate: Int, start: Long) {
        try {
            cacheExecutor.execute {
                runCatching { chunkCache?.write(key, samples, track, rate, start) }
                    .onFailure { runCatching { Log.w("StemInferenceWorker", "分离缓存写入失败，当前播放继续使用内存", it) } }
            }
        } catch (_: RejectedExecutionException) {
            // 关闭期间已交付的音频继续有效，不再提交新的磁盘任务。
        }
    }

    fun prewarm(frames: Int): Future<FloatArray> {
        val result = CompletableFuture<FloatArray>()
        submitInference(FloatArray(frames * 2), frames, null, null, 0, 0, result)
        return result
    }

    override fun close() {
        pending.forEach { it.cancel(false) }
        executor.shutdownNow()
        cacheExecutor.shutdownNow()
        engine.getAndSet(null)?.let { inference ->
            Thread({ runCatching { inference.close() } }, "NekoStemInferenceClose")
                .apply { isDaemon = true }.start()
        }
    }
}

/** 已缓存范围按小块直接混音；仅缺失范围收集模型上下文并异步补全。 */
internal class StemStreamingMixer(
    private val channels: Int,
    private val inference: StemChunkInference,
    private val report: (StemStreamPhase) -> Unit,
    private val reportError: (Throwable) -> Unit,
    private val chunkFrames: Int,
    private val trimFrames: Int,
    private val clock: () -> Long = System::nanoTime,
    private val maxWaitNanos: Long = TimeUnit.MILLISECONDS.toNanos(400),
    private val initialWaitNanos: Long = TimeUnit.MILLISECONDS.toNanos(4000),
    private val sampleRate: Int = MdxStemEngine.SAMPLE_RATE,
    startFrame: Long = 0,
) {
    private companion object {
        const val MAX_BUFFERED_CHUNKS = 6
        const val OUTPUT_FRAMES = 1024
    }

    private class Chunk(
        val original: FloatArray,
        val result: Future<FloatArray>?,
        val cached: StemCachedChunk? = null,
        val cachedOffset: Int = 0,
    ) {
        var waitStarted: Long? = null
        var offset = 0
        var backing: FloatArray? = null
        var error: Throwable? = null
        var decided = false
    }

    private val chunks = ArrayDeque<Chunk>(MAX_BUFFERED_CHUNKS)
    private val input = FloatArray((chunkFrames + trimFrames) * 2)
    private val history = FloatArray(trimFrames * 2)
    private var collectedFrames = 0
    private var nextFrame = startFrame.coerceAtLeast(0)
    private var ended = false
    private var firstChunkDecided = false
    val isDrained: Boolean get() = chunks.isEmpty() && collectedFrames == 0

    fun queueInput(buffer: ByteBuffer) {
        // 整曲已有缓存一次准备完成才接收第一块 PCM，起播后绝不逐片读盘。
        if (!inference.cachePrepared) return
        pump(buffer)
    }

    private fun pump(buffer: ByteBuffer) {
        while (chunks.size < MAX_BUFFERED_CHUNKS) {
            if (collectedFrames == 0 && buffer.remaining() < channels * 2) break
            val cached = inference.cachedAt(sampleRate, nextFrame)
            if (cached != null) {
                val count = minOf(OUTPUT_FRAMES.toLong(), input.size / 2L, cached.endFrame - nextFrame).toInt()
                collect(buffer, count)
                val available = minOf(count, collectedFrames)
                if (available == 0) break
                chunks.addLast(Chunk(input.copyOf(available * 2), null, cached,
                    (nextFrame - cached.startFrame).toInt()))
                consume(available)
                continue
            }

            // 从歌曲中间起播时，先输出到固定分片边界；已有缓存仍可从任意采样位置直接播放。
            val offset = (nextFrame % chunkFrames).toInt()
            if (offset != 0 || !inference.canInfer) {
                val count = minOf(OUTPUT_FRAMES, chunkFrames - offset)
                collect(buffer, count)
                val available = minOf(count, collectedFrames)
                if (available == 0) break
                chunks.addLast(Chunk(input.copyOf(available * 2), null))
                consume(available)
                continue
            }

            collect(buffer, chunkFrames + trimFrames)
            if (collectedFrames == chunkFrames + trimFrames) {
                enqueueMissing(chunkFrames)
            } else if (ended && collectedFrames > 0) {
                enqueueMissing(minOf(collectedFrames, chunkFrames))
            } else {
                break
            }
        }
    }

    private fun collect(buffer: ByteBuffer, target: Int) {
        val frameBytes = channels * 2
        while (collectedFrames < target && buffer.remaining() >= frameBytes) {
            val left = buffer.short / 32768f
            val right = if (channels == 2) buffer.short / 32768f else left
            input[collectedFrames * 2] = left
            input[collectedFrames * 2 + 1] = right
            collectedFrames++
        }
    }

    private fun enqueueMissing(frames: Int) {
        val original = input.copyOf(frames * 2)
        val context = FloatArray((chunkFrames + trimFrames * 2) * 2)
        history.copyInto(context)
        input.copyInto(context, history.size, endIndex = collectedFrames * 2)
        val result = inference.submitAt(context, chunkFrames, sampleRate, nextFrame)
        chunks.addLast(Chunk(original, result))
        consume(frames)
    }

    private fun consume(frames: Int) {
        if (trimFrames > 0) {
            val previous = minOf(trimFrames, frames)
            history.copyInto(history, startIndex = previous * 2)
            input.copyInto(history, history.size - previous * 2,
                (frames - previous) * 2, frames * 2)
        }
        input.copyInto(input, startIndex = frames * 2, endIndex = collectedFrames * 2)
        collectedFrames -= frames
        nextFrame += frames
    }

    fun finish() {
        ended = true
    }

    fun output(gains: StemSeparationState, allocate: (Int) -> ByteBuffer): ByteBuffer? {
        if (ended) pump(ByteBuffer.allocate(0))
        val chunk = chunks.firstOrNull() ?: return null
        if (!chunk.decided) {
            val result = chunk.result
            if (chunk.cached == null && result != null && !result.isDone) {
                val now = clock()
                val started = chunk.waitStarted ?: now.also { chunk.waitStarted = it }
                if (now - started < if (firstChunkDecided) maxWaitNanos else initialWaitNanos) {
                    report(StemStreamPhase.PROCESSING)
                    return null
                }
                // 只对未计算的范围有限等待；任务继续在后台补全缓存。
            } else if (result != null && !result.isCancelled) {
                try {
                    chunk.backing = result.get().also { check(it.size >= chunk.original.size) }
                } catch (error: ExecutionException) {
                    chunk.error = error.cause ?: error
                    reportError(chunk.error!!)
                } catch (error: IllegalStateException) {
                    chunk.error = error
                    reportError(error)
                } catch (_: java.util.concurrent.CancellationException) {
                    // 切换模式时继续输出原曲。
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            chunk.decided = true
            firstChunkDecided = true
            report(when {
                chunk.cached != null || chunk.backing != null -> StemStreamPhase.ACTIVE
                chunk.error != null -> StemStreamPhase.FAILED
                else -> StemStreamPhase.FALLBACK
            })
        }
        val backing = chunk.backing
        val frames = minOf(OUTPUT_FRAMES, chunk.original.size / 2 - chunk.offset)
        val output = allocate(frames * channels * 2)
        for (frame in chunk.offset until chunk.offset + frames) {
            repeat(channels) { channel ->
                val index = frame * 2 + channel
                val original = chunk.original[index]
                val instrumental = chunk.cached?.samples?.sample(chunk.cachedOffset + frame, channel)
                    ?: backing?.get(index)
                val mixed = if (instrumental == null) original else
                    mixStemSample(original, instrumental, gains.vocals, gains.instrumental)
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
        firstChunkDecided = false
    }
}
