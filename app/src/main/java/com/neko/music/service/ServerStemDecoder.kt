package com.neko.music.service

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** 把服务端伴奏 MP3 分片解码为当前播放采样率的双声道 PCM。 */
internal object ServerStemDecoder {
    private const val DECODER_TIMEOUT_US = 10_000L
    /** MPEG-1 Layer III 常见编码器前置延迟；分片尾部多余部分在输出时裁掉。 */
    private const val MP3_ENCODER_DELAY_FRAMES = 1_105

    fun decode(file: File, startSec: Double, endSec: Double, targetRate: Int): StemCachedSamples {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("伴奏分片缺少音轨")
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("伴奏分片格式未知")
            var sourceRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val pcm = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(DECODER_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex) ?: error("伴奏解码输入缓冲不可用")
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, DECODER_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        sourceRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        if (info.size > 0) {
                            val output = codec.getOutputBuffer(outputIndex)
                            if (output != null) {
                                output.position(info.offset)
                                output.limit(info.offset + info.size)
                                val bytes = ByteArray(info.size)
                                output.get(bytes)
                                pcm.write(bytes)
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            return convert(pcm.toByteArray(), sourceRate, channels,
                startSec, endSec, targetRate)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    fun decodeFullChunks(file: File, targetRate: Int, chunkSeconds: Double): List<StemCachedChunk> {
        val durationSec = probeDurationSeconds(file)
        val full = decode(file, 0.0, durationSec, targetRate)
        val chunkFrames = (chunkSeconds * targetRate).roundToInt().coerceAtLeast(1)
        val chunks = ArrayList<StemCachedChunk>()
        var startFrame = 0
        while (startFrame < full.frames) {
            val count = minOf(chunkFrames, full.frames - startFrame)
            chunks += StemCachedChunk(
                startFrame = startFrame.toLong(),
                samples = full.slice(startFrame, count),
            )
            startFrame += count
        }
        return chunks
    }

    private fun probeDurationSeconds(file: File): Double {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("伴奏文件缺少音轨")
            val durationUs = extractor.getTrackFormat(trackIndex)
                .getLong(MediaFormat.KEY_DURATION)
            (durationUs / 1_000_000.0).coerceAtLeast(0.001)
        } finally {
            extractor.release()
        }
    }

    private fun convert(
        pcm: ByteArray,
        sourceRate: Int,
        channels: Int,
        startSec: Double,
        endSec: Double,
        targetRate: Int,
    ): StemCachedSamples {
        require(sourceRate > 0 && channels > 0)
        val frameBytes = channels * Short.SIZE_BYTES
        val decodedFrames = pcm.size / frameBytes
        val expectedSourceFrames = ((endSec - startSec).coerceAtLeast(0.0) * sourceRate)
            .roundToInt().coerceAtLeast(1)
        val extra = (decodedFrames - expectedSourceFrames).coerceAtLeast(0)
        val delay = (MP3_ENCODER_DELAY_FRAMES.toDouble() * sourceRate / 44_100.0).roundToInt()
        val startTrim = minOf(delay, extra)
        val shortBuffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val source = FloatArray(expectedSourceFrames * 2)
        for (frame in 0 until expectedSourceFrames) {
            val decodedFrame = startTrim + frame
            if (decodedFrame >= decodedFrames) break
            for (channel in 0 until 2) {
                val sourceChannel = if (channels == 1) 0 else channel.coerceAtMost(channels - 1)
                source[frame * 2 + channel] =
                    shortBuffer.get(decodedFrame * channels + sourceChannel) / 32768f
            }
        }
        val expectedTargetFrames = ((endSec - startSec).coerceAtLeast(0.0) * targetRate)
            .roundToInt().coerceAtLeast(1)
        return StemCachedSamples.fromFloats(resampleStereo(source, expectedTargetFrames))
    }
}
