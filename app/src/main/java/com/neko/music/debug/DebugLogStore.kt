package com.neko.music.debug

import com.neko.music.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DebugLogLevel(val priority: Int, val label: String) {
    VERBOSE(android.util.Log.VERBOSE, "详细"),
    DEBUG(android.util.Log.DEBUG, "调试"),
    INFO(android.util.Log.INFO, "信息"),
    WARN(android.util.Log.WARN, "警告"),
    ERROR(android.util.Log.ERROR, "错误"),
    ASSERT(android.util.Log.ASSERT, "断言"),
    ;

    companion object {
        fun fromPriority(priority: Int): DebugLogLevel = when {
            priority <= android.util.Log.VERBOSE -> VERBOSE
            priority == android.util.Log.DEBUG -> DEBUG
            priority == android.util.Log.INFO -> INFO
            priority == android.util.Log.WARN -> WARN
            priority == android.util.Log.ERROR -> ERROR
            else -> ASSERT
        }
    }
}

data class DebugLogEntry(
    val id: Long,
    val timestampMillis: Long,
    val priority: Int,
    val tag: String,
    val message: String,
    val threadName: String,
) {
    val level: DebugLogLevel = DebugLogLevel.fromPriority(priority)
}

/**
 * 调试日志的进程内环形缓冲区。只保留最近 [MAX_ENTRIES] 条，避免长时间调试占用过多内存。
 */
object DebugLogStore {
    const val MAX_ENTRIES = 2_500

    private val lock = Any()
    private val entries = ArrayDeque<DebugLogEntry>()
    private val _revision = MutableStateFlow(0L)
    private var nextEntryId = 0L
    private var revision = 0L

    /** 每次追加、清空时递增，UI 用其触发快照刷新。 */
    val changeRevision: StateFlow<Long> = _revision.asStateFlow()

    fun record(priority: Int, tag: String, message: String) {
        if (!BuildConfig.DEBUG) return

        val currentRevision = synchronized(lock) {
            val entry = DebugLogEntry(
                id = ++nextEntryId,
                timestampMillis = System.currentTimeMillis(),
                priority = priority,
                tag = tag,
                message = message,
                threadName = Thread.currentThread().name,
            )
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) {
                entries.removeFirst()
            }
            ++revision
        }
        _revision.value = currentRevision
    }

    fun snapshot(minPriority: Int = android.util.Log.VERBOSE): List<DebugLogEntry> =
        synchronized(lock) {
            if (minPriority <= android.util.Log.VERBOSE) {
                entries.toList()
            } else {
                entries.filter { it.priority >= minPriority }
            }
        }

    fun count(): Int = synchronized(lock) { entries.size }

    fun clear() {
        val currentRevision = synchronized(lock) {
            entries.clear()
            ++revision
        }
        _revision.value = currentRevision
    }

    fun toPlainText(entries: List<DebugLogEntry>): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return entries.joinToString(separator = "\n") { entry ->
            val time = formatter.format(Date(entry.timestampMillis))
            "$time ${entry.level.label}/${entry.threadName} ${entry.tag}: ${entry.message}"
        }
    }
}
