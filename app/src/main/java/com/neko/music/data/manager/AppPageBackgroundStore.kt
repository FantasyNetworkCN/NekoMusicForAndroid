package com.neko.music.data.manager

import android.content.Context
import android.net.Uri
import com.neko.music.config.AppConfig
import java.io.File

/** 各壁纸位自定义图片：存于应用私有目录，每位独立文件。 */
object AppPageBackgroundStore {

  private const val LEGACY_FILE_NAME = "custom_page_background"
  private const val PREFS_NAME = "app_settings"

  fun customFile(context: Context, kind: AppBackgroundKind): File =
    File(context.filesDir, kind.fileName)

  fun hasCustom(context: Context, kind: AppBackgroundKind): Boolean {
    migrateLegacyIfNeeded(context)
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (!prefs.getBoolean(kind.prefEnabledKey, false)) return false
    val file = customFile(context, kind)
    return file.isFile && file.length() > 0L
  }

  fun setFromUri(context: Context, kind: AppBackgroundKind, uri: Uri): Boolean {
    migrateLegacyIfNeeded(context)
    return try {
      context.contentResolver.openInputStream(uri)?.use { input ->
        val dest = customFile(context, kind)
        dest.outputStream().use { output -> input.copyTo(output) }
      } ?: return false
      context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(kind.prefEnabledKey, true)
        .apply()
      true
    } catch (_: Exception) {
      false
    }
  }

  fun clear(context: Context, kind: AppBackgroundKind) {
    customFile(context, kind).delete()
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .remove(kind.prefEnabledKey)
      .apply()
  }

  fun isBackgroundPrefKey(key: String?): Boolean {
    if (key == null) return false
    if (key == AppConfig.PrefConfig.KEY_CUSTOM_PAGE_BACKGROUND) return true
    return AppBackgroundKind.entries.any { it.prefEnabledKey == key }
  }

  private fun migrateLegacyIfNeeded(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (!prefs.getBoolean(AppConfig.PrefConfig.KEY_CUSTOM_PAGE_BACKGROUND, false)) return

    val legacyFile = File(context.filesDir, LEGACY_FILE_NAME)
    val subPageFile = customFile(context, AppBackgroundKind.SubPage)
    if (legacyFile.isFile && legacyFile.length() > 0L && (!subPageFile.exists() || subPageFile.length() == 0L)) {
      legacyFile.copyTo(subPageFile, overwrite = true)
    }
    legacyFile.delete()

    prefs.edit()
      .putBoolean(AppBackgroundKind.SubPage.prefEnabledKey, true)
      .remove(AppConfig.PrefConfig.KEY_CUSTOM_PAGE_BACKGROUND)
      .apply()
  }
}
