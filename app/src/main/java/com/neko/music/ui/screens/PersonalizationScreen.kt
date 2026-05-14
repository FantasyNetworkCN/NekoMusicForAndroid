package com.neko.music.ui.screens

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.R
import com.neko.music.config.AppConfig
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LiquidGlassUiScale
import com.neko.music.ui.components.LocalLiquidGlassUiScale
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.theme.Lilac
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.SkyBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizationScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }
    var themeMode by remember {
        mutableStateOf(
            prefs.getString(AppConfig.PrefConfig.KEY_THEME, AppConfig.PrefConfig.DEFAULT_THEME)
                ?: AppConfig.PrefConfig.DEFAULT_THEME
        )
    }
    var useDynamicColor by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppConfig.PrefConfig.KEY_DYNAMIC_COLOR,
                AppConfig.PrefConfig.DEFAULT_DYNAMIC_COLOR
            )
        )
    }

    var liqTint by remember {
        mutableFloatStateOf(
            prefs.getFloat(
                AppConfig.PrefConfig.KEY_LIQUID_GLASS_TINT,
                AppConfig.PrefConfig.DEFAULT_LIQUID_GLASS_STRENGTH
            ).coerceIn(LiquidGlassUiScale.StrengthMin, LiquidGlassUiScale.StrengthMax)
        )
    }
    var liqBlur by remember {
        mutableFloatStateOf(
            prefs.getFloat(
                AppConfig.PrefConfig.KEY_LIQUID_GLASS_BLUR,
                AppConfig.PrefConfig.DEFAULT_LIQUID_GLASS_STRENGTH
            ).coerceIn(LiquidGlassUiScale.StrengthMin, LiquidGlassUiScale.StrengthMax)
        )
    }
    var liqLensH by remember {
        mutableFloatStateOf(
            prefs.getFloat(
                AppConfig.PrefConfig.KEY_LIQUID_GLASS_LENS_HEIGHT,
                AppConfig.PrefConfig.DEFAULT_LIQUID_GLASS_STRENGTH
            ).coerceIn(LiquidGlassUiScale.StrengthMin, LiquidGlassUiScale.StrengthMax)
        )
    }
    var liqLensA by remember {
        mutableFloatStateOf(
            prefs.getFloat(
                AppConfig.PrefConfig.KEY_LIQUID_GLASS_LENS_AMOUNT,
                AppConfig.PrefConfig.DEFAULT_LIQUID_GLASS_STRENGTH
            ).coerceIn(LiquidGlassUiScale.StrengthMin, LiquidGlassUiScale.StrengthMax)
        )
    }

    val systemDark = isSystemInDarkTheme()
    val isDarkChrome = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val scheme = MaterialTheme.colorScheme
    val pageBg = if (isDarkChrome) Color(0xFF121228) else Color(0xFFFAFAFA)
    val pageBackdrop = rememberLiquidPageBackdrop(
        if (isDarkChrome) Color(0xFF121228) else scheme.background
    )

    fun applyTheme(mode: String) {
        if (mode == themeMode) return
        themeMode = mode
        prefs.edit().putString(AppConfig.PrefConfig.KEY_THEME, mode).apply()
        (context as? Activity)?.recreate()
    }

    fun applyDynamicColor(enabled: Boolean) {
        if (enabled == useDynamicColor) return
        useDynamicColor = enabled
        prefs.edit().putBoolean(AppConfig.PrefConfig.KEY_DYNAMIC_COLOR, enabled).apply()
        (context as? Activity)?.recreate()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = pageBg,
        contentColor = if (isDarkChrome) Color(0xFFF0F0F5) else scheme.onSurface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.personalization),
                        color = if (isDarkChrome) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                            tint = if (isDarkChrome) Color(0xFFB8B8D1).copy(alpha = 0.9f) else scheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = pageBg,
                    scrolledContainerColor = pageBg
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(pageBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(pageBackdrop)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    SettingSection(
                        title = stringResource(id = R.string.theme_mode),
                        useDarkAppearance = isDarkChrome
                    ) {
                        ThemeModeOptionRow(
                            icon = Icons.Default.BrightnessAuto,
                            label = stringResource(id = R.string.theme_follow_system),
                            selected = themeMode == "auto",
                            isDarkChrome = isDarkChrome,
                            onClick = { applyTheme("auto") }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = if (isDarkChrome) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
                        )
                        ThemeModeOptionRow(
                            icon = Icons.Default.LightMode,
                            label = stringResource(id = R.string.theme_light),
                            selected = themeMode == "light",
                            isDarkChrome = isDarkChrome,
                            onClick = { applyTheme("light") }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = if (isDarkChrome) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
                        )
                        ThemeModeOptionRow(
                            icon = Icons.Default.DarkMode,
                            label = stringResource(id = R.string.theme_dark),
                            selected = themeMode == "dark",
                            isDarkChrome = isDarkChrome,
                            onClick = { applyTheme("dark") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingSection(
                            title = stringResource(id = R.string.dynamic_color),
                            useDarkAppearance = isDarkChrome
                        ) {
                            SettingSwitchItem(
                                icon = Icons.Default.Palette,
                                title = stringResource(id = R.string.dynamic_color),
                                subtitle = stringResource(id = R.string.dynamic_color_subtitle),
                                checked = useDynamicColor,
                                onCheckedChange = { applyDynamicColor(it) }
                            )
                        }
                    } else {
                        SettingSection(
                            title = stringResource(id = R.string.dynamic_color),
                            useDarkAppearance = isDarkChrome
                        ) {
                            Text(
                                text = stringResource(id = R.string.dynamic_color_unavailable),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                fontSize = 14.sp,
                                color = if (isDarkChrome) Color(0xFFB8B8D1).copy(alpha = 0.85f) else scheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val previewGlassTint = LiquidGlassDefaults.screenListCard
                    val previewBg = previewGlassTint.background(isDarkChrome)
                    val previewBd = previewGlassTint.border(isDarkChrome)
                    val previewHi = previewGlassTint.highlight(isDarkChrome)
                    val liquidMed = LiquidGlassDefaults.liquidMedium
                    val previewLiq = LiquidGlassUiScale(liqTint, liqBlur, liqLensH, liqLensA)
                    val liqRange = LiquidGlassUiScale.StrengthMin..LiquidGlassUiScale.StrengthMax

                    SettingSection(
                        title = stringResource(id = R.string.liquid_glass_section),
                        useDarkAppearance = isDarkChrome
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .height(148.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(RoseRed, SkyBlue, Lilac)
                                        )
                                    )
                            )
                            CompositionLocalProvider(LocalLiquidGlassUiScale provides previewLiq) {
                                GlassSurface(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp)
                                        .height(92.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    backgroundAlpha = previewBg,
                                    borderAlpha = previewBd,
                                    highlightAlpha = previewHi,
                                    borderColor = if (isDarkChrome) Color.White else scheme.outline,
                                    liquidBlur = liquidMed.blur,
                                    liquidLensHeight = liquidMed.lensHeight,
                                    liquidLensAmount = liquidMed.lensAmount,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.liquid_glass_preview_hint),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDarkChrome) {
                                                Color(0xFFF0F0F5).copy(alpha = 0.95f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        LiquidGlassStrengthSlider(
                            label = stringResource(id = R.string.liquid_glass_tint),
                            value = liqTint,
                            onValueChange = { liqTint = it },
                            isDarkChrome = isDarkChrome
                        )
                        LiquidGlassStrengthSlider(
                            label = stringResource(id = R.string.liquid_glass_blur),
                            value = liqBlur,
                            onValueChange = { liqBlur = it },
                            isDarkChrome = isDarkChrome
                        )
                        LiquidGlassStrengthSlider(
                            label = stringResource(id = R.string.liquid_glass_lens_height),
                            value = liqLensH,
                            onValueChange = { liqLensH = it },
                            isDarkChrome = isDarkChrome
                        )
                        LiquidGlassStrengthSlider(
                            label = stringResource(id = R.string.liquid_glass_lens_amount),
                            value = liqLensA,
                            onValueChange = { liqLensA = it },
                            isDarkChrome = isDarkChrome
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    prefs.edit()
                                        .remove(AppConfig.PrefConfig.KEY_LIQUID_GLASS_TINT)
                                        .remove(AppConfig.PrefConfig.KEY_LIQUID_GLASS_BLUR)
                                        .remove(AppConfig.PrefConfig.KEY_LIQUID_GLASS_LENS_HEIGHT)
                                        .remove(AppConfig.PrefConfig.KEY_LIQUID_GLASS_LENS_AMOUNT)
                                        .apply()
                                    (context as? Activity)?.recreate()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(id = R.string.liquid_glass_reset_default))
                            }
                            Button(
                                onClick = {
                                    prefs.edit()
                                        .putFloat(
                                            AppConfig.PrefConfig.KEY_LIQUID_GLASS_TINT,
                                            liqTint.coerceIn(liqRange.start, liqRange.endInclusive)
                                        )
                                        .putFloat(
                                            AppConfig.PrefConfig.KEY_LIQUID_GLASS_BLUR,
                                            liqBlur.coerceIn(liqRange.start, liqRange.endInclusive)
                                        )
                                        .putFloat(
                                            AppConfig.PrefConfig.KEY_LIQUID_GLASS_LENS_HEIGHT,
                                            liqLensH.coerceIn(liqRange.start, liqRange.endInclusive)
                                        )
                                        .putFloat(
                                            AppConfig.PrefConfig.KEY_LIQUID_GLASS_LENS_AMOUNT,
                                            liqLensA.coerceIn(liqRange.start, liqRange.endInclusive)
                                        )
                                        .apply()
                                    (context as? Activity)?.recreate()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(id = R.string.liquid_glass_save_apply))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(150.dp))
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassStrengthSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    isDarkChrome: Boolean,
) {
    val range = LiquidGlassUiScale.StrengthMin..LiquidGlassUiScale.StrengthMax
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = if (isDarkChrome) Color(0xFFE8E8F0).copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = String.format("%.2f", value),
                fontSize = 13.sp,
                color = if (isDarkChrome) Color(0xFFB8B8D1) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

@Composable
private fun ThemeModeOptionRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    isDarkChrome: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(RoseRed.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = RoseRed,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDarkChrome) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = RoseRed,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
