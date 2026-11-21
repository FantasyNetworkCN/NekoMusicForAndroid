package com.neko.music.service

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import kotlin.math.roundToInt

/** 不可变的分离采样；旧缓存保留 PCM16，新缓存用浮点保存，避免伴奏残差信号被削波。 */
internal class StemCachedSamples private constructor(
    private val floats: FloatArray?,
    private val shorts: ShortArray?,
) {
    val frames: Int get() = (floats?.size ?: shorts!!.size) / 2

    fun sample(frame: Int, channel: Int): Float {
        val index = frame * 2 + channel
        return floats?.get(index) ?: (shorts!![index] / 32768f)
    }

    fun toFloatArray(): FloatArray = floats ?: FloatArray(shorts!!.size) { shorts[it] / 32768f }

    companion object {
        fun fromFloats(samples: FloatArray) = StemCachedSamples(samples, null)
        fun fromShorts(samples: ShortArray) = StemCachedSamples(null, samples)
    }
}

internal data class StemCachedChunk(val startFrame: Long, val samples: StemCachedSamples) {
    val endFrame: Long get() = startFrame + samples.frames
}

/** 一首歌独立持有整曲已准备的数据；播放读取不持磁盘锁，不限制分片数量，也不逐片淘汰。 */
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

/** 磁盘读写只在后台执行，整曲准备后发布给播放线程；切歌使用独立会话隔离旧任务。 */
internal class StemChunkCache(
    private val directory: File,
    private val version: String = MODEL_VERSION,
    private val maxDiskBytes: Long = 512L * 1024L * 1024L,
) {
    private val diskWriteLock = Any()

    fun key(context: FloatArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(version.toByteArray())
        val bytes = ByteBuffer.allocate(context.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.asFloatBuffer().put(context)
        return digest.digest(bytes.array()).toHex()
    }

    /** 在起播阶段一次读取全部曲目索引和已有分片，完成之前不消费第一块音频。 */
    fun prepareTrack(track: StemTrackCache) {
        try {
            val manifest = manifestFile(track.identity)
            if (!manifest.isFile || track.released) return
            val entries = linkedMapOf<Pair<Int, Long>, Pair<String, Int>>()
            manifest.forEachLine { line ->
                if (track.released) return@forEachLine
                val fields = line.split(',')
                if (fields.size == 4) {
                    val rate = fields[0].toIntOrNull() ?: return@forEachLine
                    val start = fields[1].toLongOrNull() ?: return@forEachLine
                    val frames = fields[2].toIntOrNull() ?: return@forEachLine
                    if (rate <= 0 || start < 0 || frames <= 0) return@forEachLine
                    if (fields[3].matches(CACHE_KEY)) entries[rate to start] = fields[3] to frames
                } else if (fields.size == 2) {
                    // 兼容旧版按曲首分片序号记录的索引，完整读取后换算成绝对采样位置。
                    val index = fields[0].toLongOrNull() ?: return@forEachLine
                    if (index < 0) return@forEachLine
                    val key = fields[1].takeIf { it.matches(CACHE_KEY) } ?: return@forEachLine
                    val floatFile = File(directory, "$key.f32")
                    val frames = if (floatFile.isFile) floatFile.length() / 8L
                        else File(directory, "$key.pcm").length() / 4L
                    if (frames <= 0 || frames > MAX_CHUNK_BYTES / 8) return@forEachLine
                    val rate = (frames.toDouble() * MdxStemEngine.SAMPLE_RATE /
                        MdxStemEngine.OUTPUT_FRAMES).roundToInt()
                    entries[rate to index * frames] = key to frames.toInt()
                }
            }
            // 旧索引可能多次追加同一片，先去重再读盘；相同内容的分片共享不可变采样。
            val loaded = hashMapOf<String, StemCachedSamples?>()
            for ((position, entry) in entries) {
                if (track.released) return
                val samples = if (loaded.containsKey(entry.first)) loaded[entry.first]
                    else read(entry.first, entry.second).also { loaded[entry.first] = it }
                if (samples != null && samples.frames == entry.second) {
                    track.put(position.first, StemCachedChunk(position.second, samples))
                }
            }
        } finally {
            track.ready.complete(Unit)
        }
    }

    fun read(key: String, expectedFrames: Int? = null): StemCachedSamples? {
        if (!key.matches(CACHE_KEY)) return null
        val floatFile = File(directory, "$key.f32")
        val file = if (floatFile.isFile) floatFile else File(directory, "$key.pcm")
        val frameBytes = if (file.extension == "f32") 8 else 4
        val length = file.length()
        if (!file.isFile || length == 0L || length % frameBytes != 0L || length > MAX_CHUNK_BYTES) return null
        if (expectedFrames != null && length / frameBytes != expectedFrames.toLong()) return null
        return runCatching {
            val bytes = file.readBytes()
            if (bytes.size.toLong() != length) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val samples = if (frameBytes == 8) {
                val floats = FloatArray(bytes.size / 4)
                buffer.asFloatBuffer().get(floats)
                if (floats.any { !it.isFinite() }) return null
                StemCachedSamples.fromFloats(floats)
            } else {
                val shorts = ShortArray(bytes.size / 2)
                buffer.asShortBuffer().get(shorts)
                StemCachedSamples.fromShorts(shorts)
            }
            file.setLastModified(System.currentTimeMillis())
            samples
        }.getOrNull()
    }

    /** 结果先发布到曲目内存，再异步持久化；落盘和清理从不挡住当前音频输出。 */
    fun write(key: String, samples: StemCachedSamples, track: StemTrackCache?, sampleRate: Int, startFrame: Long) {
        synchronized(diskWriteLock) {
            directory.mkdirs()
            val bytes = ByteBuffer.allocate(samples.frames * 8).order(ByteOrder.LITTLE_ENDIAN)
            bytes.asFloatBuffer().put(samples.toFloatArray())
            val target = File(directory, "$key.f32")
            val temporary = File(directory, "$key.part")
            temporary.outputStream().buffered().use { it.write(bytes.array()) }
            check(temporary.renameTo(target))
            if (track != null) {
                manifestFile(track.identity).appendText("$sampleRate,$startFrame,${samples.frames},$key\n")
            }
            pruneDisk()
        }
    }

    private fun pruneDisk() {
        val files = directory.listFiles()?.filter { it.isFile && it.extension in setOf("pcm", "f32") }.orEmpty()
            .sortedBy { it.lastModified() }
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= maxDiskBytes) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun manifestFile(identity: String): File = File(directory,
        MessageDigest.getInstance("SHA-256").digest("$version|$identity".toByteArray()).toHex() + ".idx")

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val MODEL_VERSION = "mdx-9703-128-v1"
        private val CACHE_KEY = Regex("[0-9a-f]{64}")
        private const val MAX_CHUNK_BYTES = 16L * 1024L * 1024L
    }
}
