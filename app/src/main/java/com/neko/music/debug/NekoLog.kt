package com.neko.music.debug

import com.neko.music.BuildConfig

/**
 * Debug 构建中的 [android.util.Log] 替身。
 *
 * 业务代码统一 `import com.neko.music.debug.NekoLog as Log`，既保留 Logcat 输出，
 * 又把同一份日志写入内存环形缓冲区，供全局调试悬浮窗实时查看与导出。
 * Release 构建不会记录调试日志。
 */
object NekoLog {
    fun v(tag: String?, msg: String?): Int =
        write(android.util.Log.VERBOSE, tag, msg, null)

    fun v(tag: String?, msg: String?, tr: Throwable?): Int =
        write(android.util.Log.VERBOSE, tag, msg, tr)

    fun d(tag: String?, msg: String?): Int =
        write(android.util.Log.DEBUG, tag, msg, null)

    fun d(tag: String?, msg: String?, tr: Throwable?): Int =
        write(android.util.Log.DEBUG, tag, msg, tr)

    fun i(tag: String?, msg: String?): Int =
        write(android.util.Log.INFO, tag, msg, null)

    fun i(tag: String?, msg: String?, tr: Throwable?): Int =
        write(android.util.Log.INFO, tag, msg, tr)

    fun w(tag: String?, msg: String?): Int =
        write(android.util.Log.WARN, tag, msg, null)

    fun w(tag: String?, msg: String?, tr: Throwable?): Int =
        write(android.util.Log.WARN, tag, msg, tr)

    fun e(tag: String?, msg: String?): Int =
        write(android.util.Log.ERROR, tag, msg, null)

    fun e(tag: String?, msg: String?, tr: Throwable?): Int =
        write(android.util.Log.ERROR, tag, msg, tr)

    fun wtf(tag: String?, msg: String?): Int =
        write(android.util.Log.ASSERT, tag, msg, null)

    fun wtf(tag: String?, msg: String?, tr: Throwable?): Int =
        write(android.util.Log.ASSERT, tag, msg, tr)

    private fun write(
        priority: Int,
        tag: String?,
        msg: String?,
        throwable: Throwable?,
    ): Int {
        val safeTag = tag?.takeIf { it.isNotBlank() } ?: "NekoMusic"
        val stackTrace = throwable?.let(android.util.Log::getStackTraceString).orEmpty()
        val text = when {
            msg.isNullOrEmpty() -> stackTrace
            stackTrace.isBlank() -> msg
            else -> msg + "\n" + stackTrace
        }

        val result = android.util.Log.println(priority, safeTag, text)
        if (BuildConfig.DEBUG) {
            DebugLogStore.record(
                priority = priority,
                tag = safeTag,
                message = text,
            )
        }
        return result
    }
}
