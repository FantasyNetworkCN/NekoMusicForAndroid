package com.neko.music.ui.screens

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.theme.RoseRed

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

                    Spacer(modifier = Modifier.height(150.dp))
                }
            }
        }
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
