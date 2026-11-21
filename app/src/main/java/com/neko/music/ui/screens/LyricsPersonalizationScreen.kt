package com.neko.music.ui.screens

import com.neko.music.ui.icons.NekoIcon

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.R
import com.neko.music.config.AppConfig
import com.neko.music.data.manager.AppBackgroundKind
import com.neko.music.ui.components.AppPageBackgroundImage
import com.neko.music.ui.components.LiquidGlassSwitch
import com.neko.music.ui.components.LyricStylePreferences
import com.neko.music.ui.components.LyricStyleSettings
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.NekoAdaptivePageContent
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.theme.LightRose
import com.neko.music.ui.theme.MintGreen
import com.neko.music.ui.theme.Peach
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.SakuraPink
import com.neko.music.ui.theme.SkyBlue
import com.neko.music.ui.theme.StarYellow
import com.neko.music.ui.theme.defaultLyricHighlightColor
import com.neko.music.ui.theme.lyricColorFromArgb
import com.neko.music.ui.theme.resolveAppDarkTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsPersonalizationScreen(
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }
    val isWideLayout = LocalConfiguration.current.screenWidthDp >= 600
    val themeMode = prefs.getString(AppConfig.PrefConfig.KEY_THEME, AppConfig.PrefConfig.DEFAULT_THEME)
        ?: AppConfig.PrefConfig.DEFAULT_THEME
    val isDarkChrome = resolveAppDarkTheme(themeMode, isSystemInDarkTheme())
    val scheme = MaterialTheme.colorScheme
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)

    var styleRevision by remember { mutableIntStateOf(0) }
    val style = remember(styleRevision, isWideLayout) {
        LyricStylePreferences.load(prefs, isWideLayout)
    }
    var highlightArgb by remember {
        mutableStateOf(
            if (prefs.contains(AppConfig.PrefConfig.KEY_LYRIC_HIGHLIGHT_COLOR)) {
                prefs.getInt(AppConfig.PrefConfig.KEY_LYRIC_HIGHLIGHT_COLOR, 0)
            } else {
                null
            }
        )
    }
    val highlightColor = highlightArgb?.let(::lyricColorFromArgb)
        ?: defaultLyricHighlightColor(isDarkChrome)

    fun updateStyle(transform: (LyricStyleSettings) -> LyricStyleSettings) {
        val updated = transform(style)
        LyricStylePreferences.save(prefs, updated)
        styleRevision++
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop)
        ) {
            AppPageBackgroundImage(
                kind = AppBackgroundKind.SubPage,
                modifier = Modifier.fillMaxSize(),
            )
            PlaylistPageDarkTintOverlay(enabled = isDarkChrome)
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = if (isDarkChrome) Color(0xFFF0F0F5) else scheme.onSurface,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.lyrics_style_screen_title),
                            color = if (isDarkChrome) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            NekoIcon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = if (isDarkChrome) Color(0xFFB8B8D1) else scheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                )
            },
        ) { paddingValues ->
            CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
                NekoAdaptivePageContent(modifier = Modifier.padding(paddingValues)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    ) {
                        SettingSection(
                            title = stringResource(R.string.lyrics_style_preview),
                            useDarkAppearance = isDarkChrome,
                        ) {
                            LyricStylePreview(
                                style = style,
                                highlightColor = highlightColor,
                                isDarkChrome = isDarkChrome,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        SettingSection(
                            title = stringResource(R.string.lyrics_style_text_layout),
                            useDarkAppearance = isDarkChrome,
                        ) {
                            LyricSliderSetting(
                            title = stringResource(R.string.lyrics_style_font_size),
                            valueText = "${style.fontSizeSp.roundToInt()} sp",
                            value = style.fontSizeSp,
                            valueRange = LyricStylePreferences.FONT_SIZE_MIN..LyricStylePreferences.FONT_SIZE_MAX,
                            isDarkChrome = isDarkChrome,
                            onValueChange = { value ->
                                updateStyle { it.copy(fontSizeSp = value) }
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor(isDarkChrome))
                        LyricSliderSetting(
                            title = stringResource(R.string.lyrics_style_line_spacing),
                            valueText = "${style.lineSpacingDp.roundToInt()} dp",
                            value = style.lineSpacingDp,
                            valueRange = LyricStylePreferences.LINE_SPACING_MIN..LyricStylePreferences.LINE_SPACING_MAX,
                            isDarkChrome = isDarkChrome,
                            onValueChange = { value ->
                                updateStyle { it.copy(lineSpacingDp = value) }
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor(isDarkChrome))
                        LyricSwitchSetting(
                            title = stringResource(R.string.lyrics_style_scale_current),
                            subtitle = stringResource(R.string.lyrics_style_scale_current_subtitle),
                            checked = style.scaleCurrentLine,
                            isDarkChrome = isDarkChrome,
                            onCheckedChange = { checked ->
                                updateStyle { it.copy(scaleCurrentLine = checked) }
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor(isDarkChrome))
                        LyricSwitchSetting(
                            title = stringResource(R.string.lyrics_style_center_text),
                            subtitle = stringResource(R.string.lyrics_style_center_text_subtitle),
                            checked = style.centerText,
                            isDarkChrome = isDarkChrome,
                            onCheckedChange = { checked ->
                                updateStyle { it.copy(centerText = checked) }
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor(isDarkChrome))
                        LyricSwitchSetting(
                            title = stringResource(R.string.lyrics_style_show_translation),
                            subtitle = stringResource(R.string.lyrics_style_show_translation_subtitle),
                            checked = style.showTranslation,
                            isDarkChrome = isDarkChrome,
                            onCheckedChange = { checked ->
                                updateStyle { it.copy(showTranslation = checked) }
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor(isDarkChrome))
                        LyricSwitchSetting(
                            title = stringResource(R.string.lyrics_style_show_romanization),
                            subtitle = stringResource(R.string.lyrics_style_show_romanization_subtitle),
                            checked = style.showRomanization,
                            isDarkChrome = isDarkChrome,
                            onCheckedChange = { checked ->
                                updateStyle { it.copy(showRomanization = checked) }
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingSection(
                        title = stringResource(R.string.lyrics_style_effects),
                        useDarkAppearance = isDarkChrome,
                    ) {
                        LyricSwitchSetting(
                            title = stringResource(R.string.lyrics_style_karaoke),
                            subtitle = stringResource(R.string.lyrics_style_karaoke_subtitle),
                            checked = style.karaokeEnabled,
                            isDarkChrome = isDarkChrome,
                            onCheckedChange = { checked ->
                                updateStyle { it.copy(karaokeEnabled = checked) }
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor(isDarkChrome))
                        LyricSwitchSetting(
                            title = stringResource(R.string.lyrics_style_glow),
                            subtitle = stringResource(R.string.lyrics_style_glow_subtitle),
                            checked = style.glowEnabled,
                            isDarkChrome = isDarkChrome,
                            onCheckedChange = { checked ->
                                updateStyle { it.copy(glowEnabled = checked) }
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor(isDarkChrome))
                        LyricSwitchSetting(
                            title = stringResource(R.string.lyrics_style_smooth_scroll),
                            subtitle = stringResource(R.string.lyrics_style_smooth_scroll_subtitle),
                            checked = style.smoothScrollEnabled,
                            isDarkChrome = isDarkChrome,
                            onCheckedChange = { checked ->
                                updateStyle { it.copy(smoothScrollEnabled = checked) }
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor(isDarkChrome))
                        LyricSliderSetting(
                            title = stringResource(R.string.lyrics_style_active_alpha),
                            valueText = "${(style.activeAlpha * 100).roundToInt()}%",
                            value = style.activeAlpha,
                            valueRange = LyricStylePreferences.ACTIVE_ALPHA_MIN..LyricStylePreferences.ACTIVE_ALPHA_MAX,
                            isDarkChrome = isDarkChrome,
                            onValueChange = { value ->
                                updateStyle { it.copy(activeAlpha = value) }
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor(isDarkChrome))
                        LyricSliderSetting(
                            title = stringResource(R.string.lyrics_style_inactive_alpha),
                            valueText = "${(style.inactiveAlpha * 100).roundToInt()}%",
                            value = style.inactiveAlpha,
                            valueRange = LyricStylePreferences.INACTIVE_ALPHA_MIN..LyricStylePreferences.INACTIVE_ALPHA_MAX,
                            isDarkChrome = isDarkChrome,
                            onValueChange = { value ->
                                updateStyle { it.copy(inactiveAlpha = value) }
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val presetColors = remember {
                        listOf(
                            RoseRed,
                            SakuraPink,
                            LightRose,
                            SkyBlue,
                            MintGreen,
                            StarYellow,
                            Peach,
                            Color(0xFFBB86FC),
                        )
                    }
                    SettingSection(
                        title = stringResource(R.string.lyrics_style_highlight_color),
                        useDarkAppearance = isDarkChrome,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val ringSelected = if (isDarkChrome) Color.White.copy(alpha = 0.9f) else RoseRed
                            val ringIdle = if (isDarkChrome) Color.White.copy(alpha = 0.22f) else Color(0x33000000)
                            val defaultSelected = highlightArgb == null
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (defaultSelected) 3.dp else 1.dp,
                                        color = if (defaultSelected) ringSelected else ringIdle,
                                        shape = CircleShape,
                                    )
                                    .background(if (isDarkChrome) Color.White.copy(alpha = 0.1f) else Color(0xFFE8E8EC))
                                    .clickable {
                                        highlightArgb = null
                                        prefs.edit()
                                            .remove(AppConfig.PrefConfig.KEY_LYRIC_HIGHLIGHT_COLOR)
                                            .apply()
                                        styleRevision++
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                NekoIcon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.lyric_highlight_follow_theme),
                                    tint = if (isDarkChrome) Color(0xFFE8E8F0) else Color(0xFF555555),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            presetColors.forEach { color ->
                                val argb = color.toArgb()
                                val selected = highlightArgb == argb
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) ringSelected else ringIdle,
                                            shape = CircleShape,
                                        )
                                        .background(color)
                                        .clickable {
                                            highlightArgb = argb
                                            prefs.edit()
                                                .putInt(AppConfig.PrefConfig.KEY_LYRIC_HIGHLIGHT_COLOR, argb)
                                                .apply()
                                            styleRevision++
                                        },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            LyricStylePreferences.reset(prefs, isWideLayout)
                            styleRevision++
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        NekoIcon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.lyrics_style_reset))
                    }

                        Spacer(modifier = Modifier.height(150.dp))
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun LyricStylePreview(
    style: LyricStyleSettings,
    highlightColor: Color,
    isDarkChrome: Boolean,
) {
    val baseColor = if (isDarkChrome) Color.White else Color(0xFF111114)
    val inactiveColor = baseColor.copy(alpha = style.inactiveAlpha)
    val previewFontSize = style.fontSizeSp.coerceIn(18f, 24f).sp
    val previewLineHeight = (previewFontSize.value * 1.22f).sp
    val previewSpacing = style.lineSpacingDp.coerceIn(2f, 10f).dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isDarkChrome) Color(0xFF1A1A2E) else Color(0xFFF0F0F5))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(previewSpacing),
        horizontalAlignment = if (style.centerText) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = stringResource(R.string.lyrics_style_preview_previous),
            fontSize = (previewFontSize.value * 0.76f).sp,
            lineHeight = (previewLineHeight.value * 0.76f).sp,
            fontWeight = FontWeight.Bold,
            color = inactiveColor,
            textAlign = if (style.centerText) TextAlign.Center else TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val scale = if (style.scaleCurrentLine) 0.98f else 1.0f
                    scaleX = scale
                    scaleY = scale
                },
        )
        Text(
            text = stringResource(R.string.lyrics_style_preview_current),
            fontSize = previewFontSize,
            lineHeight = previewLineHeight,
            fontWeight = FontWeight.Bold,
            color = highlightColor.copy(alpha = style.activeAlpha),
            textAlign = if (style.centerText) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
            style = if (style.glowEnabled) {
                androidx.compose.ui.text.TextStyle(
                    shadow = Shadow(
                        color = highlightColor.copy(alpha = if (isDarkChrome) 0.30f else 0.18f),
                        blurRadius = 14f,
                    ),
                )
            } else {
                androidx.compose.ui.text.TextStyle.Default
            },
        )
        if (style.showRomanization) {
            Text(
                text = stringResource(R.string.lyrics_style_preview_romanization),
                fontSize = (previewFontSize.value * 0.61f).sp,
                lineHeight = (previewLineHeight.value * 0.61f).sp,
                fontWeight = FontWeight.Bold,
                color = baseColor.copy(alpha = 0.76f),
                textAlign = if (style.centerText) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (style.showTranslation) {
            Text(
                text = stringResource(R.string.lyrics_style_preview_translation),
                fontSize = (previewFontSize.value * 0.61f).sp,
                lineHeight = (previewLineHeight.value * 0.61f).sp,
                fontWeight = FontWeight.Bold,
                color = baseColor.copy(alpha = 0.76f),
                textAlign = if (style.centerText) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LyricSliderSetting(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    isDarkChrome: Boolean,
    onValueChange: (Float) -> Unit,
) {
    val sliderState = remember(valueRange) {
        SliderState(value = value, trackRange = valueRange)
    }
    androidx.compose.runtime.SideEffect {
        sliderState.value = value.coerceIn(valueRange)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDarkChrome) Color(0xFFF0F0F5) else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueText,
                fontSize = 13.sp,
                color = if (isDarkChrome) Color(0xFFB8B8D1) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            state = sliderState,
            onValueChange = { changedValue ->
                sliderState.value = changedValue
                onValueChange(changedValue)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LyricSwitchSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    isDarkChrome: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDarkChrome) Color(0xFFF0F0F5) else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = if (isDarkChrome) Color(0xFFB8B8D1) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        LiquidGlassSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            isDarkChrome = isDarkChrome,
        )
    }
}

private fun dividerColor(isDarkChrome: Boolean): Color =
    if (isDarkChrome) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
