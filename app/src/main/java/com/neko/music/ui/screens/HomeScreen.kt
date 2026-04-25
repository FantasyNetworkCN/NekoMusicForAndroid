package com.neko.music.ui.screens

import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.neko.music.R
import com.neko.music.util.UrlConfig
import com.neko.music.data.api.PlaylistApi
import com.neko.music.data.api.PlaylistInfo
import com.neko.music.data.manager.AppUpdateManager
import com.neko.music.data.manager.UpdateInfo
import com.neko.music.data.manager.InstallPermissionCallback
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.theme.Lilac
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.SakuraPink
import com.neko.music.ui.theme.SkyBlue
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

@Composable
fun HomeScreen(
    onSearchClick: () -> Unit = {},
    onNavigateToFavorite: () -> Unit = {},
    onNavigateToPlaylist: (Int) -> Unit = {},
    onNavigateToRanking: () -> Unit = {},
    onNavigateToLatest: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { AppUpdateManager(context) }
    val toastMessage = remember { androidx.compose.runtime.mutableStateOf("") }
    val showToast = remember { androidx.compose.runtime.mutableStateOf(false) }
    
    // 推荐歌单状态
    var recommendedPlaylists by remember { mutableStateOf<List<PlaylistInfo>>(emptyList()) }
    var rankingMusic by remember { mutableStateOf<List<com.neko.music.data.model.Music>>(emptyList()) }
    var latestMusic by remember { mutableStateOf<List<com.neko.music.data.model.Music>>(emptyList()) }
    var playlistsLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    
    // 获取推荐歌单
    LaunchedEffect(Unit) {
        playlistsLoading = true
        loadError = false
        scope.launch {
            try {
                // 并行获取热门音乐、最新音乐和歌单
                val playlistsDeferred = async {
                    Log.d("HomeScreen", "开始加载推荐歌单...")
                    val playlistApi = PlaylistApi(null, context)
                    playlistApi.searchPlaylists()
                }
                
                val rankingDeferred = async {
                    Log.d("HomeScreen", "开始加载热门音乐...")
                    val musicApi = com.neko.music.data.api.MusicApi(context)
                    musicApi.getRanking(200)
                }
                
                val latestDeferred = async {
                    Log.d("HomeScreen", "开始加载最新音乐...")
                    val musicApi = com.neko.music.data.api.MusicApi(context)
                    musicApi.getLatest(300)
                }
                
                val playlistResponse = playlistsDeferred.await()
                val rankingResult = rankingDeferred.await()
                val latestResult = latestDeferred.await()
                
                // 处理歌单响应
                if (playlistResponse.success && playlistResponse.playlists != null) {
                    recommendedPlaylists = playlistResponse.playlists.take(7)
                    Log.d("HomeScreen", "推荐歌单加载成功: ${playlistResponse.playlists.size}个")
                } else {
                    Log.e("HomeScreen", "推荐歌单加载失败: ${playlistResponse.message}")
                }
                
                // 处理热门音乐响应
                rankingResult.onSuccess { musicList ->
                    rankingMusic = musicList
                    Log.d("HomeScreen", "热门音乐加载成功: ${musicList.size}首")
                }.onFailure { error ->
                    Log.e("HomeScreen", "热门音乐加载失败: ${error.message}")
                }
                
                // 处理最新音乐响应
                latestResult.onSuccess { musicList ->
                    latestMusic = musicList
                    Log.d("HomeScreen", "最新音乐加载成功: ${musicList.size}首")
                }.onFailure { error ->
                    Log.e("HomeScreen", "最新音乐加载失败: ${error.message}")
                }
                
                if (playlistResponse.success && playlistResponse.playlists == null) {
                    loadError = true
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "推荐歌单异常: ${e.message}", e)
                loadError = true
            } finally {
                playlistsLoading = false
            }
        }
    }
    
    // 更新状态
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var showUpdateSuccessDialog by remember { mutableStateOf(false) }
    var showUpdateErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var pendingInstallFile by remember { mutableStateOf<File?>(null) }
    
    // 安装权限请求
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 权限请求完成后，再次检查权限并安装
        if (pendingInstallFile != null) {
            if (updateManager.hasInstallPermission()) {
                // 权限已授予，安装APK
                val success = updateManager.installApk(pendingInstallFile!!)
                if (!success) {
                    errorMessage = context.getString(R.string.install_failed)
                    showUpdateErrorDialog = true
                }
            } else {
                // 权限被拒绝
                errorMessage = context.getString(R.string.install_permission_denied)
                showUpdateErrorDialog = true
            }
        }
    }
    
    // 启动时检查更新
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val info = updateManager.checkUpdate()
                if (info != null && info.isUpdateAvailable) {
                    updateInfo = info
                    showUpdateDialog = true
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "检查更新失败", e)
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
                    
                    // 尝试安装APK，使用回调处理权限请求
                    val installCallback = object : InstallPermissionCallback {
                        override fun onRequestPermission() {
                            // 保存待安装的文件
                            pendingInstallFile = apkFile
                            // 启动权限请求
                            installPermissionLauncher.launch(updateManager.getInstallPermissionIntent())
                        }
                        
                        override fun onPermissionGranted() {
                            // 权限已授予，安装APK
                            updateManager.installApk(apkFile)
                        }
                        
                        override fun onPermissionDenied() {
                            // 权限被拒绝
                            errorMessage = context.getString(R.string.install_permission_denied)
                            showUpdateErrorDialog = true
                        }
                    }
                    
                    val installStarted = updateManager.installApk(apkFile, installCallback)
                    if (installStarted) {
                        // 安装已成功启动
                        showUpdateSuccessDialog = true
                    }
                } else {
                    isDownloading = false
                    showUpdateDialog = false
                    showUpdateErrorDialog = true
                    errorMessage = context.getString(R.string.downloading_update_failed)
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "下载更新失败", e)
                isDownloading = false
                showUpdateDialog = false
                showUpdateErrorDialog = true
                errorMessage = context.getString(R.string.downloading_update_failed_msg, e.message ?: "")
            }
        }
    }
    
    val view = LocalView.current
    SideEffect {
        val window = (view.context as android.app.Activity).window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }
    
    // 浮动动画
    val infiniteTransition = rememberInfiniteTransition(label = "floating")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.home_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // 显示土司消息
        if (showToast.value && toastMessage.value.isNotEmpty()) {
            LaunchedEffect(showToast.value) {
                if (showToast.value) {
                    kotlinx.coroutines.delay(2000)
                    showToast.value = false
                }
            }

            var toastVisible by remember { mutableStateOf(false) }
            val toastScale by animateFloatAsState(
                targetValue = if (toastVisible) 1f else 0.8f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            val toastAlpha by animateFloatAsState(
                targetValue = if (toastVisible) 1f else 0f,
                animationSpec = tween(durationMillis = 300)
            )

            LaunchedEffect(showToast.value) {
                if (showToast.value) {
                    toastVisible = true
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 40.dp)
                        .scale(toastScale)
                        .shadow(
                            elevation = 12.dp,
                            spotColor = RoseRed.copy(alpha = 0.35f),
                            ambientColor = Color.Gray.copy(alpha = 0.18f)
                        ),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.88f * toastAlpha)
                ) {
                    Text(
                        text = toastMessage.value,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 150.dp)
        ) {
            item {
                // 搜索框
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .statusBarsPadding()
                        .height(48.dp)
                        .clickable {
                            Log.d("HomeScreen", "搜索框被点击")
                            onSearchClick()
                        },
                    shape = RoundedCornerShape(16.dp),
                    backgroundAlpha = 0.35f,
                    borderAlpha = 0.18f
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(id = R.string.search),
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(id = R.string.search_music_artist_album),
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
                
                // 推荐歌单
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        backgroundAlpha = 0.32f,
                        borderAlpha = 0.15f,
                        highlightAlpha = 0.08f
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(20.dp)
                                        .background(
                                            Color.White.copy(alpha = 0.7f),
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(id = R.string.recommended_playlists),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.98f),
                                    letterSpacing = 0.3.sp,
                                    style = androidx.compose.ui.text.TextStyle(
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.5f),
                                            offset = Offset(0f, 1f),
                                            blurRadius = 6f
                                        )
                                    )
                                )
                            }
                            if (playlistsLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White.copy(alpha = 0.8f),
                                    strokeWidth = 2.5.dp
                                )
                            }
                        }
                    }
                    
                    if (playlistsLoading) {
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            shape = RoundedCornerShape(20.dp),
                            backgroundAlpha = 0.28f,
                            borderAlpha = 0.12f
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = Color.White.copy(alpha = 0.7f),
                                    strokeWidth = 2.5.dp
                                )
                            }
                        }
                    } else if (loadError) {
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            shape = RoundedCornerShape(20.dp),
                            backgroundAlpha = 0.28f,
                            borderAlpha = 0.12f
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.network_error_msg),
                                    fontSize = 16.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            // 热门音乐作为第一个
                            if (rankingMusic.isNotEmpty()) {
                                item {
                                    RankingMusicCard(
                                        musicList = rankingMusic,
                                        onClick = { onNavigateToRanking() }
                                    )
                                }
                            }
                            // 最新音乐
                            if (latestMusic.isNotEmpty()) {
                                item {
                                    LatestMusicCard(
                                        musicList = latestMusic,
                                        onClick = { onNavigateToLatest() }
                                    )
                                }
                            }
                            // 其他歌单
                            items(recommendedPlaylists) { playlist ->
                                PlaylistCard(
                                    playlist = playlist,
                                    onClick = { onNavigateToPlaylist(playlist.id) }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
    
    // 更新对话框
    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            versionName = updateInfo!!.versionName,
            versionCode = updateInfo!!.versionCode,
            onConfirm = { downloadAndInstall() },
            onDismiss = { showUpdateDialog = false }
        )
    }
    
    if (isDownloading) {
        DownloadProgressDialog(
            progress = downloadProgress,
            onDismiss = { isDownloading = false }
        )
    }
    
    if (showUpdateSuccessDialog) {
        UpdateSuccessDialog(
            onDismiss = { showUpdateSuccessDialog = false }
        )
    }
    
    if (showUpdateErrorDialog) {
        UpdateErrorDialog(
            message = errorMessage,
            onDismiss = { showUpdateErrorDialog = false }
        )
    }
}

@Composable
fun HeaderSection(
    onSearchClick: () -> Unit,
    floatOffset: Float
) {
    // Preload strings
    val searchText = stringResource(id = R.string.search)
    val searchMusicArtistAlbumText = stringResource(id = R.string.search_music_artist_album)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .statusBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // 搜索框 - 优化设计
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .shadow(
                        elevation = 4.dp,
                        spotColor = Color.Black.copy(alpha = 0.1f),
                        ambientColor = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 20.dp)
                    .clickable {
                        Log.d("HomeScreen", "搜索框被点击")
                        onSearchClick()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = searchText,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = searchMusicArtistAlbumText,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.95f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}



@Composable
fun QuickAccessItem(
    icon: Int,
    label: String,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Column(
        modifier = Modifier
            .scale(scale)
            .clickable {
                isPressed = true
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = gradientColors
                    )
                )
                .shadow(
                    elevation = 4.dp,
                    spotColor = RoseRed.copy(alpha = 0.2f),
                    ambientColor = Color.Gray.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium
        )
    }
}

// 更新提示组件
@Composable
fun UpdateDialog(
    versionName: String,
    versionCode: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .shadow(
                    elevation = 12.dp,
                    spotColor = RoseRed.copy(alpha = 0.35f),
                    ambientColor = Color.Gray.copy(alpha = 0.18f)
                )
        ) {
            Column(
                modifier = Modifier.padding(32.dp)
            ) {
                // 顶部图标
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    RoseRed.copy(alpha = 0.15f),
                                    SakuraPink.copy(alpha = 0.15f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.update),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(id = R.string.new_version_found),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseRed,
                    letterSpacing = 0.3.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(id = R.string.new_version, versionName),
                    fontSize = 17.sp,
                    color = Color.Gray.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(id = R.string.version_code, versionCode),
                    fontSize = 17.sp,
                    color = Color.Gray.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(id = R.string.later),
                            fontSize = 17.sp,
                            color = Color.Gray.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoseRed
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.update_now),
                            fontSize = 17.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadProgressDialog(
    progress: Float,
    onDismiss: () -> Unit
) {
    // Preload strings
    val downloadingUpdateText = stringResource(id = R.string.downloading_update)
    
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .shadow(
                    elevation = 12.dp,
                    spotColor = RoseRed.copy(alpha = 0.35f),
                    ambientColor = Color.Gray.copy(alpha = 0.18f)
                )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部图标
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    RoseRed.copy(alpha = 0.15f),
                                    SakuraPink.copy(alpha = 0.15f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.download),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = downloadingUpdateText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseRed,
                    letterSpacing = 0.3.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = RoseRed,
                    trackColor = Color.Gray.copy(alpha = 0.25f)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 18.sp,
                    color = Color.Gray.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun UpdateSuccessDialog(
    onDismiss: () -> Unit
) {
    // Preload strings
    val downloadCompleteText = stringResource(id = R.string.download_complete)
    val installingUpdateText = stringResource(id = R.string.installing_update)
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .shadow(
                    elevation = 12.dp,
                    spotColor = Color(0xFF4CAF50).copy(alpha = 0.35f),
                    ambientColor = Color.Gray.copy(alpha = 0.18f)
                )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部图标
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF4CAF50).copy(alpha = 0.15f),
                                    Color(0xFF66BB6A).copy(alpha = 0.15f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = Color(0xFF4CAF50),
                                shape = RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "√",
                            fontSize = 28.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = downloadCompleteText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    letterSpacing = 0.3.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = installingUpdateText,
                    fontSize = 17.sp,
                    color = Color.Gray.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun UpdateErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    // Preload strings
    val updateFailedText = stringResource(id = R.string.update_failed)
    val confirmText = stringResource(id = R.string.confirm)
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .shadow(
                    elevation = 12.dp,
                    spotColor = Color(0xFFF44336).copy(alpha = 0.35f),
                    ambientColor = Color.Gray.copy(alpha = 0.18f)
                )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部图标
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFF44336).copy(alpha = 0.15f),
                                    Color(0xFFEF5350).copy(alpha = 0.15f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = Color(0xFFF44336),
                                shape = RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "×",
                            fontSize = 32.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = updateFailedText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF44336),
                    letterSpacing = 0.3.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = message,
                    fontSize = 17.sp,
                    color = Color.Gray.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = confirmText,
                            fontSize = 17.sp,
                            color = RoseRed,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

}

@Composable
fun MusicCard(
    music: com.neko.music.data.model.Music,
    rank: Int = 0,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val musicApi = remember { com.neko.music.data.api.MusicApi(context) }
    var coverUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(music.id) {
        coverUrl = musicApi.getMusicCoverUrl(music)
    }

    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 封面
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            SakuraPink.copy(alpha = 0.3f),
                            SkyBlue.copy(alpha = 0.3f)
                        )
                    )
                )
                .shadow(
                    elevation = 4.dp,
                    spotColor = RoseRed.copy(alpha = 0.2f),
                    ambientColor = Color.Gray.copy(alpha = 0.1f)
                )
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl ?: UrlConfig.getDefaultAvatarUrl())
                    .crossfade(true)
                    .build(),
                contentDescription = music.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // 排名徽章
            if (rank > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(
                            color = when (rank) {
                                1 -> Color(0xFFFFD700)
                                2 -> Color(0xFFC0C0C0)
                                3 -> Color(0xFFCD7F32)
                                else -> Color.Black.copy(alpha = 0.6f)
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$rank",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 播放次数标签
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.play_count, music.playCount ?: 0),
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 歌曲名称
        Text(
            text = music.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.95f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 歌手
        Text(
            text = music.artist,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.65f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RankingCard(
    musicList: List<com.neko.music.data.model.Music>,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        RoseRed.copy(alpha = 0.15f),
                        SakuraPink.copy(alpha = 0.1f)
                    )
                )
            )
            .clickable {
                isPressed = true
                onClick()
            }
            .padding(16.dp)
    ) {
        // 显示前3首热门音乐
        musicList.take(3).forEachIndexed { index, music ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 排名
                Text(
                    text = "${index + 1}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (index) {
                        0 -> Color(0xFFFFD700) // 金色
                        1 -> Color(0xFFC0C0C0) // 银色
                        2 -> Color(0xFFCD7F32) // 铜色
                        else -> Color.White
                    },
                    modifier = Modifier.width(30.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 封面
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(UrlConfig.getMusicCoverUrl(music.id))
                        .crossfade(true)
                        .build(),
                    contentDescription = music.title,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 歌曲信息
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = music.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.95f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = music.artist,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                
                // 播放次数
                Text(
                    text = stringResource(id = R.string.play_count, music.playCount ?: 0),
                    fontSize = 12.sp,
                    color = RoseRed.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // 显示总数
        if (musicList.size > 3) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(id = R.string.total_hot_music, musicList.size),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun PlaylistCard(
    playlist: PlaylistInfo,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Column(
        modifier = Modifier
            .width(160.dp)
            .scale(scale)
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) {
                isPressed = true
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 封面
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(20.dp))
                .shadow(
                    elevation = 8.dp,
                    spotColor = RoseRed.copy(alpha = 0.25f),
                    ambientColor = Color.Gray.copy(alpha = 0.12f)
                )
        ) {
            // 背景渐变
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                SakuraPink.copy(alpha = 0.3f),
                                SkyBlue.copy(alpha = 0.25f),
                                Lilac.copy(alpha = 0.2f)
                            )
                        )
                    )
            )
            
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(
                        when {
                            !playlist.firstMusicCover.isNullOrEmpty() -> playlist.firstMusicCover
                            !playlist.coverPath.isNullOrEmpty() -> playlist.coverPath
                            else -> UrlConfig.getDefaultAvatarUrl()
                        }
                    )
                    .crossfade(true)
                    .build(),
                contentDescription = playlist.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // 渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f)
                            )
                        )
                    )
            )

            // 光泽效果
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.White.copy(alpha = 0.05f)
                            ),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(160f, 160f)
                        )
                    )
            )

            // 音乐数量标签
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(
                        color = Color(0xFF1A1A2E).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.songs_count_format, playlist.musicCount),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.95f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.7f),
                            offset = Offset(0f, 1f),
                            blurRadius = 4f
                        )
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 歌单名称
        Text(
            text = playlist.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.98f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.width(160.dp),
            letterSpacing = 0.2.sp,
            style = androidx.compose.ui.text.TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.6f),
                    offset = Offset(0f, 1f),
                    blurRadius = 5f
                )
            )
        )

        Spacer(modifier = Modifier.height(5.dp))

        // 歌单描述
        Text(
            text = playlist.description ?: stringResource(id = R.string.no_description),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.width(160.dp),
            fontWeight = FontWeight.Medium,
            style = androidx.compose.ui.text.TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.5f),
                    offset = Offset(0f, 1f),
                    blurRadius = 4f
                )
            )
        )
    }
}

@Composable
fun RankingMusicCard(
    musicList: List<com.neko.music.data.model.Music>,
    onClick: () -> Unit
) {
    // Preload strings
    val hotMusicText = stringResource(id = R.string.hot_music)
    val hotMusicDescText = stringResource(id = R.string.hot_music_desc)
    val songsCountShortText = stringResource(id = R.string.songs_count_short, musicList.size)
    
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Column(
        modifier = Modifier
            .width(160.dp)
            .scale(scale)
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) {
                isPressed = true
                android.util.Log.d("RankingMusicCard", "点击热门音乐，共${musicList.size}首")
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 封面
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(20.dp))
                .shadow(
                    elevation = 8.dp,
                    spotColor = RoseRed.copy(alpha = 0.3f),
                    ambientColor = Color.Gray.copy(alpha = 0.15f)
                )
        ) {
            // 背景渐变
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                RoseRed.copy(alpha = 0.35f),
                                SakuraPink.copy(alpha = 0.25f),
                                Color(0xFFFF6B9D).copy(alpha = 0.2f)
                            )
                        )
                    )
            )
            
            if (musicList.isNotEmpty()) {
                val topMusic = musicList[0]
                val context = LocalContext.current
                val musicApi = remember { com.neko.music.data.api.MusicApi(context) }
                var coverUrl by remember { mutableStateOf<String?>(null) }
                
                LaunchedEffect(topMusic.id) {
                    coverUrl = musicApi.getMusicCoverUrl(topMusic)
                }
                
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverUrl ?: UrlConfig.getDefaultAvatarUrl())
                        .crossfade(true)
                        .build(),
                    contentDescription = hotMusicText,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.music),
                        contentDescription = hotMusicText,
                        modifier = Modifier.size(56.dp),
                        alpha = 0.7f
                    )
                }
            }
            
            // 渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f)
                            )
                        )
                    )
            )

            // 光泽效果
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.White.copy(alpha = 0.05f)
                            ),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(160f, 160f)
                        )
                    )
            )

            // 音乐数量标签
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(
                        color = Color(0xFF1A1A2E).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = songsCountShortText,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.95f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.7f),
                            offset = Offset(0f, 1f),
                            blurRadius = 4f
                        )
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 标题
        Text(
            text = hotMusicText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.98f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.width(160.dp),
            letterSpacing = 0.2.sp,
            style = androidx.compose.ui.text.TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.6f),
                    offset = Offset(0f, 1f),
                    blurRadius = 5f
                )
            )
        )

        Spacer(modifier = Modifier.height(5.dp))

        // 描述
        Text(
            text = hotMusicDescText,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.width(160.dp),
            fontWeight = FontWeight.Medium,
            style = androidx.compose.ui.text.TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.5f),
                    offset = Offset(0f, 1f),
                    blurRadius = 4f
                )
            )
        )
    }
}
