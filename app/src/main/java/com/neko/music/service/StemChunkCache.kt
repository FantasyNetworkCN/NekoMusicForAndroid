package com.neko.music.service

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** 不可变的双声道 PCM 采样；以服务端原始的 16-bit 数据保存，内存占用仅为浮点数组的一半。 */
internal class StemCachedSamples private constructor(
    private val samples: ShortArray,
    private val offsetSamples: Int = 0,
    private val sampleCount: Int = samples.size,
    private val outputFrames: Int = sampleCount / 2,
) {
    val frames: Int get() = outputFrames

    fun sample(frame: Int, channel: Int): Float {
        val position = frame.toDouble() * (sampleCount / 2) / outputFrames
        val first = position.toInt().coerceAtMost(sampleCount / 2 - 1)
        val second = (first + 1).coerceAtMost(sampleCount / 2 - 1)
        val fraction = (position - first).toFloat()
        val a = samples[offsetSamples + first * 2 + channel]
        val b = samples[offsetSamples + second * 2 + channel]
        return (a + (b - a) * fraction) / 32768f
    }

    /** 采样率视图共享原始 PCM，不因 48kHz/Hi-Res 播放复制整曲数据。 */
    fun resampled(frames: Int): StemCachedSamples =
        StemCachedSamples(samples, offsetSamples, sampleCount, frames)

    /** 分片共享同一块底层数组，避免整曲回退时再复制一份完整采样。 */
    fun slice(startFrame: Int, frameCount: Int): StemCachedSamples {
        require(outputFrames == sampleCount / 2)
        require(startFrame >= 0 && frameCount > 0 && startFrame + frameCount <= frames)
        return StemCachedSamples(
            samples = samples,
            offsetSamples = offsetSamples + startFrame * 2,
            sampleCount = frameCount * 2,
        )
    }

    companion object {
        fun fromFloats(samples: FloatArray): StemCachedSamples {
            require(samples.size % 2 == 0) { "双声道采样长度必须为偶数" }
            return StemCachedSamples(
                samples = ShortArray(samples.size) { index ->
                    (samples[index].coerceIn(-1f, 1f) * 32767f)
                        .roundToInt()
                        .toShort()
                },
            )
        }

        /** 调用方必须保证 [samples] 不再被修改。 */
        fun fromShorts(samples: ShortArray): StemCachedSamples {
            require(samples.size % 2 == 0) { "双声道采样长度必须为偶数" }
            return StemCachedSamples(samples)
        }
    }
}

internal data class StemCachedChunk(
    val startFrame: Long,
    val vocals: StemCachedSamples,
    val accompaniment: StemCachedSamples,
) {
    val frames: Int get() = minOf(vocals.frames, accompaniment.frames)
    val endFrame: Long get() = startFrame + frames
}

/** 当前歌曲持有的服务端分离分片；切歌立即释放，不在音频线程读写磁盘。 */
internal class StemTrackCache(val identity: String) {
    val ready = CompletableFuture.completedFuture(Unit)
    @Volatile var released = false
        private set
    private val chunks = ConcurrentHashMap<Int, ConcurrentSkipListMap<Long, StemCachedChunk>>()

    fun chunkAt(sampleRate: Int, frame: Long): StemCachedChunk? {
        chunks[sampleRate]?.floorEntry(frame)?.value?.let {
            if (frame < it.endFrame) return it
        }
        val canonical = chunks[STEM_SAMPLE_RATE] ?: return null
        val canonicalFrame = frame * STEM_SAMPLE_RATE / sampleRate
        val chunk = canonical.floorEntry(canonicalFrame)?.value ?: return null
        val start = (chunk.startFrame.toDouble() * sampleRate / STEM_SAMPLE_RATE).roundToLong()
        val end = (chunk.endFrame.toDouble() * sampleRate / STEM_SAMPLE_RATE).roundToLong()
        if (frame < start || frame >= end) return null
        val view = StemCachedChunk(start, chunk.vocals.resampled((end - start).toInt()),
            chunk.accompaniment.resampled((end - start).toInt()))
        chunks.computeIfAbsent(sampleRate) { ConcurrentSkipListMap() }[start] = view
        return view
    }

    fun put(sampleRate: Int, chunk: StemCachedChunk) {
        if (released) return
        chunks.computeIfAbsent(sampleRate) { ConcurrentSkipListMap() }[chunk.startFrame] = chunk
        // 新分片覆盖同一位置时清理采样率视图，防止继续读取重试前的数据。
        if (sampleRate == STEM_SAMPLE_RATE) chunks.keys.filter { it != sampleRate }.forEach(chunks::remove)
        if (released) chunks.clear()
    }

    val chunkCount: Int get() = chunks[STEM_SAMPLE_RATE]?.size ?: 0

    fun retainAround(frame: Long) {
        val from = (frame - STEM_SAMPLE_RATE * 30L).coerceAtLeast(0)
        val to = frame + STEM_SAMPLE_RATE * 90L
        chunks[STEM_SAMPLE_RATE]?.entries?.removeIf { it.value.endFrame <= from || it.key > to }
        // 视图只共享同一份数组，但也必须释放窗口外的引用。
        chunks.keys.filter { it != STEM_SAMPLE_RATE }.forEach(chunks::remove)
    }

    fun release() {
        released = true
        chunks.clear()
        ready.complete(Unit)
    }
}

internal const val STEM_SAMPLE_RATE = 44_100

/** 两条解码任务可交错完成；只有双轨均存在时才移除并发布，避免单边提前丢失。 */
internal class StemPairAssembler(private val publish: (StemCachedChunk) -> Unit) {
    private val vocals = mutableMapOf<Long, StemCachedSamples>()
    private val accompaniment = mutableMapOf<Long, StemCachedSamples>()

    @Synchronized
    fun put(startFrame: Long, samples: StemCachedSamples, vocal: Boolean) {
        (if (vocal) vocals else accompaniment)[startFrame] = samples
        val voice = vocals[startFrame] ?: return
        val backing = accompaniment[startFrame] ?: return
        vocals.remove(startFrame)
        accompaniment.remove(startFrame)
        publish(StemCachedChunk(startFrame, voice, backing))
    }
}
