package com.neko.music.service

import java.nio.ByteBuffer
import kotlin.math.roundToInt

internal enum class StemStreamPhase {
    WAITING, PROCESSING, ACTIVE, FALLBACK, FAILED, UNSUPPORTED,
    LOGIN_REQUIRED, VIP_REQUIRED,
}

internal interface StemChunkSource {
    val cachePrepared: Boolean get() = true
    fun cachedAt(sampleRate: Int, frame: Long): StemCachedChunk?
}

/** 当前曲目只读视图；服务端分片会在播放过程中逐片补齐。 */
internal class StemTrackInference(private val track: StemTrackCache) : StemChunkSource {
    override fun cachedAt(sampleRate: Int, frame: Long): StemCachedChunk? =
        track.chunkAt(sampleRate, frame)
}

/**
 * 用服务端伴奏分片和播放器解码出的原曲 PCM 做本地混音。
 *
 * <p>分片尚未到达时当前 10ms 小块继续输出原曲；后续分片到达后无需重启播放器即可生效。</p>
 */
internal class StemStreamingMixer(
    private val channels: Int,
    private val source: StemChunkSource,
    private val report: (StemStreamPhase) -> Unit,
    private val sampleRate: Int,
    private val chunkFrames: Int,
    startFrame: Long = 0,
) {
    private companion object {
        const val MAX_BUFFERED_CHUNKS = 12
        const val OUTPUT_FRAMES = 1024
    }

    private class Chunk(
        val original: FloatArray,
        val cached: StemCachedChunk?,
        val cachedOffset: Int,
    ) {
        var offset = 0
        var decided = false
    }

    private val chunks = ArrayDeque<Chunk>(MAX_BUFFERED_CHUNKS)
    private var nextFrame = startFrame.coerceAtLeast(0)
    private var ended = false

    val isDrained: Boolean get() = chunks.isEmpty()

    fun queueInput(buffer: ByteBuffer) {
        if (!source.cachePrepared) return
        val frameBytes = channels * Short.SIZE_BYTES
        while (chunks.size < MAX_BUFFERED_CHUNKS && buffer.remaining() >= frameBytes) {
            val cached = source.cachedAt(sampleRate, nextFrame)
            val boundary = if (cached != null) {
                (cached.endFrame - nextFrame).coerceAtLeast(1L)
            } else {
                (chunkFrames - (nextFrame % chunkFrames)).coerceAtLeast(1L)
            }
            val availableFrames = buffer.remaining() / frameBytes
            val count = minOf(OUTPUT_FRAMES.toLong(), boundary, availableFrames.toLong()).toInt()
            if (count <= 0) break
            val original = FloatArray(count * 2)
            repeat(count) {
                val left = buffer.short / 32768f
                val right = if (channels == 2) buffer.short / 32768f else left
                original[it * 2] = left
                original[it * 2 + 1] = right
            }
            chunks.addLast(Chunk(
                original = original,
                cached = cached,
                cachedOffset = cached?.let { (nextFrame - it.startFrame).toInt() } ?: 0,
            ))
            nextFrame += count
        }
    }

    fun finish() {
        ended = true
    }

    fun output(gains: StemSeparationState, allocate: (Int) -> ByteBuffer): ByteBuffer? {
        val chunk = chunks.firstOrNull() ?: return null
        if (!chunk.decided) {
            chunk.decided = true
            report(if (chunk.cached != null) StemStreamPhase.ACTIVE else StemStreamPhase.FALLBACK)
        }
        val frames = minOf(OUTPUT_FRAMES, chunk.original.size / 2 - chunk.offset)
        val output = allocate(frames * channels * Short.SIZE_BYTES)
        for (frame in chunk.offset until chunk.offset + frames) {
            repeat(channels) { channel ->
                val index = frame * 2 + channel
                val original = chunk.original[index]
                val instrumental = chunk.cached?.samples?.sample(chunk.cachedOffset + frame, channel)
                val mixed = if (instrumental == null) original else
                    mixStemSample(original, instrumental, gains.vocals, gains.instrumental)
                output.putShort((mixed.coerceIn(-1f, 1f) * 32768f).roundToInt()
                    .coerceIn(-32768, 32767).toShort())
            }
        }
        output.flip()
        chunk.offset += frames
        if (chunk.offset == chunk.original.size / 2) chunks.removeFirst()
        return output
    }

    fun reset() {
        chunks.clear()
        ended = false
    }
}

internal fun resampleStereo(input: FloatArray, outputFrames: Int): FloatArray {
    require(input.size >= 2 && input.size % 2 == 0 && outputFrames > 0)
    val inputFrames = input.size / 2
    if (inputFrames == outputFrames) return input.copyOf()
    return FloatArray(outputFrames * 2) { index ->
        val position = (index / 2).toDouble() * inputFrames / outputFrames
        val first = position.toInt().coerceAtMost(inputFrames - 1)
        val second = (first + 1).coerceAtMost(inputFrames - 1)
        val fraction = (position - first).toFloat()
        val channel = index % 2
        input[first * 2 + channel] * (1f - fraction) + input[second * 2 + channel] * fraction
    }
}

/** 两路均为 100% 时逐样本恢复原声，避免分离残差改变默认听感。 */
internal fun mixStemSample(original: Float, instrumental: Float, vocals: Float, backing: Float): Float =
    if (vocals == backing) original * vocals else
        (original - instrumental) * vocals + instrumental * backing
