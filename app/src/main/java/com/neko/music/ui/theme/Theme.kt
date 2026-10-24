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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.neko.music.config.AppConfig

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5F1222),
    onPrimaryContainer = Color(0xFFFFD9DE),
    inversePrimary = Color(0xFFBE0036),
    secondary = SecondaryDark,
    onSecondary = Color(0xFF45111D),
    secondaryContainer = Color(0xFF4B2B34),
    onSecondaryContainer = Color(0xFFFFD9E1),
    tertiary = TertiaryDark,
    onTertiary = Color(0xFF4A1120),
    tertiaryContainer = Color(0xFF5F2734),
    onTertiaryContainer = Color(0xFFFFD9E0),
    background = BackgroundDark,
    onBackground = Color(0xFFF1EDF4),
    surface = SurfaceDark,
    onSurface = Color(0xFFF1EDF4),
    surfaceVariant = Color(0xFF393641),
    onSurfaceVariant = Color(0xFFCEC5D0),
    surfaceTint = PrimaryDark,
    inverseSurface = Color(0xFFF1EDF4),
    inverseOnSurface = Color(0xFF312E33),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF978E99),
    outlineVariant = Color(0xFF474250),
    scrim = Color.Black,
    surfaceBright = Color(0xFF3A3947),
    surfaceDim = Color(0xFF12111B),
    surfaceContainerLowest = Color(0xFF0C0C18),
    surfaceContainerLow = Color(0xFF191927),
    surfaceContainer = Color(0xFF1E1E2D),
    surfaceContainerHigh = Color(0xFF252535),
    surfaceContainerHighest = Color(0xFF2D2D3F),
    primaryFixed = Color(0xFFFFD9DE),
    primaryFixedDim = Color(0xFFFFB3BE),
    onPrimaryFixed = Color(0xFF400010),
    onPrimaryFixedVariant = Color(0xFF930024),
    secondaryFixed = Color(0xFFFFD9E1),
    secondaryFixedDim = Color(0xFFE7BDC4),
    onSecondaryFixed = Color(0xFF3B2026),
    onSecondaryFixedVariant = Color(0xFF5A3C43),
    tertiaryFixed = Color(0xFFFFD9E0),
    tertiaryFixedDim = Color(0xFFE8B9C3),
    onTertiaryFixed = Color(0xFF400015),
    onTertiaryFixedVariant = Color(0xFF5E1130),
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADF),
    onPrimaryContainer = Color(0xFF40000A),
    inversePrimary = Color(0xFFB00030),
    secondary = SecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4DDE1),
    onSecondaryContainer = Color(0xFF3B2026),
    tertiary = TertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFADDE1),
    onTertiaryContainer = Color(0xFF400015),
    background = BackgroundLight,
    onBackground = Color(0xFF1C1B1F),
    surface = SurfaceLight,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFEFE1E4),
    onSurfaceVariant = Color(0xFF524346),
    surfaceTint = PrimaryLight,
    inverseSurface = Color(0xFF312E33),
    inverseOnSurface = Color(0xFFF5EFF1),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF857376),
    outlineVariant = Color(0xFFD9C8CC),
    scrim = Color.Black,
    surfaceBright = Color(0xFFFFF8F8),
    surfaceDim = Color(0xFFE1D8DA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF7F8),
    surfaceContainer = Color(0xFFF8F1F2),
    surfaceContainerHigh = Color(0xFFF3EAEC),
    surfaceContainerHighest = Color(0xFFEEE4E7),
    primaryFixed = Color(0xFFFFD9DE),
    primaryFixedDim = Color(0xFFFFB3BE),
    onPrimaryFixed = Color(0xFF400010),
    onPrimaryFixedVariant = Color(0xFF930024),
    secondaryFixed = Color(0xFFFFD9E1),
    secondaryFixedDim = Color(0xFFE7BDC4),
    onSecondaryFixed = Color(0xFF3B2026),
    onSecondaryFixedVariant = Color(0xFF5A3C43),
    tertiaryFixed = Color(0xFFFFD9E0),
    tertiaryFixedDim = Color(0xFFE8B9C3),
    onTertiaryFixed = Color(0xFF400015),
    onTertiaryFixedVariant = Color(0xFF5E1130),
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
