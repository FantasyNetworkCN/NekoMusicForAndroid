package com.neko.music.service

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap

/** 不可变的双声道 PCM 采样。服务端分片解码后以浮点保存在当前曲目内存。 */
internal class StemCachedSamples private constructor(private val samples: FloatArray) {
    val frames: Int get() = samples.size / 2

    fun sample(frame: Int, channel: Int): Float = samples[frame * 2 + channel]

    fun slice(startFrame: Int, frameCount: Int): StemCachedSamples {
        require(startFrame >= 0 && frameCount > 0 && startFrame + frameCount <= frames)
        return fromFloats(samples.copyOfRange(startFrame * 2, (startFrame + frameCount) * 2))
    }

    companion object {
        fun fromFloats(samples: FloatArray): StemCachedSamples {
            require(samples.size % 2 == 0) { "双声道采样长度必须为偶数" }
            return StemCachedSamples(samples.copyOf())
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
