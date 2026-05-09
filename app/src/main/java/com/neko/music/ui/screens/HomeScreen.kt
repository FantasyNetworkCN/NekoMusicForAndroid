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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.neko.music.ui.components.AppUpdateDownloadProgressDialog
import com.neko.music.ui.components.AppUpdateErrorDialog
import com.neko.music.ui.components.AppUpdatePromptDialog
import com.neko.music.ui.components.AppUpdateSuccessDialog
import com.neko.music.ui.home.HomeLiquidHeroState
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
    liquidHeroState: HomeLiquidHeroState,
    heroInsetPx: Int,
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
    
    // 推荐歌单等数据写入 [liquidHeroState]，供页内 [HomeLiquidHeroOverlay] 真液态采样
    LaunchedEffect(Unit) {
        liquidHeroState.playlistsLoading = true
        liquidHeroState.loadError = false
        scope.launch {
            try {
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

                if (playlistResponse.success && playlistResponse.playlists != null) {
                    liquidHeroState.recommendedPlaylists = playlistResponse.playlists.take(7)
                    Log.d("HomeScreen", "推荐歌单加载成功: ${playlistResponse.playlists.size}个")
                } else {
                    Log.e("HomeScreen", "推荐歌单加载失败: ${playlistResponse.message}")
                }

                rankingResult.onSuccess { musicList ->
                    liquidHeroState.rankingMusic = musicList
                    Log.d("HomeScreen", "热门音乐加载成功: ${musicList.size}首")
                }.onFailure { error ->
                    Log.e("HomeScreen", "热门音乐加载失败: ${error.message}")
                }

                latestResult.onSuccess { musicList ->
                    liquidHeroState.latestMusic = musicList
                    Log.d("HomeScreen", "最新音乐加载成功: ${musicList.size}首")
                }.onFailure { error ->
                    Log.e("HomeScreen", "最新音乐加载失败: ${error.message}")
                }

                if (playlistResponse.success && playlistResponse.playlists == null) {
                    liquidHeroState.loadError = true
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "推荐歌单异常: ${e.message}", e)
                liquidHeroState.loadError = true
            } finally {
                liquidHeroState.playlistsLoading = false
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
    
    val density = LocalDensity.current
    val heroTopInsetDp = remember(heroInsetPx, density) {
        if (heroInsetPx > 0) with(density) { heroInsetPx.toDp() } else 380.dp
    }

    // 背景与列表仅由 NavHost 的 layerBackdrop(liquidBackdrop) 录屏；搜索/推荐玻璃在 MainActivity 中叠在 NavHost 外采样。
    Box(modifier = Modifier.fillMaxSize()) {
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
            contentPadding = PaddingValues(bottom = 250.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(heroTopInsetDp))
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
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
            progress = downloadProgress,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isDarkCard = scheme.background.luminance() < 0.5f
    val cardTitleColor = if (isDarkCard) Color.White.copy(alpha = 0.98f) else scheme.onSurface
    val cardSubtitleColor = if (isDarkCard) Color.White.copy(alpha = 0.88f) else scheme.onSurfaceVariant
    val cardTitleShadow =
        if (isDarkCard) {
            Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 1f), 4f)
        } else {
            Shadow(Color.Black.copy(alpha = 0.08f), Offset(0f, 1f), 2.5f)
        }
    val cardSubtitleShadow =
        if (isDarkCard) {
            Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1f), 3f)
        } else {
            Shadow(Color.Black.copy(alpha = 0.06f), Offset(0f, 1f), 2f)
        }

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Column(
        modifier = modifier
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
                .fillMaxWidth()
                .aspectRatio(1f)
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

        // 文字信息
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            // 歌单名称
            Text(
                text = playlist.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = cardTitleColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                letterSpacing = 0.2.sp,
                style = androidx.compose.ui.text.TextStyle(shadow = cardTitleShadow)
            )

            Spacer(modifier = Modifier.height(3.dp))

            // 歌单描述
            Text(
                text = playlist.description ?: stringResource(id = R.string.no_description),
                fontSize = 12.sp,
                color = cardSubtitleColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                style = androidx.compose.ui.text.TextStyle(shadow = cardSubtitleShadow)
            )
        }
    }
}

@Composable
fun RankingMusicCard(
    musicList: List<com.neko.music.data.model.Music>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Preload strings
    val hotMusicText = stringResource(id = R.string.hot_music)
    val hotMusicDescText = stringResource(id = R.string.hot_music_desc)
    val songsCountShortText = stringResource(id = R.string.songs_count_short, musicList.size)

    val scheme = MaterialTheme.colorScheme
    val isDarkCard = scheme.background.luminance() < 0.5f
    val cardTitleColor = if (isDarkCard) Color.White.copy(alpha = 0.98f) else scheme.onSurface
    val cardSubtitleColor = if (isDarkCard) Color.White.copy(alpha = 0.88f) else scheme.onSurfaceVariant
    val cardTitleShadow =
        if (isDarkCard) Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 1f), 4f)
        else Shadow(Color.Black.copy(alpha = 0.08f), Offset(0f, 1f), 2.5f)
    val cardSubtitleShadow =
        if (isDarkCard) Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1f), 3f)
        else Shadow(Color.Black.copy(alpha = 0.06f), Offset(0f, 1f), 2f)

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Column(
        modifier = modifier
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
                .fillMaxWidth()
                .aspectRatio(1f)
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

        // 文字信息
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = hotMusicText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = cardTitleColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                letterSpacing = 0.2.sp,
                style = androidx.compose.ui.text.TextStyle(shadow = cardTitleShadow)
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = hotMusicDescText,
                fontSize = 12.sp,
                color = cardSubtitleColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                style = androidx.compose.ui.text.TextStyle(shadow = cardSubtitleShadow)
            )
        }
    }
}
