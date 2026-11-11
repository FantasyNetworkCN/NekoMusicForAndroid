package com.neko.music.service

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug-only helper that exports the exact background-recognition PCM stream to
 * Download/NekoMusic as a 16 kHz mono PCM16 WAV file.
 *
 * The experiment is intentionally bounded by the recognition session (at most
 * 20 seconds), so keeping the PCM in memory avoids a second temporary file.
 */
internal class RecognitionAudioExporter(private val context: Context) {
    private val pcm = ByteArrayOutputStream()
    private var finished = false

    @Synchronized
    fun append(bytes: ByteArray, offset: Int, length: Int) {
        if (finished || length <= 0) return
        pcm.write(bytes, offset, length)
    }

    @Synchronized
    fun finishAndPublish(): Result<String> {
        if (finished) {
            return Result.failure(IllegalStateException("测试音频已经结束"))
        }
        finished = true
        val pcmBytes = pcm.toByteArray()
        pcm.reset()
        if (pcmBytes.isEmpty()) {
            return Result.failure(IllegalStateException("没有采集到后台音频"))
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Result.failure(UnsupportedOperationException("测试导出需要 Android 10 及以上"))
        }

        val fileName = "neko-recognition-bg-${
            SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        }.wav"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE_WAV)
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/$OUTPUT_DIRECTORY",
            )
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return Result.failure(IllegalStateException("无法创建下载目录文件"))
        return try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                output.write(wrapAsWav(pcmBytes))
            } ?: error("无法打开测试音频输出流")
            Result.success("${Environment.DIRECTORY_DOWNLOADS}/$OUTPUT_DIRECTORY/$fileName")
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            Result.failure(error)
        }
    }

    private fun wrapAsWav(pcmBytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(HEADER_SIZE + pcmBytes.size)
        output.writeAscii("RIFF")
        output.writeIntLittleEndian(36 + pcmBytes.size)
        output.writeAscii("WAVE")
        output.writeAscii("fmt ")
        output.writeIntLittleEndian(16)
        output.writeShortLittleEndian(1) // PCM
        output.writeShortLittleEndian(CHANNELS)
        output.writeIntLittleEndian(SAMPLE_RATE)
        output.writeIntLittleEndian(SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE)
        output.writeShortLittleEndian(CHANNELS * BYTES_PER_SAMPLE)
        output.writeShortLittleEndian(BITS_PER_SAMPLE)
        output.writeAscii("data")
        output.writeIntLittleEndian(pcmBytes.size)
        output.write(pcmBytes)
        return output.toByteArray()
    }

    private fun ByteArrayOutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun ByteArrayOutputStream.writeIntLittleEndian(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeShortLittleEndian(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private companion object {
        const val MIME_TYPE_WAV = "audio/wav"
        const val OUTPUT_DIRECTORY = "NekoMusic"
        const val SAMPLE_RATE = 16_000
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
        const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
        const val HEADER_SIZE = 44
    }
}
