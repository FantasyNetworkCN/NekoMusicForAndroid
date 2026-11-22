package com.neko.music.service

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import kotlin.math.roundToInt

/** 不可变的双声道 PCM 采样；以服务端原始的 16-bit 数据保存，内存占用仅为浮点数组的一半。 */
internal class StemCachedSamples private constructor(
    private val samples: ShortArray,
    private val offsetSamples: Int = 0,
    private val sampleCount: Int = samples.size,
) {
    val frames: Int get() = sampleCount / 2

    fun sample(frame: Int, channel: Int): Float =
        samples[offsetSamples + frame * 2 + channel] / 32768f

    /** 分片共享同一块底层数组，避免整曲回退时再复制一份完整采样。 */
    fun slice(startFrame: Int, frameCount: Int): StemCachedSamples {
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

internal data class StemCachedChunk(val startFrame: Long, val samples: StemCachedSamples) {
    val endFrame: Long get() = startFrame + samples.frames
}

/** 当前歌曲持有的服务端分离分片；切歌立即释放，不在音频线程读写磁盘。 */
internal class StemTrackCache(val identity: String) {
    val ready = CompletableFuture<Unit>()
    @Volatile var released = false
        private set
    private val chunks = ConcurrentHashMap<Int, ConcurrentSkipListMap<Long, StemCachedChunk>>()

    fun chunkAt(sampleRate: Int, frame: Long): StemCachedChunk? {
        val chunk = chunks[sampleRate]?.floorEntry(frame)?.value ?: return null
        return chunk.takeIf { frame < it.endFrame }
    }

    fun put(sampleRate: Int, chunk: StemCachedChunk) {
        if (released) return
        chunks.computeIfAbsent(sampleRate) { ConcurrentSkipListMap() }[chunk.startFrame] = chunk
        if (released) chunks.clear()
    }

    val chunkCount: Int get() = chunks.values.sumOf { it.size }

    fun release() {
        released = true
        chunks.clear()
        ready.complete(Unit)
    }
}
