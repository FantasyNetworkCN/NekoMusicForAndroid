package com.neko.music.ui.theme

import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import com.neko.music.config.AppConfig

fun defaultLyricHighlightColor(isDarkTheme: Boolean): Color =
    if (isDarkTheme) SakuraPink else RoseRed

fun lyricHighlightColorFromPrefs(prefs: SharedPreferences, isDarkTheme: Boolean): Color {
    if (!prefs.contains(AppConfig.PrefConfig.KEY_LYRIC_HIGHLIGHT_COLOR)) {
        return defaultLyricHighlightColor(isDarkTheme)
    }
    val argb = prefs.getInt(AppConfig.PrefConfig.KEY_LYRIC_HIGHLIGHT_COLOR, 0)
    return lyricColorFromArgb(argb)
}

fun lyricColorFromArgb(argb: Int): Color = argbIntToComposeColor(argb)

private fun argbIntToComposeColor(argb: Int): Color {
    return Color(
        red = AndroidColor.red(argb) / 255f,
        green = AndroidColor.green(argb) / 255f,
        blue = AndroidColor.blue(argb) / 255f,
        alpha = AndroidColor.alpha(argb).coerceIn(1, 255) / 255f
    )
}
