package com.neko.music.service

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

internal data class DecodedStemChunk(
    val startFrame: Long,
    val samples: StemCachedSamples,
)

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

    fun decodeFullChunks(
        file: File,
        targetRate: Int,
        chunkSeconds: Double,
        onChunk: ((DecodedStemChunk) -> Unit)? = null,
    ): List<DecodedStemChunk> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("伴奏文件缺少音轨")
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("伴奏文件格式未知")
            var sourceRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var frameBytes = channels * Short.SIZE_BYTES
            var sourceChunkFrames = (chunkSeconds * sourceRate).roundToInt().coerceAtLeast(1)
            var leadingTrimFrames =
                (MP3_ENCODER_DELAY_FRAMES.toDouble() * sourceRate / 44_100.0).roundToInt()
            val currentPcm = ByteArrayOutputStream()
            var currentSourceFrames = 0
            var nextStartFrame = 0L
            val chunks = ArrayList<DecodedStemChunk>()
            val info = MediaCodec.BufferInfo()

            fun flushChunk() {
                if (currentSourceFrames <= 0) return
                val targetFrames = (currentSourceFrames.toDouble() * targetRate / sourceRate)
                    .roundToInt()
                    .coerceAtLeast(1)
                val chunk = DecodedStemChunk(
                    startFrame = nextStartFrame,
                    samples = convertPcm(
                        pcm = currentPcm.toByteArray(),
                        channels = channels,
                        sourceFrames = currentSourceFrames,
                        targetFrames = targetFrames,
                        startTrimFrames = 0,
                    ),
                )
                chunks += chunk
                onChunk?.invoke(chunk)
                nextStartFrame += targetFrames
                currentPcm.reset()
                currentSourceFrames = 0
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(DECODER_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                            ?: error("伴奏解码输入缓冲不可用")
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
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
                        frameBytes = channels * Short.SIZE_BYTES
                        sourceChunkFrames =
                            (chunkSeconds * sourceRate).roundToInt().coerceAtLeast(1)
                        if (currentSourceFrames == 0) {
                            leadingTrimFrames =
                                (MP3_ENCODER_DELAY_FRAMES.toDouble() * sourceRate / 44_100.0)
                                    .roundToInt()
                        }
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
                                var byteOffset = 0
                                var availableFrames = bytes.size / frameBytes
                                if (leadingTrimFrames > 0 && availableFrames > 0) {
                                    val trimFrames = minOf(leadingTrimFrames, availableFrames)
                                    byteOffset += trimFrames * frameBytes
                                    availableFrames -= trimFrames
                                    leadingTrimFrames -= trimFrames
                                }
                                while (availableFrames > 0) {
                                    val takeFrames = minOf(
                                        availableFrames,
                                        sourceChunkFrames - currentSourceFrames,
                                    )
                                    currentPcm.write(bytes, byteOffset, takeFrames * frameBytes)
                                    currentSourceFrames += takeFrames
                                    availableFrames -= takeFrames
                                    byteOffset += takeFrames * frameBytes
                                    if (currentSourceFrames >= sourceChunkFrames) flushChunk()
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            flushChunk()
            chunks
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
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
        val expectedTargetFrames = ((endSec - startSec).coerceAtLeast(0.0) * targetRate)
            .roundToInt().coerceAtLeast(1)
        return convertPcm(
            pcm = pcm,
            channels = channels,
            sourceFrames = expectedSourceFrames,
            targetFrames = expectedTargetFrames,
            startTrimFrames = startTrim,
        )
    }

    private fun convertPcm(
        pcm: ByteArray,
        channels: Int,
        sourceFrames: Int,
        targetFrames: Int,
        startTrimFrames: Int,
    ): StemCachedSamples {
        val frameBytes = channels * Short.SIZE_BYTES
        val decodedFrames = pcm.size / frameBytes
        val shortBuffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val output = ShortArray(targetFrames * 2)
        val sourceStep = sourceFrames.toDouble() / targetFrames
        val lastDecodedFrame = (decodedFrames - 1).coerceAtLeast(0)
        for (frame in 0 until targetFrames) {
            val sourcePosition = frame * sourceStep
            val firstFrame = (startTrimFrames + sourcePosition.toInt()).coerceAtMost(lastDecodedFrame)
            val secondFrame = (firstFrame + 1).coerceAtMost(lastDecodedFrame)
            val fraction = sourcePosition - sourcePosition.toInt()
            for (channel in 0 until 2) {
                val sourceChannel = if (channels == 1) 0 else channel.coerceAtMost(channels - 1)
                val first = shortBuffer.get(firstFrame * channels + sourceChannel).toDouble()
                val second = shortBuffer.get(secondFrame * channels + sourceChannel).toDouble()
                output[frame * 2 + channel] =
                    (first + (second - first) * fraction).roundToInt().toShort()
            }
        }
        return StemCachedSamples.fromShorts(output)
    }
}
