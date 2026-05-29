package com.neko.music.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import com.neko.music.R
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.neko.music.ui.theme.isAppDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.music.config.AppConfig
import com.neko.music.data.manager.AppUpdateManager
import com.neko.music.data.manager.UpdateInfo
import com.neko.music.ui.components.AppUpdateDownloadProgressDialog
import com.neko.music.ui.components.AppUpdateErrorDialog
import com.neko.music.ui.components.AppUpdatePromptDialog
import com.neko.music.ui.components.AppUpdateSuccessDialog
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LiquidGlassSwitch
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.components.AppPageBackgroundImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.ui.theme.RoseRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    onNavigateToCache: () -> Unit = {},
    onNavigateToPersonalization: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { AppUpdateManager(context) }

    // 版本信息
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: Exception) {
        "1.0.0"
    }

    val versionCode = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
        }
    } catch (e: Exception) {
        1L
    }

    // 更新状态
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadTransferred by remember { mutableStateOf(0L) }
    var downloadContentLength by remember { mutableStateOf(0L) }
    var showUpdateSuccessDialog by remember { mutableStateOf(false) }
    var showUpdateErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // 缓存设置 - 使用与 MusicCacheManager 相同的 SharedPreferences
    val cachePrefs = remember { context.getSharedPreferences("music_cache", Context.MODE_PRIVATE) }
    var isCacheEnabled by remember { mutableStateOf(cachePrefs.getBoolean("cache_enabled", true)) }
    
    // 焦点锁定设置
    val focusLockPrefs = remember { context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE) }
    var isFocusLockEnabled by remember { mutableStateOf(focusLockPrefs.getBoolean("focus_lock_enabled", false)) }
    
    // 语言设置
    val languagePrefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var currentLanguage by remember { mutableStateOf(languagePrefs.getString("language", "system") ?: "system") }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val themeMode =
        languagePrefs.getString(AppConfig.PrefConfig.KEY_THEME, AppConfig.PrefConfig.DEFAULT_THEME)
            ?: AppConfig.PrefConfig.DEFAULT_THEME
    val themeSubtitle = when (themeMode) {
        "light" -> stringResource(id = R.string.theme_light)
        "dark" -> stringResource(id = R.string.theme_dark)
        else -> stringResource(id = R.string.theme_follow_system)
    }
    
    // 悬浮窗权限检查
    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.provider.Settings.canDrawOverlays(context)
            } else {
                true
            }
        )
    }
    
    // 缓存管理
    val cacheManager = remember { com.neko.music.data.cache.MusicCacheManager.getInstance(context) }
    var cacheSize by remember { mutableStateOf(cacheManager.getCacheSizeFormatted()) }
    var cachedMusicCount by remember { mutableStateOf(cacheManager.getCachedMusicCount()) }
    
    // 当缓存启用状态改变时更新缓存信息
    LaunchedEffect(isCacheEnabled) {
        cacheSize = cacheManager.getCacheSizeFormatted()
        cachedMusicCount = cacheManager.getCachedMusicCount()
    }
    
    // 监听权限状态变化
    LaunchedEffect(Unit) {
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    // 监听应用生命周期，在恢复时重新检查权限
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 重新检查权限状态
                val newPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.provider.Settings.canDrawOverlays(context)
                } else {
                    true
                }
                
                hasOverlayPermission = newPermission
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        try {
            kotlinx.coroutines.delay(Long.MAX_VALUE)
        } finally {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 检查更新
    val checkUpdate = {
        scope.launch {
            isCheckingUpdate = true
            try {
                val info = updateManager.checkUpdate()
                if (info != null && info.isUpdateAvailable) {
                    updateInfo = info
                    showUpdateDialog = true
                } else {
                    // 没有更新
                }
            } catch (e: Exception) {
                Log.e("SettingsScreen", "检查更新失败", e)
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    // 下载并安装更新
    val downloadAndInstall = {
        scope.launch {
            isDownloading = true
            downloadTransferred = 0L
            downloadContentLength = 0L

            // 清理所有旧的更新文件
            updateManager.cleanupUpdateFiles()

            try {
                val apkFile = updateManager.downloadApk(
                    updateInfo!!.updateUrl,
                    { downloaded, total ->
                        downloadTransferred = downloaded
                        downloadContentLength = total
                    },
                )

                if (apkFile != null) {
                    isDownloading = false
                    showUpdateDialog = false
                    showUpdateSuccessDialog = true
                    updateManager.installApk(apkFile)
                } else {
                    isDownloading = false
                    showUpdateDialog = false
                    showUpdateErrorDialog = true
                    errorMessage = "下载失败，请稍后重试"
                }
            } catch (e: Exception) {
                Log.e("SettingsScreen", "下载更新失败", e)
                isDownloading = false
                showUpdateDialog = false
                showUpdateErrorDialog = true
                errorMessage = "下载失败：${e.message}"
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    val isDarkTheme = isAppDarkTheme()
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    val glassTint = LiquidGlassDefaults.screenListCard
    val glassBg = glassTint.background(isDarkTheme)
    val glassBorder = glassTint.border(isDarkTheme)
    val glassHighlight = glassTint.highlight(isDarkTheme)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop)
        ) {
            AppPageBackgroundImage(
                modifier = Modifier.fillMaxSize(),
            )
            PlaylistPageDarkTintOverlay(enabled = isDarkTheme)
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentColor = if (isDarkTheme) Color(0xFFF0F0F5) else scheme.onSurface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.settings),
                        color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                            tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.9f) else scheme.onSurface
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    backgroundAlpha = glassBg,
                    borderAlpha = glassBorder,
                    highlightAlpha = glassHighlight
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(id = R.string.app_name),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${stringResource(id = R.string.version)} $versionName ($versionCode)",
                                    fontSize = 14.sp,
                                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else scheme.onSurfaceVariant
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                RoseRed,
                                                Color(0xFFFF6B9D)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.music),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingSection(title = stringResource(id = R.string.personalization_section)) {
                    SettingItem(
                        icon = Icons.Filled.Palette,
                        title = stringResource(id = R.string.personalization),
                        subtitle = themeSubtitle,
                        onClick = onNavigateToPersonalization
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingSection(title = stringResource(id = R.string.general)) {
                    SettingSwitchItem(
                        icon = Icons.Default.Info,
                        title = stringResource(id = R.string.cache_enabled),
                        subtitle = stringResource(id = R.string.cache_enabled_subtitle),
                        checked = isCacheEnabled,
                        onCheckedChange = { enabled ->
                            isCacheEnabled = enabled
                            cachePrefs.edit().putBoolean("cache_enabled", enabled).apply()
                        }
                    )
                    
                    if (isCacheEnabled) {
                        SettingItem(
                            icon = Icons.Default.Info,
                            title = stringResource(id = R.string.cache_management),
                            subtitle = stringResource(id = R.string.cached_songs, cachedMusicCount, cacheSize),
                            onClick = { onNavigateToCache() }
                        )
                    }
                    
                    SettingSwitchItem(
                        icon = Icons.Default.Info,
                        title = stringResource(id = R.string.focus_lock),
                        subtitle = stringResource(id = R.string.focus_lock_subtitle),
                        checked = isFocusLockEnabled,
                        onCheckedChange = { enabled ->
                            isFocusLockEnabled = enabled
                            focusLockPrefs.edit().putBoolean("focus_lock_enabled", enabled).apply()
                            
                            // 通知MusicPlayerManager重新应用音频属性
                            val playerManager = com.neko.music.service.MusicPlayerManager.getInstance(context)
                            playerManager.updateAudioAttributes(enabled)
                        }
                    )
                    
                    SettingItem(
                        icon = Icons.Default.Info,
                        title = stringResource(id = R.string.language),
                        subtitle = getLanguageDisplayName(context, currentLanguage),
                        onClick = { showLanguageDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(150.dp))
            }

            // 更新对话框
            if (showUpdateDialog && updateInfo != null) {
                AppUpdatePromptDialog(
                    versionName = updateInfo!!.versionName,
                    versionCode = updateInfo!!.versionCode,
                    onConfirm = { downloadAndInstall() },
                    onDismiss = { showUpdateDialog = false }
                )
            }

            if (isDownloading) {
                AppUpdateDownloadProgressDialog(
                    transferredBytes = downloadTransferred,
                    contentLengthBytes = downloadContentLength,
                    onDismiss = { isDownloading = false }
                )
            }

            if (showUpdateSuccessDialog) {
                AppUpdateSuccessDialog(
                    onDismiss = { showUpdateSuccessDialog = false }
                )
            }

            if (showUpdateErrorDialog) {
                AppUpdateErrorDialog(
                    message = errorMessage,
                    onDismiss = { showUpdateErrorDialog = false }
                )
            }
            
            // 语言选择对话框
            if (showLanguageDialog) {
                LanguageSelectionDialog(
                    currentLanguage = currentLanguage,
                    onDismiss = { showLanguageDialog = false },
                    onLanguageSelected = { languageCode ->
                        currentLanguage = languageCode
                        languagePrefs.edit().putString("language", languageCode).apply()
                        // 重新创建Activity以应用语言更改，保持在当前页面
                        if (context is android.app.Activity) {
                            context.recreate()
                        }
                    }
                )
            }

            }
        }
    }
    }
}

@Composable
fun SettingSection(
    title: String,
    useDarkAppearance: Boolean? = null,
    content: @Composable ColumnScope.() -> Unit
) {
        val isDark = useDarkAppearance ?: isAppDarkTheme()
        val glassTint = LiquidGlassDefaults.screenListCard
        val glassBg = glassTint.background(isDark)
        val glassBorder = glassTint.border(isDark)
        val glassHighlight = glassTint.highlight(isDark)
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )

            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                backgroundAlpha = glassBg,
                borderAlpha = glassBorder,
                highlightAlpha = glassHighlight
            ) {
                Column {
                    content()
                }
            }
        }
    }

@Composable
fun SettingItem(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        subtitle: String = "",
        showLoading: Boolean = false,
        onClick: () -> Unit = {}
    ) {
        var isPressed by remember { mutableStateOf(false) }
        val isDarkTheme = isAppDarkTheme()

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isPressed = true
                    onClick()
                },
            color = if (isPressed) 
                if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color(0xFFF5F5F5) 
            else Color.Transparent
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
                        contentDescription = title,
                        tint = RoseRed,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black
                    )
                    if (subtitle.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            fontSize = 13.sp,
                            color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                        )
                    }
                }

                if (showLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = RoseRed,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(id = R.string.more),
                        tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        LaunchedEffect(isPressed) {
            if (isPressed) {
                kotlinx.coroutines.delay(100)
                isPressed = false
            }
        }
}

@Composable
fun SettingSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    /** 个性化页等可传入预览用的深浅色，与 [SettingSection] 一致 */
    useDarkAppearance: Boolean? = null,
) {
    var isPressed by remember { mutableStateOf(false) }
    val isDarkTheme = useDarkAppearance ?: isAppDarkTheme()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        isPressed = true
                        onCheckedChange(!checked)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isPressed)
                            RoseRed.copy(alpha = 0.16f)
                        else
                            RoseRed.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = RoseRed,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                    )
                }
            }
            }

            Spacer(modifier = Modifier.width(12.dp))

            LiquidGlassSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                isDarkChrome = isDarkTheme
            )
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(100)
            isPressed = false
        }
    }
}

@Composable
fun LanguageSelectionDialog(
    currentLanguage: String,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    val isDarkTheme = isAppDarkTheme()
    val languages = listOf(
        "system" to stringResource(id = R.string.language_follow_system),
        "zh" to stringResource(id = R.string.language_zh),
        "nya" to stringResource(id = R.string.language_nya),
        "en" to stringResource(id = R.string.language_en)
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(id = R.string.language),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RoseRed
            )
        },
        text = {
            Column {
                languages.forEach { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onLanguageSelected(code)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentLanguage == code,
                            onClick = {
                                onLanguageSelected(code)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = RoseRed
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = name,
                            fontSize = 16.sp,
                            color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(id = R.string.cancel),
                    fontSize = 16.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                )
            }
        },
        containerColor = if (isDarkTheme) Color(0xFF1A1A2E).copy(alpha = 0.95f) else Color.White
    )
}

fun getLanguageDisplayName(context: Context, language: String): String {
    return when (language) {
        "system" -> context.getString(R.string.language_follow_system)
        "zh" -> context.getString(R.string.language_zh)
        "nya" -> context.getString(R.string.language_nya)
        "en" -> context.getString(R.string.language_en)
        else -> context.getString(R.string.language_follow_system)
    }
}
