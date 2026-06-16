package com.neko.music.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.neko.music.config.AppConfig

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    secondary = SecondaryDark,
    tertiary = TertiaryDark,
    background = BackgroundDark,
    surface = SurfaceDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    secondary = SecondaryLight,
    tertiary = TertiaryLight,
    background = BackgroundLight,
    surface = SurfaceLight
)

/** 根据软件主题设置解析是否使用深色外观；`auto` 时回退系统深色模式。 */
fun resolveAppDarkTheme(themeMode: String, systemDark: Boolean): Boolean = when (themeMode) {
    "light" -> false
    "dark" -> true
    else -> systemDark
}

@Composable
fun isAppDarkTheme(): Boolean {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
    }
    val themeMode =
        prefs.getString(AppConfig.PrefConfig.KEY_THEME, AppConfig.PrefConfig.DEFAULT_THEME)
            ?: AppConfig.PrefConfig.DEFAULT_THEME
    return resolveAppDarkTheme(themeMode, isSystemInDarkTheme())
}

@Composable
fun Neko歌姬计划(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}