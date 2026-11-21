@file:Suppress("DEPRECATION")

package com.neko.music.service

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.google.android.exoplayer2.upstream.DataSpec
import com.neko.music.util.nekoMediaDataSourceFactory
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 独立解码整首歌曲到磁盘，再逐块分离；内存占用与歌曲时长无关。 */
internal class StemTrackPreparer(private val context: Context) {
    private data class Pcm(val rate: Int, val frames: Long)

    suspend fun prepare(source: String, engine: StemInference, output: File, onProgress: (StemPreparationProgress) -> Unit) {
        val directory = output.parentFile!!
        check(directory.isDirectory || directory.mkdirs())
        val key = output.nameWithoutExtension
        val input = File(directory, "$key.source")
        val pcm = File(directory, "$key.pcm")
        val metadata = File(directory, "$key.meta")
        check(directory.usableSpace > 32L * 1024 * 1024)
        if (!input.isFile) {
            val downloading = File(directory, "$key.source-part")
            try {
                copySource(source, downloading, onProgress)
                check(downloading.renameTo(input))
            } finally { downloading.delete() }
        }
        var format = runCatching {
            val values = metadata.readText().split(',')
            Pcm(values[0].toInt(), values[1].toLong()).takeIf { it.rate > 0 && it.frames > 0 && pcm.length() == it.frames * 4 }
        }.getOrNull()
        if (format == null) {
            onProgress(StemPreparationProgress(StemPreparationPhase.DECODING))
            format = decode(input, pcm)
            metadata.writeText("${format.rate},${format.frames}")
        }
        val chunkFrames = (MdxStemEngine.OUTPUT_FRAMES.toDouble() * format.rate / MdxStemEngine.SAMPLE_RATE)
            .roundToInt().coerceAtLeast(1)
        val trimFrames = (MdxStemEngine.TRIM_FRAMES.toDouble() * format.rate / MdxStemEngine.SAMPLE_RATE)
            .roundToInt().coerceAtLeast(1)
        val contextFrames = chunkFrames + 2 * trimFrames
        check(directory.usableSpace > format.frames * StemCacheFile.FRAME_BYTES + 8L * 1024 * 1024)
        RandomAccessFile(pcm, "r").use { reader ->
            RandomAccessFile(output, "rw").use { writer ->
                val completedFrames = StemCacheFile.resumableFrames(writer.length(), format.frames)
                writer.setLength(StemCacheFile.HEADER_BYTES + completedFrames * StemCacheFile.FRAME_BYTES)
                writer.seek(writer.length())
                var position = completedFrames
                onProgress(StemPreparationProgress(StemPreparationPhase.SEPARATING, position.toFloat() / format.frames))
                while (position < format.frames) {
                    currentCoroutineContext().ensureActive()
                    val wave = FloatArray(contextFrames * 2)
                    val first = (position - trimFrames).coerceAtLeast(0)
                    val last = (position + chunkFrames + trimFrames).coerceAtMost(format.frames)
                    val data = ByteArray(((last - first) * 4).toInt())
                    reader.seek(first * 4)
                    reader.readFully(data)
                    val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                    val offset = ((first - position + trimFrames) * 2).toInt()
                    var index = offset
                    while (buffer.hasRemaining()) wave[index++] = buffer.short / 32768f
                    val backing = engine.instrumental(wave, chunkFrames)
                    currentCoroutineContext().ensureActive()
                    val frames = minOf(chunkFrames.toLong(), format.frames - position).toInt()
                    val mixed = ByteBuffer.allocate(frames * StemCacheFile.FRAME_BYTES).order(ByteOrder.LITTLE_ENDIAN)
                    repeat(frames) { frame ->
                        for (channel in 0..1) {
                            mixed.putShort((wave[(frame + trimFrames) * 2 + channel] * 32768f)
                                .roundToInt().coerceIn(-32768, 32767).toShort())
                        }
                        for (channel in 0..1) {
                            mixed.putShort((backing[frame * 2 + channel] * 32768f)
                                .roundToInt().coerceIn(-32768, 32767).toShort())
                        }
                    }
                    writer.write(mixed.array())
                    position += frames
                    onProgress(StemPreparationProgress(StemPreparationPhase.SEPARATING, position.toFloat() / format.frames))
                }
                StemCacheFile.writeHeader(writer, format.rate, format.frames)
            }
        }
        check(StemCacheFile.isComplete(output))
    }

    private suspend fun copySource(source: String, destination: File, onProgress: (StemPreparationProgress) -> Unit) {
        val dataSource = nekoMediaDataSourceFactory(context).createDataSource()
        try {
            val uri = if (source.startsWith("/")) Uri.fromFile(File(source)) else Uri.parse(source)
            val remote = uri.scheme == "http" || uri.scheme == "https"
            val phase = if (remote) StemPreparationPhase.DOWNLOADING_TRACK else StemPreparationPhase.READING_TRACK
            onProgress(StemPreparationProgress(phase))
            val total = dataSource.open(DataSpec(uri)).takeIf { it > 0 }
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                var received = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = dataSource.read(buffer, 0, buffer.size)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    received += count
                    onProgress(StemPreparationProgress(phase, total?.let { received.toFloat() / it }, received, total))
                    check(destination.parentFile!!.usableSpace > 8L * 1024 * 1024)
                }
            }
        } finally {
            dataSource.close()
        }
    }

    private suspend fun decode(source: File, destination: File): Pcm {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("歌曲中未找到音频轨道")
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var inputEnded = false
            var outputEnded = false
            var skip = if (format.containsKey(MediaFormat.KEY_ENCODER_DELAY)) format.getInteger(MediaFormat.KEY_ENCODER_DELAY) else 0
            val padding = if (format.containsKey(MediaFormat.KEY_ENCODER_PADDING)) format.getInteger(MediaFormat.KEY_ENCODER_PADDING) else 0
            var frames = 0L
            var idleSince = System.nanoTime()
            val info = MediaCodec.BufferInfo()
            destination.outputStream().buffered().use { output ->
                while (!outputEnded) {
                    currentCoroutineContext().ensureActive()
                    check(System.nanoTime() - idleSince < 30_000_000_000L) { "音频解码长时间没有进展" }
                    if (!inputEnded) {
                        val index = decoder.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val buffer = decoder.getInputBuffer(index)!!
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val index = decoder.dequeueOutputBuffer(info, 10_000)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val decodedFormat = decoder.outputFormat
                        rate = decodedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = decodedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (decodedFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = decodedFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else if (index >= 0) {
                        idleSince = System.nanoTime()
                        try {
                            require(channels in 1..2 && rate > 0) { "音频声道不支持离线分离" }
                            require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT)
                            val bytesPerSample = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                            val buffer = decoder.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val frameCount = info.size / (channels * bytesPerSample)
                            val data = ByteBuffer.allocate(frameCount * 4).order(ByteOrder.LITTLE_ENDIAN)
                            repeat(frameCount) {
                                fun sample(): Short = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                    (buffer.float * 32768f).roundToInt().coerceIn(-32768, 32767).toShort()
                                } else buffer.short
                                val left = sample()
                                val right = if (channels == 2) sample() else left
                                if (skip > 0) skip-- else {
                                    data.putShort(left).putShort(right)
                                    frames++
                                }
                            }
                            output.write(data.array(), 0, data.position())
                            check(destination.parentFile!!.usableSpace > 8L * 1024 * 1024)
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        } finally {
                            decoder.releaseOutputBuffer(index, false)
                        }
                    }
                }
            }
            frames = (frames - padding).coerceAtLeast(0)
            require(frames > 0) { "歌曲没有可分离的音频数据" }
            RandomAccessFile(destination, "rw").use { it.setLength(frames * 4) }
            return Pcm(rate, frames)
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }
}
