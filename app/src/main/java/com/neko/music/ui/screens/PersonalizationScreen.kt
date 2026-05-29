package com.neko.music.ui.screens

import android.app.Activity
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.neko.music.data.manager.AppPageBackgroundStore
import com.neko.music.ui.components.AppPageBackgroundImage
import com.neko.music.ui.components.LiquidGlassUiScale
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    var liquidHardwareEffects by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppConfig.PrefConfig.KEY_LIQUID_GLASS_HARDWARE_EFFECTS,
                AppConfig.PrefConfig.DEFAULT_LIQUID_GLASS_HARDWARE_EFFECTS
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

    var lyricHighlightArgb: Int? by remember {
        mutableStateOf(
            if (prefs.contains(AppConfig.PrefConfig.KEY_LYRIC_HIGHLIGHT_COLOR)) {
                prefs.getInt(AppConfig.PrefConfig.KEY_LYRIC_HIGHLIGHT_COLOR, 0)
            } else {
                null
            }
        )
    }

    val isDarkChrome = com.neko.music.ui.theme.resolveAppDarkTheme(themeMode, isSystemInDarkTheme())
    val scheme = MaterialTheme.colorScheme
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    val scope = rememberCoroutineScope()
    var customBackgroundByKind by remember {
        mutableStateOf(
            AppBackgroundKind.entries.associateWith { AppPageBackgroundStore.hasCustom(context, it) },
        )
    }
    var pickingBackgroundKind by remember { mutableStateOf<AppBackgroundKind?>(null) }
    var savingBackgroundKind by remember { mutableStateOf<AppBackgroundKind?>(null) }
    val backgroundAppliedText = stringResource(R.string.personalization_background_applied)
    val backgroundFailedText = stringResource(R.string.personalization_background_failed)
    val pickBackgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        val kind = pickingBackgroundKind
        if (uri == null || kind == null) {
            pickingBackgroundKind = null
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            savingBackgroundKind = kind
            val ok = withContext(Dispatchers.IO) {
                AppPageBackgroundStore.setFromUri(context, kind, uri)
            }
            savingBackgroundKind = null
            pickingBackgroundKind = null
            if (ok) {
                customBackgroundByKind = customBackgroundByKind + (kind to true)
                Toast.makeText(context, backgroundAppliedText, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, backgroundFailedText, Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
                Box(modifier = Modifier.fillMaxSize()) {
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
                                onCheckedChange = { applyDynamicColor(it) },
                                useDarkAppearance = isDarkChrome
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

                    SettingSection(
                        title = stringResource(id = R.string.personalization_background_section),
                        useDarkAppearance = isDarkChrome,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = stringResource(id = R.string.personalization_background_subtitle),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = if (isDarkChrome) {
                                    Color(0xFFB8B8D1).copy(alpha = 0.85f)
                                } else {
                                    scheme.onSurfaceVariant
                                },
                            )
                            AppBackgroundKind.entries.forEach { kind ->
                                val hasCustom = customBackgroundByKind[kind] == true
                                val isSavingThis = savingBackgroundKind == kind
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = stringResource(id = kind.titleResId),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isDarkChrome) {
                                            Color(0xFFF0F0F5).copy(alpha = 0.95f)
                                        } else {
                                            scheme.onSurface
                                        },
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(96.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                    ) {
                                        AppPageBackgroundImage(
                                            kind = kind,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        if (kind == AppBackgroundKind.SubPage) {
                                            PlaylistPageDarkTintOverlay(enabled = isDarkChrome)
                                        }
                                        if (isSavingThis) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.35f)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CircularProgressIndicator(color = RoseRed)
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Button(
                                            onClick = {
                                                if (savingBackgroundKind == null) {
                                                    pickingBackgroundKind = kind
                                                    pickBackgroundLauncher.launch("image/*")
                                                }
                                            },
                                            enabled = savingBackgroundKind == null,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(id = R.string.personalization_background_choose))
                                        }
                                        TextButton(
                                            onClick = {
                                                if (savingBackgroundKind == null && hasCustom) {
                                                    scope.launch(Dispatchers.IO) {
                                                        AppPageBackgroundStore.clear(context, kind)
                                                        customBackgroundByKind = customBackgroundByKind + (kind to false)
                                                    }
                                                }
                                            },
                                            enabled = savingBackgroundKind == null && hasCustom,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Restore,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(id = R.string.personalization_background_reset))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val lyricHighlightPresetColors = remember {
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
                    val lyricPreviewHighlight = remember(isDarkChrome, lyricHighlightArgb) {
                        lyricHighlightArgb?.let { lyricColorFromArgb(it) }
                            ?: defaultLyricHighlightColor(isDarkChrome)
                    }
                    val lyricOtherLine = if (isDarkChrome) Color.White.copy(alpha = 0.35f) else Color.Gray
                    val lyricPreviewBg = if (isDarkChrome) Color(0xFF1A1A2E) else Color(0xFFF0F0F5)
                    val lyricSwatchScroll = rememberScrollState()

                    SettingSection(
                        title = stringResource(id = R.string.lyric_highlight_color_section),
                        useDarkAppearance = isDarkChrome
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(lyricPreviewBg)
                                    .padding(vertical = 16.dp, horizontal = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(id = R.string.lyric_highlight_preview_prev),
                                    fontSize = 14.sp,
                                    color = lyricOtherLine,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = stringResource(id = R.string.lyric_highlight_preview_current),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = lyricPreviewHighlight,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                                Text(
                                    text = stringResource(id = R.string.lyric_highlight_preview_next),
                                    fontSize = 14.sp,
                                    color = lyricOtherLine,
                                    textAlign = TextAlign.Center,
                                )
                            }

                            val ringSel = if (isDarkChrome) Color.White.copy(alpha = 0.9f) else RoseRed
                            val ringIdle =
                                if (isDarkChrome) Color.White.copy(alpha = 0.22f) else Color(0x33000000)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(lyricSwatchScroll)
                                    .padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val defaultSelected = lyricHighlightArgb == null
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (defaultSelected) 3.dp else 1.dp,
                                            color = if (defaultSelected) ringSel else ringIdle,
                                            shape = CircleShape
                                        )
                                        .background(
                                            if (isDarkChrome) Color.White.copy(alpha = 0.1f) else Color(0xFFE8E8EC)
                                        )
                                        .clickable {
                                            lyricHighlightArgb = null
                                            prefs.edit()
                                                .remove(AppConfig.PrefConfig.KEY_LYRIC_HIGHLIGHT_COLOR)
                                                .apply()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BrightnessAuto,
                                        contentDescription = stringResource(id = R.string.lyric_highlight_follow_theme),
                                        tint = if (isDarkChrome) Color(0xFFE8E8F0) else Color(0xFF555555),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                lyricHighlightPresetColors.forEach { color ->
                                    val argb = color.toArgb()
                                    val selected = lyricHighlightArgb == argb
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = if (selected) 3.dp else 1.dp,
                                                color = if (selected) ringSel else ringIdle,
                                                shape = CircleShape
                                            )
                                            .background(color)
                                            .clickable {
                                                lyricHighlightArgb = argb
                                                prefs.edit()
                                                    .putInt(
                                                        AppConfig.PrefConfig.KEY_LYRIC_HIGHLIGHT_COLOR,
                                                        argb
                                                    )
                                                    .apply()
                                            }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val liqRange = LiquidGlassUiScale.StrengthMin..LiquidGlassUiScale.StrengthMax

                    SettingSection(
                        title = stringResource(id = R.string.liquid_glass_section),
                        useDarkAppearance = isDarkChrome
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            SettingSwitchItem(
                                icon = Icons.Default.BlurOn,
                                title = stringResource(id = R.string.liquid_glass_hardware_effects),
                                subtitle = stringResource(id = R.string.liquid_glass_hardware_effects_subtitle),
                                checked = liquidHardwareEffects,
                                useDarkAppearance = isDarkChrome,
                                onCheckedChange = { enabled ->
                                    liquidHardwareEffects = enabled
                                    prefs.edit()
                                        .putBoolean(
                                            AppConfig.PrefConfig.KEY_LIQUID_GLASS_HARDWARE_EFFECTS,
                                            enabled
                                        )
                                        .apply()
                                    (context as? Activity)?.recreate()
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = if (isDarkChrome) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
                            )
                        } else {
                            Text(
                                text = stringResource(id = R.string.liquid_glass_hardware_effects_unavailable),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                fontSize = 13.sp,
                                color = if (isDarkChrome) Color(0xFFB8B8D1).copy(alpha = 0.85f) else scheme.onSurfaceVariant
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = if (isDarkChrome) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
                            )
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
                                        .remove(AppConfig.PrefConfig.KEY_LIQUID_GLASS_HARDWARE_EFFECTS)
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
