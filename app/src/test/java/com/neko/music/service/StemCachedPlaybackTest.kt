@file:Suppress("DEPRECATION")

package com.neko.music.service

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessingPipeline
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.common.collect.ImmutableList
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class StemCachedPlaybackTest {
    private fun pcm(frames: Int, channels: Int = 2): ByteBuffer =
        ByteBuffer.allocateDirect(frames * channels * 2).order(ByteOrder.nativeOrder()).apply {
            repeat(frames * channels) { putShort(6000) }
            flip()
        }

    private fun drain(pipeline: AudioProcessingPipeline, output: ByteArrayOutputStream) {
        while (true) {
            val buffer = pipeline.output
            if (!buffer.hasRemaining()) return
            output.write(ByteArray(buffer.remaining()).also(buffer::get))
        }
    }

    @Test fun actualExoPipelineOutputsTenMillisecondsImmediatelyFromPreparedTrackWithoutDiskOrAi() {
        val directory = Files.createTempDirectory("stem-cached-pipeline").toFile()
        try {
            val disk = StemChunkCache(directory)
            val track = StemTrackCache("music:prepared:hq")
            val frames = MdxStemEngine.OUTPUT_FRAMES
            val samples = FloatArray(frames * 2) { 2000 / 32768f }
            disk.write(disk.key(samples), StemCachedSamples.fromFloats(samples), track, 44_100, 0)
            disk.prepareTrack(track)
            directory.listFiles()!!.forEach { assertTrue(it.delete()) }
            val inference = StemTrackInference(track) { error("完整缓存播放不应请求 AI") }
            val state = MutableStateFlow(StemSeparationState(enabled = true, realtime = false, vocals = 0f))
            val control = object : StemProcessingControl {
                override val state = state
                override val aiInference = inference
            }
            val pipeline = AudioProcessingPipeline(ImmutableList.of<AudioProcessor>(StemAudioProcessor(control)))
            pipeline.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
            pipeline.flush()
            try {
                val first = pcm(441)
                pipeline.queueInput(first)
                assertFalse(first.hasRemaining())
                val output = pipeline.output
                assertEquals("10ms 输入必须立即输出，不得等待 2.8 秒模型窗口", 441 * 4, output.remaining())
                while (output.hasRemaining()) assertEquals(2000, output.short.toInt())
                state.value = state.value.copy(vocals = 1f, instrumental = 0f)
                pipeline.queueInput(pcm(441))
                val adjusted = pipeline.output
                assertEquals(441 * 4, adjusted.remaining())
                while (adjusted.hasRemaining()) assertEquals(4000, adjusted.short.toInt())
            } finally { pipeline.reset() }
        } finally { directory.deleteRecursively() }
    }

    @Test fun firstPcmWaitsForWholeTrackPreparationRatherThanPlayingBeforeRemainingCacheIsLoaded() {
        val track = StemTrackCache("preparing")
        track.put(44_100, StemCachedChunk(0, StemCachedSamples.fromFloats(FloatArray(2000) { 0.1f })))
        val inference = StemTrackInference(track) { error("缓存准备阶段不能计算 AI") }
        val control = object : StemProcessingControl {
            override val state = MutableStateFlow(StemSeparationState(enabled = true, realtime = false))
            override val aiInference = inference
        }
        val pipeline = AudioProcessingPipeline(ImmutableList.of<AudioProcessor>(StemAudioProcessor(control)))
        pipeline.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        pipeline.flush()
        try {
            val input = pcm(441)
            pipeline.queueInput(input)
            assertEquals("整曲准备结束前不能先消费首片", 0, input.position())
            assertFalse(pipeline.output.hasRemaining())
            track.ready.complete(Unit)
            pipeline.queueInput(input)
            assertFalse(input.hasRemaining())
            assertEquals(441 * 4, pipeline.output.remaining())
        } finally { pipeline.reset() }
    }

    @Test fun cachedTrackStreamsAcrossAllBoundariesAndCanSeekAndLoopAtBothSampleRates() {
        for (rate in listOf(44_100, 48_000)) {
            for (channels in 1..2) {
                val frames = (MdxStemEngine.OUTPUT_FRAMES * (rate.toDouble() / 44_100)).toInt()
                val track = StemTrackCache("rate:$rate:channels:$channels")
                repeat(3) { index ->
                    track.put(rate, StemCachedChunk(index * frames.toLong(),
                        StemCachedSamples.fromFloats(FloatArray(frames * 2) { sample ->
                            ((index * frames + sample / 2) % 4000 - 2000) / 32768f
                        })))
                }
                track.ready.complete(Unit)
                val control = object : StemProcessingControl {
                    override val state = MutableStateFlow(StemSeparationState(enabled = true, realtime = false, vocals = 0f))
                    override val aiInference = StemTrackInference(track) { error("已缓存范围不能提交 AI") }
                }
                val processor = StemAudioProcessor(control)
                val pipeline = AudioProcessingPipeline(ImmutableList.of<AudioProcessor>(processor))
                pipeline.configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT))
                pipeline.flush()
                try {
                    val output = ByteArrayOutputStream()
                    var position = 0
                    while (position < frames * 3) {
                        val count = minOf(513, frames * 3 - position)
                        val input = pcm(count, channels)
                        pipeline.queueInput(input)
                        assertFalse("每个小块必须连续消费，无需攒满分片", input.hasRemaining())
                        val previous = output.size()
                        drain(pipeline, output)
                        assertEquals(count * channels * 2, output.size() - previous)
                        position += count
                    }
                    pipeline.queueEndOfStream()
                    drain(pipeline, output)
                    assertTrue(pipeline.isEnded)
                    val samples = ByteBuffer.wrap(output.toByteArray()).order(ByteOrder.nativeOrder())
                    repeat(frames * 3) { frame ->
                        repeat(channels) { assertEquals(frame % 4000 - 2000, samples.short.toInt()) }
                    }
                    assertFalse(samples.hasRemaining())

                    // 同一缓存可在分片中间拖动，之后单曲循环回曲首。
                    for (target in listOf(frames + 441, 0)) {
                        pipeline.flush()
                        processor.setInputTimestampUs((target * 1_000_000L + rate / 2) / rate)
                        pipeline.queueInput(pcm(100, channels))
                        val seekOutput = pipeline.output
                        assertEquals(100 * channels * 2, seekOutput.remaining())
                        repeat(100) { frame ->
                            repeat(channels) { assertEquals((target + frame) % 4000 - 2000, seekOutput.short.toInt()) }
                        }
                    }
                } finally { pipeline.reset() }
            }
        }
    }

    @Test fun partialCacheOnlyComputesMissingRangeAndPreservesHistoryAndSampleOrder() {
        val track = StemTrackCache("partial")
        track.put(44_100, StemCachedChunk(0, StemCachedSamples.fromFloats(FloatArray(16) { 2000 / 32768f })))
        track.put(44_100, StemCachedChunk(16, StemCachedSamples.fromFloats(FloatArray(16) { 4000 / 32768f })))
        track.ready.complete(Unit)
        val requests = mutableListOf<Long>()
        val inference = object : StemChunkInference {
            override fun cachedAt(sampleRate: Int, frame: Long) = track.chunkAt(sampleRate, frame)
            override fun submit(context: FloatArray, frames: Int) = error("缺片计算必须携带采样位置")
            override fun submitAt(context: FloatArray, frames: Int, sampleRate: Int, startFrame: Long): CompletableFuture<FloatArray> {
                requests += startFrame
                assertEquals(6000 / 32768f, context[0], 0f)
                return CompletableFuture.completedFuture(FloatArray(frames * 2) { 3000 / 32768f })
            }
        }
        val mixer = StemStreamingMixer(2, inference, {}, { throw it }, 8, 2)
        val input = pcm(24)
        mixer.queueInput(input)
        assertFalse(input.hasRemaining())
        mixer.finish()
        val actual = mutableListOf<Int>()
        val gains = StemSeparationState(enabled = true, realtime = false, vocals = 0f)
        while (!mixer.isDrained) {
            val buffer = mixer.output(gains) { ByteBuffer.allocate(it).order(ByteOrder.nativeOrder()) }!!
            while (buffer.hasRemaining()) actual += buffer.short.toInt()
        }
        assertEquals(listOf(8L), requests)
        assertEquals(List(16) { 2000 } + List(16) { 3000 } + List(16) { 4000 }, actual)
    }
}
