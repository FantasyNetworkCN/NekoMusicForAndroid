package com.neko.music.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import com.neko.music.R

/**
 * Android 15（API 35）起，选择器优先使用 [AppWidgetManager.setWidgetPreview] 的 RemoteViews，
 * 不再展示 XML 里的 [android:previewLayout]；须在应用进程内注册一次（建议按版本号去重，该 API 有频率限制）。
 */
internal object MusicWidgetPreviewRegistrar {

    private const val TAG = "MusicWidgetPreview"
    private const val PREFS = "widget_preview_reg"
    private const val KEY_VERSION = "preview_ok_version_code"

    fun register(context: Context) {
        if (Build.VERSION.SDK_INT < 35) return
        val app = context.applicationContext
        val versionCode = packageVersionCode(app) ?: return

        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_VERSION, -1L) == versionCode) return

        val mgr = AppWidgetManager.getInstance(app)
        val provider = ComponentName(app, MusicWidgetProvider::class.java)
        val preview = RemoteViews(app.packageName, R.layout.music_widget_preview)
        val ok = try {
            mgr.setWidgetPreview(
                provider,
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                preview
            )
        } catch (e: Exception) {
            Log.w(TAG, "setWidgetPreview failed", e)
            false
        }
        if (ok) {
            prefs.edit().putLong(KEY_VERSION, versionCode).apply()
        }
    }

    private fun packageVersionCode(context: Context): Long? {
        return try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pi.versionCode.toLong()
            }
        } catch (e: Exception) {
            Log.w(TAG, "packageInfo", e)
            null
        }
    }
}
