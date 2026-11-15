package com.neko.music.ui.components

import android.content.SharedPreferences
import com.neko.music.config.AppConfig

data class LyricStyleSettings(
    val fontSizeSp: Float,
    val lineSpacingDp: Float,
    val activeAlpha: Float,
    val inactiveAlpha: Float,
    val scaleCurrentLine: Boolean,
    val glowEnabled: Boolean,
    val karaokeEnabled: Boolean,
    val smoothScrollEnabled: Boolean,
    val centerText: Boolean,
    val showTranslation: Boolean,
    val showRomanization: Boolean,
) {
    fun resolvedFontSizeSp(isWideLayout: Boolean): Float = when {
        fontSizeSp > 0f -> fontSizeSp
        isWideLayout -> 24f
        else -> 28f
    }
}

object LyricStylePreferences {
    const val FONT_SIZE_MIN = 18f
    const val FONT_SIZE_MAX = 36f
    const val LINE_SPACING_MIN = 8f
    const val LINE_SPACING_MAX = 32f
    const val ACTIVE_ALPHA_MIN = 0.50f
    const val ACTIVE_ALPHA_MAX = 1.00f
    const val INACTIVE_ALPHA_MIN = 0.08f
    const val INACTIVE_ALPHA_MAX = 0.50f

    const val DEFAULT_FONT_SIZE = 0f
    const val DEFAULT_LINE_SPACING = 20f
    const val DEFAULT_ACTIVE_ALPHA = 0.94f
    const val DEFAULT_INACTIVE_ALPHA = 0.18f
    const val DEFAULT_SCALE_CURRENT = true
    const val DEFAULT_GLOW = true
    const val DEFAULT_KARAOKE = true
    const val DEFAULT_SMOOTH_SCROLL = true
    const val DEFAULT_CENTER_TEXT = false
    const val DEFAULT_SHOW_TRANSLATION = true
    const val DEFAULT_SHOW_ROMANIZATION = true

    val keys: Set<String> = setOf(
        AppConfig.PrefConfig.KEY_LYRIC_HIGHLIGHT_COLOR,
        AppConfig.PrefConfig.KEY_LYRIC_FONT_SIZE,
        AppConfig.PrefConfig.KEY_LYRIC_LINE_SPACING,
        AppConfig.PrefConfig.KEY_LYRIC_ACTIVE_ALPHA,
        AppConfig.PrefConfig.KEY_LYRIC_INACTIVE_ALPHA,
        AppConfig.PrefConfig.KEY_LYRIC_SCALE_CURRENT,
        AppConfig.PrefConfig.KEY_LYRIC_GLOW,
        AppConfig.PrefConfig.KEY_LYRIC_KARAOKE,
        AppConfig.PrefConfig.KEY_LYRIC_SMOOTH_SCROLL,
        AppConfig.PrefConfig.KEY_LYRIC_CENTER_TEXT,
        AppConfig.PrefConfig.KEY_LYRIC_SHOW_TRANSLATION,
        AppConfig.PrefConfig.KEY_LYRIC_SHOW_ROMANIZATION,
    )

    fun load(prefs: SharedPreferences, isWideLayout: Boolean): LyricStyleSettings {
        val storedFontSize = prefs.getFloat(
            AppConfig.PrefConfig.KEY_LYRIC_FONT_SIZE,
            DEFAULT_FONT_SIZE,
        )
        val fontSize = when {
            storedFontSize <= 0f -> if (isWideLayout) 24f else 28f
            else -> storedFontSize.coerceIn(FONT_SIZE_MIN, FONT_SIZE_MAX)
        }
        return LyricStyleSettings(
            fontSizeSp = fontSize,
            lineSpacingDp = prefs.getFloat(
                AppConfig.PrefConfig.KEY_LYRIC_LINE_SPACING,
                DEFAULT_LINE_SPACING,
            ).coerceIn(LINE_SPACING_MIN, LINE_SPACING_MAX),
            activeAlpha = prefs.getFloat(
                AppConfig.PrefConfig.KEY_LYRIC_ACTIVE_ALPHA,
                DEFAULT_ACTIVE_ALPHA,
            ).coerceIn(ACTIVE_ALPHA_MIN, ACTIVE_ALPHA_MAX),
            inactiveAlpha = prefs.getFloat(
                AppConfig.PrefConfig.KEY_LYRIC_INACTIVE_ALPHA,
                DEFAULT_INACTIVE_ALPHA,
            ).coerceIn(INACTIVE_ALPHA_MIN, INACTIVE_ALPHA_MAX),
            scaleCurrentLine = prefs.getBoolean(
                AppConfig.PrefConfig.KEY_LYRIC_SCALE_CURRENT,
                DEFAULT_SCALE_CURRENT,
            ),
            glowEnabled = prefs.getBoolean(
                AppConfig.PrefConfig.KEY_LYRIC_GLOW,
                DEFAULT_GLOW,
            ),
            karaokeEnabled = prefs.getBoolean(
                AppConfig.PrefConfig.KEY_LYRIC_KARAOKE,
                DEFAULT_KARAOKE,
            ),
            smoothScrollEnabled = prefs.getBoolean(
                AppConfig.PrefConfig.KEY_LYRIC_SMOOTH_SCROLL,
                DEFAULT_SMOOTH_SCROLL,
            ),
            centerText = prefs.getBoolean(
                AppConfig.PrefConfig.KEY_LYRIC_CENTER_TEXT,
                DEFAULT_CENTER_TEXT,
            ),
            showTranslation = prefs.getBoolean(
                AppConfig.PrefConfig.KEY_LYRIC_SHOW_TRANSLATION,
                DEFAULT_SHOW_TRANSLATION,
            ),
            showRomanization = prefs.getBoolean(
                AppConfig.PrefConfig.KEY_LYRIC_SHOW_ROMANIZATION,
                DEFAULT_SHOW_ROMANIZATION,
            ),
        )
    }

    fun save(prefs: SharedPreferences, settings: LyricStyleSettings) {
        prefs.edit()
            .putFloat(AppConfig.PrefConfig.KEY_LYRIC_FONT_SIZE, settings.fontSizeSp)
            .putFloat(AppConfig.PrefConfig.KEY_LYRIC_LINE_SPACING, settings.lineSpacingDp)
            .putFloat(AppConfig.PrefConfig.KEY_LYRIC_ACTIVE_ALPHA, settings.activeAlpha)
            .putFloat(AppConfig.PrefConfig.KEY_LYRIC_INACTIVE_ALPHA, settings.inactiveAlpha)
            .putBoolean(AppConfig.PrefConfig.KEY_LYRIC_SCALE_CURRENT, settings.scaleCurrentLine)
            .putBoolean(AppConfig.PrefConfig.KEY_LYRIC_GLOW, settings.glowEnabled)
            .putBoolean(AppConfig.PrefConfig.KEY_LYRIC_KARAOKE, settings.karaokeEnabled)
            .putBoolean(AppConfig.PrefConfig.KEY_LYRIC_SMOOTH_SCROLL, settings.smoothScrollEnabled)
            .putBoolean(AppConfig.PrefConfig.KEY_LYRIC_CENTER_TEXT, settings.centerText)
            .putBoolean(AppConfig.PrefConfig.KEY_LYRIC_SHOW_TRANSLATION, settings.showTranslation)
            .putBoolean(AppConfig.PrefConfig.KEY_LYRIC_SHOW_ROMANIZATION, settings.showRomanization)
            .apply()
    }

    fun reset(prefs: SharedPreferences, isWideLayout: Boolean): LyricStyleSettings {
        prefs.edit()
            .remove(AppConfig.PrefConfig.KEY_LYRIC_FONT_SIZE)
            .remove(AppConfig.PrefConfig.KEY_LYRIC_LINE_SPACING)
            .remove(AppConfig.PrefConfig.KEY_LYRIC_ACTIVE_ALPHA)
            .remove(AppConfig.PrefConfig.KEY_LYRIC_INACTIVE_ALPHA)
            .remove(AppConfig.PrefConfig.KEY_LYRIC_SCALE_CURRENT)
            .remove(AppConfig.PrefConfig.KEY_LYRIC_GLOW)
            .remove(AppConfig.PrefConfig.KEY_LYRIC_KARAOKE)
            .remove(AppConfig.PrefConfig.KEY_LYRIC_SMOOTH_SCROLL)
            .remove(AppConfig.PrefConfig.KEY_LYRIC_CENTER_TEXT)
            .remove(AppConfig.PrefConfig.KEY_LYRIC_SHOW_TRANSLATION)
            .remove(AppConfig.PrefConfig.KEY_LYRIC_SHOW_ROMANIZATION)
            .apply()
        return load(prefs, isWideLayout)
    }
}
