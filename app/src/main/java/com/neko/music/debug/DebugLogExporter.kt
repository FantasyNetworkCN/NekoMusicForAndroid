package com.neko.music.debug

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.neko.music.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DebugLogExporter {
    suspend fun share(context: Context, entries: List<DebugLogEntry>) {
        val file = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "debug_logs").apply { mkdirs() }
            val time = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            File(directory, "neko-music-log-$time.txt").apply {
                writeText(buildExportText(context, entries), Charsets.UTF_8)
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "NekoMusic 调试日志")
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "NekoMusic ${BuildConfig.VERSION_NAME} 调试日志")
            clipData = ClipData.newRawUri("NekoMusic 调试日志", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "发送 NekoMusic 调试日志")
        if (context !is android.app.Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun buildExportText(context: Context, entries: List<DebugLogEntry>): String {
        val exportedAt = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS Z",
            Locale.getDefault(),
        ).format(Date())
        return buildString {
            appendLine("NekoMusic 调试日志")
            appendLine("应用版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("包名: ${context.packageName}")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("导出时间: $exportedAt")
            appendLine("日志条数: ${entries.size}")
            appendLine("=".repeat(72))
            append(DebugLogStore.toPlainText(entries))
            appendLine()
        }
    }
}
