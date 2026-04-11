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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.music.data.manager.AppUpdateManager
import com.neko.music.data.manager.UpdateInfo
import com.neko.music.ui.theme.RoseRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    onNavigateToCache: () -> Unit = {}
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
    var downloadProgress by remember { mutableStateOf(0f) }
    var showUpdateSuccessDialog by remember { mutableStateOf(false) }
    var showUpdateErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // 缓存设置 - 使用与 MusicCacheManager 相同的 SharedPreferences
    val cachePrefs = remember { context.getSharedPreferences("music_cache", Context.MODE_PRIVATE) }
    var isCacheEnabled by remember { mutableStateOf(cachePrefs.getBoolean("cache_enabled", true)) }
    
    // FuckChinaOS 悬浮窗设置
    val floatPrefs = remember { context.getSharedPreferences("float_window", Context.MODE_PRIVATE) }
    var isFuckChinaOSEnabled by remember { mutableStateOf(floatPrefs.getBoolean("fuck_china_os_enabled", false)) }
    
    // 焦点锁定设置
    val focusLockPrefs = remember { context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE) }
    var isFocusLockEnabled by remember { mutableStateOf(focusLockPrefs.getBoolean("focus_lock_enabled", false)) }
    
    // 语言设置
    val languagePrefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var currentLanguage by remember { mutableStateOf(languagePrefs.getString("language", "system") ?: "system") }
    var showLanguageDialog by remember { mutableStateOf(false) }
    
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

    // 记录用户是否尝试开启灵动岛但被权限阻止
    var pendingFuckChinaOSEnable by remember { mutableStateOf(false) }

    // 监听应用生命周期，在恢复时重新检查权限并自动启动灵动岛
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
                
                // 如果用户之前尝试开启灵动岛但被权限阻止，现在有了权限，自动开启
                if (pendingFuckChinaOSEnable && newPermission && !isFuckChinaOSEnabled) {
                    isFuckChinaOSEnabled = true
                    floatPrefs.edit().putBoolean("fuck_china_os_enabled", true).apply()
                    val intent = Intent(context, com.neko.music.floatwindow.FuckChinaOSFloatService::class.java)
                    intent.action = com.neko.music.floatwindow.FuckChinaOSFloatService.ACTION_SHOW
                    context.startService(intent)
                    pendingFuckChinaOSEnable = false
                }
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
            downloadProgress = 0f

            // 清理所有旧的更新文件
            updateManager.cleanupUpdateFiles()

            try {
                val apkFile = updateManager.downloadApk(
                    updateInfo!!.updateUrl,
                    { downloaded, total ->
                        if (total > 0) {
                            downloadProgress = downloaded.toFloat() / total.toFloat()
                        }
                    }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(id = R.string.settings),
                        color = if (isSystemInDarkTheme()) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                            tint = if (isSystemInDarkTheme()) Color(0xFFB8B8D1).copy(alpha = 0.9f) else Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isSystemInDarkTheme()) Color(0xFF1A1A2E).copy(alpha = 0.95f) else Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(if (isSystemInDarkTheme()) Color(0xFF121228) else Color(0xFFFAFAFA))
        ) {
            // 设置列表
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // 版本信息卡片
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSystemInDarkTheme()) Color(0xFF252545).copy(alpha = 0.6f) else Color.White
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 2.dp
                    )
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
                                    color = if (isSystemInDarkTheme()) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${stringResource(id = R.string.version)} $versionName ($versionCode)",
                                    fontSize = 14.sp,
                                    color = if (isSystemInDarkTheme()) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
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

                Spacer(modifier = Modifier.height(16.dp))

                SettingSection(title = "FuckChinaOS") {
                    SettingSwitchItem(
                        icon = Icons.Default.Info,
                        title = stringResource(id = R.string.fuck_china_os),
                        subtitle = stringResource(id = R.string.fuck_china_os_subtitle),
                        checked = isFuckChinaOSEnabled,
                        onCheckedChange = { enabled ->
                            // 如果开启但没有权限，先请求权限（开关状态不变，等用户授权后手动开启）
                            if (enabled && !hasOverlayPermission) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                                pendingFuckChinaOSEnable = true
                            } else {
                                // 有权限或关闭时，直接执行
                                isFuckChinaOSEnabled = enabled
                                floatPrefs.edit().putBoolean("fuck_china_os_enabled", enabled).apply()
                                
                                // 控制悬浮窗服务
                                val serviceIntent = Intent(context, com.neko.music.floatwindow.FuckChinaOSFloatService::class.java)
                                if (enabled) {
                                    serviceIntent.action = com.neko.music.floatwindow.FuckChinaOSFloatService.ACTION_SHOW
                                    context.startService(serviceIntent)
                                } else {
                                    serviceIntent.action = com.neko.music.floatwindow.FuckChinaOSFloatService.ACTION_HIDE
                                    context.startService(serviceIntent)
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(150.dp))
            }

            // 更新对话框
            if (showUpdateDialog && updateInfo != null) {
                SettingsUpdateDialog(
                    versionName = updateInfo!!.versionName,
                    versionCode = updateInfo!!.versionCode,
                    onConfirm = { downloadAndInstall() },
                    onDismiss = { showUpdateDialog = false }
                )
            }

            if (isDownloading) {
                SettingsDownloadProgressDialog(
                    progress = downloadProgress,
                    onDismiss = { isDownloading = false }
                )
            }

            if (showUpdateSuccessDialog) {
                SettingsUpdateSuccessDialog(
                    onDismiss = { showUpdateSuccessDialog = false }
                )
            }

            if (showUpdateErrorDialog) {
                SettingsUpdateErrorDialog(
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

@Composable
fun SettingSection(
        title: String,
        content: @Composable ColumnScope.() -> Unit
    ) {
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSystemInDarkTheme()) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSystemInDarkTheme()) Color(0xFF252545).copy(alpha = 0.6f) else Color.White
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                )
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
        val isDarkTheme = isSystemInDarkTheme()

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

// 对话框组件
@Composable
fun SettingsUpdateDialog(
        versionName: String,
        versionCode: Int,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
    val isDarkTheme = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            // 喵！用 Row 把图标和文字排在一起
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    // 使用自带的更新图标，或者主人有特定的 DW 图标资源也可以换成 painterResource
                    painter = painterResource(id = R.drawable.update),
                    contentDescription = null,
                    tint = RoseRed,
                    modifier = Modifier.size(24.dp) // 比文字稍微大一点点会更好看喵
                )
                Spacer(modifier = Modifier.width(8.dp)) // 给图标和文字留点小缝隙
                Text(
                    text = stringResource(id = R.string.new_version_found),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseRed
                )
            }
        },
        text = {
            Column {
                Text(
                    text = stringResource(id = R.string.new_version, versionName),
                    fontSize = 16.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.version_code_display, versionCode),
                    fontSize = 16.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoseRed
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.update_now),
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(id = R.string.later),
                    fontSize = 16.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                )
            }
        },
        containerColor = if (isDarkTheme) Color(0xFF1A1A2E).copy(alpha = 0.95f) else Color.White
    )
}

@Composable
fun SettingsDownloadProgressDialog(
        progress: Float,
        onDismiss: () -> Unit
    ) {
    val isDarkTheme = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                text = stringResource(id = R.string.downloading_update_progress),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RoseRed
            )
        },
        text = {
            Column {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = RoseRed,
                    trackColor = if (isDarkTheme) Color(0xFF353558).copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 16.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        confirmButton = {},
        containerColor = if (isDarkTheme) Color(0xFF1A1A2E).copy(alpha = 0.95f) else Color.White
    )

}

@Composable
fun SettingsUpdateSuccessDialog(
        onDismiss: () -> Unit
    ) {
    val isDarkTheme = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    text = "✓",
                    fontSize = 56.sp,
                    color = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.download_complete),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black
                )
            }
        },
        text = {
            Text(
                text = stringResource(id = R.string.installing_update),
                fontSize = 16.sp,
                color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        confirmButton = {},
        containerColor = if (isDarkTheme) Color(0xFF1A1A2E).copy(alpha = 0.95f) else Color.White
    )
}

@Composable
fun SettingsUpdateErrorDialog(
        message: String,
        onDismiss: () -> Unit
    ) {
    val isDarkTheme = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(id = R.string.update_failed_msg),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF44336)
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 16.sp,
                color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(id = R.string.confirm),
                    fontSize = 16.sp,
                    color = RoseRed
                )
            }
        },
        containerColor = if (isDarkTheme) Color(0xFF1A1A2E).copy(alpha = 0.95f) else Color.White
    )
}
@Composable
fun SettingSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val isDarkTheme = isSystemInDarkTheme()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                isPressed = true
                onCheckedChange(!checked)
            },
        color = if (isPressed) 
            if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color(0xFFF5F5F5) 
        else Color.Transparent,
        onClick = {}
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

            Spacer(modifier = Modifier.width(16.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = RoseRed,
                    checkedTrackColor = RoseRed.copy(alpha = 0.5f),
                    uncheckedThumbColor = if (isDarkTheme) Color(0xFFB8B8D1) else Color.Gray,
                    uncheckedTrackColor = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.5f)
                )
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
    val isDarkTheme = isSystemInDarkTheme()
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
