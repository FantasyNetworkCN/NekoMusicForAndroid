package com.neko.music.service

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** 缓存按原声左/右、伴奏左/右保存，调节两路音量时无需重新计算。 */
internal object StemCacheFile {
    const val HEADER_BYTES = 44
    const val FRAME_BYTES = 8

    fun resumableFrames(length: Long, totalFrames: Long): Long =
        ((length - HEADER_BYTES).coerceAtLeast(0) / FRAME_BYTES).coerceAtMost(totalFrames)

    fun key(source: String): String = MessageDigest.getInstance("SHA-256")
        .digest(source.toByteArray()).joinToString("") { "%02x".format(it) }

    fun writeHeader(output: RandomAccessFile, sampleRate: Int, frameCount: Long) {
        require(sampleRate > 0 && frameCount > 0 && frameCount <= (0xffffffffL - 36) / FRAME_BYTES)
        val bytes = frameCount * FRAME_BYTES
        val header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt((bytes + 36).toInt()).put("WAVEfmt ".toByteArray())
        header.putInt(16).putShort(1).putShort(4).putInt(sampleRate).putInt(sampleRate * FRAME_BYTES)
        header.putShort(FRAME_BYTES.toShort()).putShort(16).put("data".toByteArray()).putInt(bytes.toInt())
        output.seek(0)
        output.write(header.array())
    }

    fun isComplete(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() <= HEADER_BYTES) return false
        file.inputStream().use { input ->
            val bytes = ByteArray(HEADER_BYTES)
            if (input.read(bytes) != HEADER_BYTES) return false
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val dataBytes = header.getInt(40).toLong() and 0xffffffffL
            String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE" &&
                header.getShort(20) == 1.toShort() && header.getShort(22) == 4.toShort() &&
                header.getShort(34) == 16.toShort() && dataBytes % FRAME_BYTES == 0L &&
                file.length() == dataBytes + HEADER_BYTES
        }
    }.getOrDefault(false)
}
