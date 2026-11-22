package com.neko.music.service

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.roundToInt

/** 双轨 PCM 按歌曲、时间位置原子持久化；重播先读本地，不再下载和解码整曲。 */
internal class StemDiskCache(private val root: File) {
    private fun directory(musicId: Int) = File(root, "$musicId/pcm-v2")
    private fun fresh(file: File) = file.isFile && System.currentTimeMillis() - file.lastModified() < TTL_MS

    fun restore(musicId: Int, publish: (StemCachedChunk) -> Unit): Boolean {
        val dir = directory(musicId)
        val restored = mutableSetOf<Long>()
        dir.listFiles().orEmpty().filter { it.extension == "pcm" }.sortedBy {
            it.nameWithoutExtension.toLongOrNull() ?: Long.MAX_VALUE
        }.forEach { file ->
            if (!fresh(file)) return@forEach
            val chunk = runCatching { read(file) }.getOrNull() ?: return@forEach
            publish(chunk)
            restored += chunk.startFrame
        }
        val marker = File(dir, "complete")
        val expected = if (fresh(marker)) runCatching {
            marker.readLines().map { it.toLong() }.toSet()
        }.getOrNull() else null
        val complete = !expected.isNullOrEmpty() && expected.all { it in restored }
        if (restored.isNotEmpty()) dir.parentFile?.setLastModified(System.currentTimeMillis())
        return complete
    }

    fun write(musicId: Int, chunk: StemCachedChunk) {
        val dir = directory(musicId).apply { mkdirs() }
        val target = File(dir, "${chunk.startFrame}.pcm")
        val part = File.createTempFile(".chunk-", ".part", dir)
        try {
            DataOutputStream(part.outputStream().buffered()).use { out ->
                out.writeInt(MAGIC)
                out.writeLong(chunk.startFrame)
                out.writeInt(chunk.frames)
                for (samples in listOf(chunk.vocals, chunk.accompaniment)) {
                    repeat(chunk.frames) { frame ->
                        repeat(2) { channel ->
                            out.writeShort((samples.sample(frame, channel) * 32768f)
                                .roundToInt().coerceIn(-32768, 32767))
                        }
                    }
                }
            }
            check(part.renameTo(target)) { "分离 PCM 缓存原子替换失败" }
        } finally {
            part.delete()
        }
    }

    fun loadNear(musicId: Int, frame: Long, needed: (Long) -> Boolean, publish: (StemCachedChunk) -> Unit) {
        directory(musicId).listFiles().orEmpty().filter { it.extension == "pcm" }.sortedBy {
            it.nameWithoutExtension.toLongOrNull() ?: Long.MAX_VALUE
        }.forEach { file ->
            val start = file.nameWithoutExtension.toLongOrNull() ?: return@forEach
            if (start > frame + STEM_SAMPLE_RATE * 90L ||
                start + STEM_SAMPLE_RATE * 31L <= frame || !needed(start)) return@forEach
            if (fresh(file)) runCatching { read(file) }.getOrNull()?.let(publish)
        }
    }

    fun complete(musicId: Int, starts: Collection<Long>) {
        if (starts.isEmpty()) return
        val dir = directory(musicId).apply { mkdirs() }
        val part = File.createTempFile(".complete-", ".part", dir)
        try {
            part.writeText(starts.sorted().joinToString("\n"))
            check(part.renameTo(File(dir, "complete"))) { "分离缓存完成标记保存失败" }
            prune(musicId)
        } finally {
            part.delete()
        }
    }

    /** 缓存有界且只清理其他歌曲，不能在播放途中清掉当前双轨。 */
    private fun prune(currentMusicId: Int) {
        val directories = root.listFiles().orEmpty().filter { it.isDirectory }
        var total = directories.sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
        for (dir in directories.sortedBy { it.lastModified() }) {
            if (total <= MAX_DISK_BYTES) break
            if (dir.name == currentMusicId.toString()) continue
            val bytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            if (dir.deleteRecursively()) total -= bytes
        }
    }

    private fun read(file: File): StemCachedChunk = DataInputStream(file.inputStream().buffered()).use { input ->
        check(input.readInt() == MAGIC)
        val start = input.readLong()
        val frames = input.readInt()
        check(start >= 0 && frames in 1..(STEM_SAMPLE_RATE * 31))
        check(file.length() == HEADER_BYTES + frames * 8L)
        val voice = ShortArray(frames * 2) { input.readShort() }
        val backing = ShortArray(frames * 2) { input.readShort() }
        StemCachedChunk(start, StemCachedSamples.fromShorts(voice), StemCachedSamples.fromShorts(backing))
    }

    private companion object {
        const val MAGIC = 0x4E535432
        const val HEADER_BYTES = 16L
        const val TTL_MS = 180L * 24 * 60 * 60 * 1000
        const val MAX_DISK_BYTES = 512L * 1024 * 1024
    }
}
