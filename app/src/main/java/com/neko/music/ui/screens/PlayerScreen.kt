package com.neko.music.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.Image
import android.os.Build
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width as composeWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import com.neko.music.service.MusicPlayerManager
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import com.neko.music.service.PlayMode
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.data.api.PlaylistApi
import com.neko.music.data.api.PlaylistInfo
import com.neko.music.data.api.PlaylistListResponse
import com.neko.music.data.model.Music
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.SakuraPink
import androidx.compose.animation.core.Spring
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import kotlinx.coroutines.launch

// 格式化时间显示（毫秒转 mm:ss）
fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    return String.format("%d:%02d", minutes, seconds)
}

// LRC歌词行数据类
data class LrcLine(
    val time: Float, // 时间（秒）
    val text: String, // 歌词文本
    val translation: String = "" // 翻译文本（可选）
)

// 解析 LRC 格式歌词（支持双语）
fun parseLrcLyrics(lrcText: String): List<LrcLine> {
    android.util.Log.d("parseLrcLyrics", "开始解析歌词，文本长度: ${lrcText.length}")
    val lines = lrcText.lines()
    android.util.Log.d("parseLrcLyrics", "歌词行数: ${lines.size}")
    val result = mutableListOf<LrcLine>()
    
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()
        
        // 跳过空行
        if (line.isEmpty()) {
            i++
            continue
        }
        
        // 匹配时间标签 [mm:ss.xx]（强制两位毫秒）
        val timePattern = Regex("""\[(\d{1,2}):(\d{1,2})\.(\d{2})\]""")
        val match = timePattern.find(line)
        
        if (match != null) {
            val minutes = match.groupValues[1].toInt()
            val seconds = match.groupValues[2].toInt()
            val milliseconds = match.groupValues[3].toInt()
            
            // 计算时间（毫秒只有两位，所以直接除以 100）
            val time = minutes * 60 + seconds + milliseconds / 100f
            
            // 提取歌词文本（移除时间标签）
            val text = line.replace(timePattern, "").trim()
            
            // 查找下一行是否有翻译
            var translation = ""
            if (i + 1 < lines.size) {
                val nextLine = lines[i + 1].trim()
                android.util.Log.d("parseLrcLyrics", "检查翻译行: $nextLine")
                // 检查是否是JSON格式的翻译行
                // 修复：正确匹配JSON格式的翻译
                if (nextLine.startsWith("{") && nextLine.endsWith("}")) {
                    // 移除开头和结尾的花括号
                    var jsonContent = nextLine.substring(1, nextLine.length - 1)
                    android.util.Log.d("parseLrcLyrics", "移除花括号后: $jsonContent")
                    
                    // 移除首尾的引号（包括转义的引号）
                    // 处理转义的引号 {"content"} -> \"content\" -> content
                    if (jsonContent.startsWith("\"") && jsonContent.endsWith("\"")) {
                        jsonContent = jsonContent.substring(1, jsonContent.length - 1)
                    } else if (jsonContent.startsWith("'") && jsonContent.endsWith("'")) {
                        jsonContent = jsonContent.substring(1, jsonContent.length - 1)
                    }
                    
                    android.util.Log.d("parseLrcLyrics", "移除外层引号后: $jsonContent")
                    
                    // 移除可能的转义引号
                    translation = jsonContent.replace("\\\"", "")
                    android.util.Log.d("parseLrcLyrics", "移除转义引号后: $translation")
                    i++ // 跳过翻译行
                }
            }
            
            if (text.isNotEmpty()) {
                result.add(LrcLine(time, text, translation))
                android.util.Log.d("parseLrcLyrics", "添加歌词行: 时间=$time, 原文=$text, 翻译=$translation")
            }
        }
        
        i++
    }
    
    android.util.Log.d("parseLrcLyrics", "解析完成，共 ${result.size} 行歌词")
    return result
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PlayerScreen(
    music: Music,
    onBackClick: () -> Unit,
    onPlaylistClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val playerManager = MusicPlayerManager.getInstance(context)
    val tokenManager = com.neko.music.data.manager.TokenManager(context)

    val isPlaying by playerManager.isPlaying.collectAsState()
    val currentPosition by playerManager.currentPosition.collectAsState()
    val duration by playerManager.duration.collectAsState()
    val currentMusicId by playerManager.currentMusicId.collectAsState()
    val currentMusicTitle by playerManager.currentMusicTitle.collectAsState()
    val currentMusicArtist by playerManager.currentMusicArtist.collectAsState()
    val currentMusicCover by playerManager.currentMusicCover.collectAsState()

    // 检查登录状态
    val isLoggedIn = tokenManager.isLoggedIn()

    val currentTime by remember { derivedStateOf { formatTime(currentPosition) } }
    val totalTime by remember { derivedStateOf { formatTime(duration) } }
    val currentProgressSeconds by remember { derivedStateOf { currentPosition / 1000f } }

    var musicFileUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
    val lyricsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isFavorite by playerManager.isFavorite.collectAsState()
    var showLyrics by remember { mutableStateOf(false) }
    val playMode by playerManager.playMode.collectAsState()
    val playbackSpeed by playerManager.playbackSpeed.collectAsState()
    val sleepTimerMinutes by playerManager.sleepTimerMinutes.collectAsState()
    val sleepTimerRemainingSeconds by playerManager.sleepTimerRemainingSeconds.collectAsState()

    // 通知权限
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }
    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    // 桌面歌词设置
    val desktopLyricPrefs = remember { context.getSharedPreferences("desktop_lyric", Context.MODE_PRIVATE) }
    var isDesktopLyricEnabled by remember { mutableStateOf(desktopLyricPrefs.getBoolean("desktop_lyric_enabled", false)) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }

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

    // VR设备检查
    val isVRDevice by remember { 
        mutableStateOf(com.neko.music.util.DeviceDetector.isVRDevice()) 
    }
    
    // 3D空间HUD检查
    var is3DSpatialHUDAvailable by remember {
        mutableStateOf(
            if (isVRDevice) {
                com.neko.music.desktoplyric.VRHUDLyricManager.getInstance(context).is3DSpatialHUDAvailable()
            } else {
                false
            }
        )
    }

    // 登录提示
    
    val playModeChanged by playerManager.playModeChanged.collectAsState()

    // 分享对话框
    var showShareDialog by remember { mutableStateOf(false) }

    // 添加到歌单
    var playlists by remember { mutableStateOf<List<com.neko.music.data.api.PlaylistInfo>>(emptyList()) }
    var selectedPlaylistId by remember { mutableStateOf<Int?>(null) }
    var playlistFirstMusicCovers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    // 创建歌单对话框状态
    var showCreateDialog by remember { mutableStateOf(false) }
    var dialogPlaylistName by remember { mutableStateOf("") }

    // 预加载字符串资源
    val sleepTimerCancelled = stringResource(id = R.string.sleep_timer_cancelled)
    val pleaseLoginFirst = stringResource(id = R.string.please_login_first)
    val createSuccess = stringResource(id = R.string.create_success)
    val listLoop = stringResource(id = R.string.list_loop)
    val singleLoop = stringResource(id = R.string.single_loop)
    val shufflePlay = stringResource(id = R.string.shuffle_play)
    val linkCopied = stringResource(id = R.string.link_copied)
    val copyFailed = stringResource(id = R.string.copy_failed)
    val shareFailed = stringResource(id = R.string.share_failed)

    // 从播放器获取当前音乐信息
    val currentMusic = remember(currentMusicId) {
        val id = currentMusicId
        val title = currentMusicTitle
        val artist = currentMusicArtist
        if (id != null && title != null && artist != null) {
            Music(
                id = id,
                title = title,
                artist = artist,
                album = "",
                duration = duration.toInt(),
                filePath = musicFileUrl ?: "",
                coverFilePath = currentMusicCover ?: "",
                uploadUserId = 0,
                createdAt = ""
            )
        } else {
            music
        }
    }

    val musicApi = remember { MusicApi(context) }
    val scope = rememberCoroutineScope()

    // 加载音乐文件URL，只在音乐ID不同时才重新播放
    LaunchedEffect(music.id) {
        isLoading = true
        scope.launch {
            musicFileUrl = musicApi.getMusicFileUrl(music)
            isLoading = false
            musicFileUrl?.let { url ->
                // 只在音乐ID不同时才播放
                if (currentMusicId != music.id) {
                    // 获取完整的封面URL
                    val fullCoverUrl = if (!music.coverFilePath.isNullOrEmpty()) {
                        "https://music.cnmsb.xin${music.coverFilePath}"
                    } else {
                        "https://music.cnmsb.xin/api/music/cover/${music.id}"
                    }
                    Log.d(
                        "PlayerScreen",
                        "封面URL: $fullCoverUrl, coverFilePath: ${music.coverFilePath}"
                    )
                    playerManager.playMusic(
                        url,
                        music.id,
                        music.title,
                        music.artist,
                        music.coverFilePath ?: "",
                        fullCoverUrl
                    )
                }
            }
            Log.d("PlayerScreen", "音乐文件URL: $musicFileUrl, 当前音乐ID: $currentMusicId")
        }
    }

    // 加载歌词
    LaunchedEffect(currentMusic.id) {
        scope.launch {
            val result = musicApi.getMusicLyrics(currentMusic)
            result.fold(
                onSuccess = { lyricsText ->
                    lyrics = parseLrcLyrics(lyricsText)
                    Log.d("PlayerScreen", "歌词加载成功，共 ${lyrics.size} 行")
                },
                onFailure = { error ->
                    Log.e("PlayerScreen", "歌词加载失败: ${error.message}")
                }
            )
        }
    }

    // 监听桌面歌词状态变化
    LaunchedEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "desktop_lyric_enabled") {
                isDesktopLyricEnabled = desktopLyricPrefs.getBoolean("desktop_lyric_enabled", false)
            }
        }
        desktopLyricPrefs.registerOnSharedPreferenceChangeListener(listener)
        
        try {
            kotlinx.coroutines.delay(Long.MAX_VALUE)
        } finally {
            desktopLyricPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // 监听悬浮窗权限状态变化
    LaunchedEffect(Unit) {
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    // 桌面歌词切换逻辑
    val toggleDesktopLyric = {
        if (isDesktopLyricEnabled) {
            // 关闭桌面歌词
            isDesktopLyricEnabled = false
            desktopLyricPrefs.edit().putBoolean("desktop_lyric_enabled", false).apply()
            val serviceIntent = Intent(context, com.neko.music.desktoplyric.DesktopLyricService::class.java)
            serviceIntent.action = com.neko.music.desktoplyric.DesktopLyricService.ACTION_HIDE
            context.startService(serviceIntent)
        } else {
            // 开启桌面歌词，先检查权限
            if (isVRDevice) {
                // VR设备使用3D空间HUD
                if (!is3DSpatialHUDAvailable) {
                    android.util.Log.w("PlayerScreen", "3D Spatial HUD not available on VR device")
                } else {
                    isDesktopLyricEnabled = true
                    desktopLyricPrefs.edit().putBoolean("desktop_lyric_enabled", true).apply()
                    val serviceIntent = Intent(context, com.neko.music.desktoplyric.DesktopLyricService::class.java)
                    serviceIntent.action = com.neko.music.desktoplyric.DesktopLyricService.ACTION_SHOW
                    context.startService(serviceIntent)
                }
            } else {
                // 普通设备需要检查悬浮窗权限
                if (!hasOverlayPermission) {
                    showOverlayPermissionDialog = true
                } else {
                    isDesktopLyricEnabled = true
                    desktopLyricPrefs.edit().putBoolean("desktop_lyric_enabled", true).apply()
                    val serviceIntent = Intent(context, com.neko.music.desktoplyric.DesktopLyricService::class.java)
                    serviceIntent.action = com.neko.music.desktoplyric.DesktopLyricService.ACTION_SHOW
                    context.startService(serviceIntent)
                }
            }
        }
        Unit
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            TopBar(
                onBackClick = onBackClick,
                onMenuClick = {
                    scope.launch {
                        try {
                            val token = tokenManager.getToken()
                            if (token != null) {
                                val playlistApi = PlaylistApi(token, context)
                                val response = playlistApi.getMyPlaylists()
                                if (response.success) {
                                    playlists = response.playlists ?: emptyList()
                                    // 清空之前的封面缓存
                                    playlistFirstMusicCovers = emptyMap()
                                    // 异步加载每个没有封面的歌单的第一首音乐封面
                                    playlists.forEach { playlist ->
                                        if (playlist.coverPath.isNullOrEmpty() && playlist.musicCount > 0) {
                                            scope.launch {
                                                try {
                                                    val musicResponse = playlistApi.getPlaylistMusic(playlist.id)
                                                    if (musicResponse.success && musicResponse.musicList?.isNotEmpty() == true) {
                                                        val firstMusic = musicResponse.musicList[0]
                                                        val coverUrl = "https://music.cnmsb.xin/api/music/cover/${firstMusic.id}"
                                                        playlistFirstMusicCovers = playlistFirstMusicCovers + (playlist.id to coverUrl)
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("PlayerScreen", "加载歌单${playlist.id}封面失败", e)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // 如果加载失败，清空歌单列表
                                    playlists = emptyList()
                                }
                            } else {
                                // 如果未登录，清空歌单列表
                                playlists = emptyList()
                            }
                            showShareDialog = true
                        } catch (e: Exception) {
                            Log.e("PlayerScreen", "加载歌单失败", e)
                            // 即使加载失败，也显示对话框
                            playlists = emptyList()
                            showShareDialog = true
                        }
                    }
                },
                onPlaylistClick = onPlaylistClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 封面视图和歌词视图容器
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // 封面视图
                androidx.compose.animation.AnimatedVisibility(
                    visible = !showLyrics,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CoverImage(
                            music = currentMusic,
                            onClick = { showLyrics = true }
                        )
                    }
                }

                // 歌词视图
                androidx.compose.animation.AnimatedVisibility(
                    visible = showLyrics,
                    modifier = Modifier.fillMaxSize(),
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LyricsView(
                            lyrics = lyrics,
                            currentProgressSeconds = currentProgressSeconds,
                            isLoading = isLoading,
                            onClick = { showLyrics = false },
                            modifier = Modifier.fillMaxSize(),
                            listState = lyricsListState
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 歌曲信息和收藏按钮 - 紧贴在进度条上方
            LyricSongInfoBar(
                music = currentMusic,
                isFavorite = isFavorite,
                onFavoriteClick = {
                    if (isLoggedIn) {
                        playerManager.toggleFavorite()
                    } else {
                        Toast.makeText(context, pleaseLoginFirst, Toast.LENGTH_SHORT).show()
                    }
                },
                showLyrics = showLyrics,
                isLoggedIn = isLoggedIn,
                isDesktopLyricEnabled = isDesktopLyricEnabled,
                onDesktopLyricClick = toggleDesktopLyric
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 进度条
            ProgressSlider(
                progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                currentTime = currentTime,
                totalTime = totalTime,
                isLoading = isLoading,
                onProgressChange = { value ->
                    if (duration > 0) {
                        playerManager.seekTo((value * duration).toLong())
                    }
                    Unit
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            PlaybackControls(
                isPlaying = isPlaying,
                isLoading = isLoading,
                musicFileUrl = musicFileUrl,
                playMode = playMode,
                onPlayPauseClick = {
                    playerManager.togglePlayPause()
                },
                onPreviousClick = { playerManager.previous() },
                onNextClick = { playerManager.next() },
                onPlaylistClick = onPlaylistClick,
                onPlayModeClick = { playerManager.togglePlayMode() }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    // 控制播放模式提示的显示
    LaunchedEffect(playModeChanged) {
        if (playModeChanged > 0) {
            val message = when (playMode) {
                PlayMode.LIST_LOOP -> listLoop
                PlayMode.SINGLE_LOOP -> singleLoop
                PlayMode.SHUFFLE -> shufflePlay
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // 分享对话框
    if (showShareDialog) {
        ShareDialog(
            music = currentMusic,
            onDismiss = { showShareDialog = false },
            onCopyLink = {
                scope.launch {
                    showShareDialog = false
                    try {
                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val shareText = context.getString(R.string.share_music_text, currentMusic.artist, currentMusic.title, currentMusic.id)
                        val clip = android.content.ClipData.newPlainText(context.getString(R.string.music_link), shareText)
                        clipboardManager.setPrimaryClip(clip)
                        Toast.makeText(context, linkCopied, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, copyFailed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDownload = {
                scope.launch {
                    showShareDialog = false
                    try {
                        val downloadHelper = com.neko.music.util.DownloadHelper(context)
                        val result = downloadHelper.downloadMusicWithLyrics(currentMusic)
                        result.fold(
                            onSuccess = { message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { error ->
                                val errorMsg = error.message ?: "Unknown error"
                                Toast.makeText(context, context.getString(R.string.download_failed_format, errorMsg), Toast.LENGTH_SHORT).show()
                            }
                        )
                    } catch (e: Exception) {
                        val errorMsg = e.message ?: "Unknown error"
                        Toast.makeText(context, "下载失败: $errorMsg", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onShareToTwitter = {
                scope.launch {
                    showShareDialog = false
                    try {
                        val shareText = context.getString(R.string.share_music_text, currentMusic.artist, currentMusic.title, currentMusic.id)
                        val encodedText = java.net.URLEncoder.encode(shareText, "UTF-8")

                        // 先尝试使用Twitter应用
                        val twitterIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            data = android.net.Uri.parse("twitter://post?message=$encodedText")
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        // 尝试启动Twitter应用
                        try {
                            context.startActivity(twitterIntent)
                        } catch (e: Exception) {
                            // 如果Twitter应用未安装，使用网页版
                            val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse("https://twitter.com/intent/tweet?text=$encodedText")
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(webIntent)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, shareFailed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSpeedChange = { speed ->
                playerManager.setPlaybackSpeed(speed)
                Toast.makeText(context, context.getString(R.string.playback_speed_format, speed), Toast.LENGTH_SHORT).show()
            },
            currentSpeed = playbackSpeed,
            onSleepTimerChange = { minutes ->
                if (minutes > 0 && notificationPermissionState != null && !notificationPermissionState.status.isGranted) {
                    showNotificationPermissionDialog = true
                } else {
                    playerManager.setSleepTimer(minutes)
                    val message = if (minutes == 0) {
                        sleepTimerCancelled
                    } else {
                        val hours = minutes / 60
                        val mins = minutes % 60
                        if (hours > 0 && mins > 0) {
                            context.getString(R.string.sleep_timer_hours_minutes, hours, mins)
                        } else if (hours > 0) {
                            context.getString(R.string.sleep_timer_hours, hours)
                        } else {
                            context.getString(R.string.sleep_timer_minutes, minutes)
                        }
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            currentSleepTimerMinutes = sleepTimerMinutes,
            playlists = playlists,
            selectedPlaylistId = selectedPlaylistId,
            playlistFirstMusicCovers = playlistFirstMusicCovers,
            onPlaylistSelected = { playlist ->
                selectedPlaylistId = playlist.id
                scope.launch {
                    try {
                        val token = tokenManager.getToken()
                        Log.d("PlayerScreen", "开始添加到歌单: playlistId=${playlist.id}, musicId=${currentMusic.id}, token=$token")

                        if (token != null) {
                            val playlistApi = PlaylistApi(token, context)
                            Log.d("PlayerScreen", "调用API添加到歌单")

                            val response = playlistApi.addMusicToPlaylist(playlist.id, currentMusic.id)
                            Log.d("PlayerScreen", "API响应: success=${response.success}, message=${response.message}")

                            if (response.success) {
                                Toast.makeText(context, context.getString(R.string.added_to_playlist_format, playlist.name), Toast.LENGTH_SHORT).show()
                                showShareDialog = false
                            } else {
                                Toast.makeText(context, response.message, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.e("PlayerScreen", "Token为空")
                            Toast.makeText(context, pleaseLoginFirst, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("PlayerScreen", "添加到歌单失败", e)
                        val errorMsg = e.message ?: "Unknown error"
                        Toast.makeText(context, context.getString(R.string.add_to_playlist_failed, errorMsg), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onCreatePlaylist = {
                if (isLoggedIn) {
                    showCreateDialog = true
                } else {
                    Toast.makeText(context, pleaseLoginFirst, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 创建歌单对话框
    if (showCreateDialog) {
        PlaylistDialog(
            title = stringResource(id = R.string.create_playlist),
            playlistName = dialogPlaylistName,
            onNameChange = { dialogPlaylistName = it },
            onConfirm = {
                scope.launch {
                    try {
                        val token = tokenManager.getToken()
                        if (token != null) {
                            val playlistApi = PlaylistApi(token, context)
                            val response = playlistApi.createPlaylist(dialogPlaylistName)

                            if (response.success) {
                                Toast.makeText(context, createSuccess, Toast.LENGTH_SHORT).show()
                                showCreateDialog = false
                                dialogPlaylistName = ""

                                // 重新加载歌单列表
                                val newResponse = playlistApi.getMyPlaylists()
                                if (newResponse.success) {
                                    val newPlaylists = newResponse.playlists ?: emptyList()
                                    playlists = newPlaylists

                                    // 为新歌单加载第一首音乐封面
                                    val newPlaylistId = response.playlist?.id
                                    if (newPlaylistId != null) {
                                        scope.launch {
                                            try {
                                                val musicResponse = playlistApi.getPlaylistMusic(newPlaylistId)
                                                if (musicResponse.success && musicResponse.musicList?.isNotEmpty() == true) {
                                                    val firstMusic = musicResponse.musicList[0]
                                                    val coverUrl = "https://music.cnmsb.xin/api/music/cover/${firstMusic.id}"
                                                    val newCovers = playlistFirstMusicCovers.toMutableMap()
                                                    newCovers[newPlaylistId] = coverUrl
                                                    playlistFirstMusicCovers = newCovers
                                                }
                                            } catch (e: Exception) {
                                                Log.e("PlayerScreen", "加载歌单封面失败", e)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Toast.makeText(context, response.message, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, pleaseLoginFirst, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("PlayerScreen", "创建歌单失败", e)
                        val errorMsg = e.message ?: "Unknown error"
                        Toast.makeText(context, context.getString(R.string.create_playlist_failed, errorMsg), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = {
                showCreateDialog = false
                dialogPlaylistName = ""
            }
        )
    }

    // 通知权限请求对话框
    if (showNotificationPermissionDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showNotificationPermissionDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isSystemInDarkTheme()) {
                            Color.Black.copy(alpha = 0.7f)
                        } else {
                            Color.Black.copy(alpha = 0.4f)
                        }
                    )
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { showNotificationPermissionDialog = false }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        .padding(24.dp)
                        .width(300.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(id = R.string.notification_permission_title),
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(id = R.string.notification_permission_message),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
                                    .clickable { showNotificationPermissionDialog = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.cancel),
                                    fontSize = 15.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .background(RoseRed, RoundedCornerShape(12.dp))
                                    .clickable {
                                        notificationPermissionState?.launchPermissionRequest()
                                        showNotificationPermissionDialog = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.authorize),
                                    fontSize = 15.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 悬浮窗权限请求对话框
    if (showOverlayPermissionDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isSystemInDarkTheme()) {
                            Color.Black.copy(alpha = 0.7f)
                        } else {
                            Color.Black.copy(alpha = 0.4f)
                        }
                    )
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { showOverlayPermissionDialog = false }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        .padding(24.dp)
                        .width(300.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(id = R.string.desktop_lyric),
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(id = R.string.desktop_lyric_permission_message),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
                                    .clickable { showOverlayPermissionDialog = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.cancel),
                                    fontSize = 15.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .background(RoseRed, RoundedCornerShape(12.dp))
                                    .clickable {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                        showOverlayPermissionDialog = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.authorize),
                                    fontSize = 15.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopBar(
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
    onPlaylistClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(48.dp)
        ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(id = R.string.back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = stringResource(id = R.string.now_playing),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(id = R.string.more),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        @Composable
fun CoverImage(
            music: Music,
            onClick: () -> Unit
        ) {
            val context = LocalContext.current
            val musicApi = remember { MusicApi(context) }
            var coverUrl by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(music.id) {
                coverUrl = musicApi.getMusicCoverUrl(music)
            }

            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RoseRed.copy(alpha = 0.1f))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                if (!coverUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = stringResource(id = R.string.cover),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.music),
                        contentDescription = null,
                        modifier = Modifier.size(60.dp)
                    )
                }
            }
        }

        @Composable
        fun MusicInfoWithFavorite(
                    music: Music,
                    isFavorite: Boolean,
                    onFavoriteClick: () -> Unit,
                    isLoggedIn: Boolean = true
        ) {            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                // 歌名和歌手 - 居中
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        val shouldScroll by remember { derivedStateOf { music.title.length > 15 } }
                        
                        LaunchedEffect(shouldScroll, music.title) {
                            if (shouldScroll) {
                                while (true) {
                                    delay(3000)
                                    scrollState.animateScrollTo(scrollState.maxValue, animationSpec = tween(5000))
                                    delay(3000)
                                    scrollState.animateScrollTo(0, animationSpec = tween(5000))
                                }
                            }
                        }
                        
                        Text(
                            text = music.title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollState, enabled = false)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = music.artist,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // 收藏按钮 - 右侧固定位置
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) stringResource(id = R.string.favorite) else stringResource(id = R.string.unfavorite),
                        tint = if (isFavorite) RoseRed else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        @Composable
fun MusicInfo(
            music: Music,
            isFavorite: Boolean,
            onFavoriteClick: () -> Unit
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = music.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = music.artist,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) stringResource(id = R.string.favorite) else stringResource(id = R.string.unfavorite),
                        tint = if (isFavorite) RoseRed else Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        @Composable
fun LyricSongInfoBar(
            music: Music,
            isFavorite: Boolean,
            onFavoriteClick: () -> Unit,
            showLyrics: Boolean,
            isLoggedIn: Boolean = true,
            isDesktopLyricEnabled: Boolean = false,
            onDesktopLyricClick: () -> Unit = {}
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                if (showLyrics) {
                    // 歌词界面：歌名和歌手在左侧
                    Column(
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        // 歌名 - 可横向滚动
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                        ) {
                            Text(
                                text = music.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // 歌手
                        Text(
                            text = music.artist,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                } else {
                    // 封面界面：歌名和歌手居中
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 歌名 - 自动滚动
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            val scrollState = rememberScrollState()
                            val shouldScroll by remember { derivedStateOf { music.title.length > 15 } }
                            
                            LaunchedEffect(shouldScroll, music.title) {
                                if (shouldScroll) {
                                    while (true) {
                                        delay(3000)
                                        scrollState.animateScrollTo(scrollState.maxValue, animationSpec = tween(5000))
                                        delay(3000)
                                        scrollState.animateScrollTo(0, animationSpec = tween(5000))
                                    }
                                }
                            }
                            
                            Text(
                                text = music.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(scrollState, enabled = false)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = music.artist,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // 右侧按钮组
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 收藏按钮
                    IconButton(
                        onClick = onFavoriteClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) stringResource(id = R.string.favorite) else stringResource(id = R.string.unfavorite),
                            tint = if (isFavorite) RoseRed else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 桌面歌词按钮
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isDesktopLyricEnabled) RoseRed.copy(alpha = 0.15f) else Color.Transparent
                            )
                            .clickable(onClick = onDesktopLyricClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "词",
                            fontSize = 14.sp,
                            fontWeight = if (isDesktopLyricEnabled) FontWeight.Bold else FontWeight.Normal,
                            color = if (isDesktopLyricEnabled) RoseRed else Color.Gray
                        )
                    }
                }
            }
        }

        @Composable
fun LyricsSongInfo(
            music: Music,
            modifier: Modifier = Modifier
        ) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                // 歌名和歌手 - 左侧
                Column(
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    // 歌名 - 可横向滚动
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    ) {
                        Text(
                            text = music.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 歌手
                    Text(
                        text = music.artist,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }

                // 收藏按钮 - 右侧固定位置
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(id = R.string.unfavorite),
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        @Composable
fun LyricsView(
            lyrics: List<LrcLine>,
            currentProgressSeconds: Float,
            isLoading: Boolean,
            onClick: () -> Unit,
            modifier: Modifier = Modifier,
            listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
        ) {
            val currentIndex = remember(lyrics, currentProgressSeconds) {
                lyrics.indexOfLast { it.time <= currentProgressSeconds }
            }

            // 自动滚动到当前歌词，使其居中
            androidx.compose.runtime.LaunchedEffect(currentIndex) {
                android.util.Log.d("LyricsView", "LaunchedEffect: currentIndex=$currentIndex, lyrics.size=${lyrics.size}")
                if (currentIndex >= 0 && lyrics.isNotEmpty()) {
                    try {
                        // 延迟一下，避免频繁触发
                        kotlinx.coroutines.delay(50)
                        // 简单地滚动到当前歌词
                        listState.animateScrollToItem(currentIndex, 0)
                        android.util.Log.d("LyricsView", "Scroll to index=$currentIndex")
                    } catch (e: Exception) {
                        android.util.Log.e("LyricsView", "Scroll error: ${e.message}", e)
                    }
                }
            }

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 32.dp)
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = RoseRed)
                        }
                    }

                    lyrics.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(id = R.string.no_lyrics),
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    else -> {
                        androidx.compose.foundation.lazy.LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 添加顶部占位，让第一行歌词也能居中
                            item {
                                Spacer(modifier = Modifier.height(250.dp))
                            }

                            items(lyrics.size) { index ->
                                val line = lyrics[index]
                                val isCurrentLine = index == currentIndex

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    // 原文歌词
                                    Text(
                                        text = line.text,
                                        fontSize = if (isCurrentLine) 18.sp else 14.sp,
                                        fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrentLine) RoseRed else Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                    
                                    // 翻译歌词（如果存在）
                                    if (line.translation.isNotEmpty()) {
                                        Text(
                                            text = line.translation,
                                            fontSize = if (isCurrentLine) 14.sp else 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = if (isCurrentLine) RoseRed.copy(alpha = 0.9f) else Color.Gray.copy(alpha = 0.7f),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }

                            // 添加底部占位，让最后一行歌词也能居中
                            item {
                                Spacer(modifier = Modifier.height(300.dp))
                            }
                        }
                    }
                }
            }
        }

        /**
         * LRC 歌词行数据类
         */
        @Composable
fun ProgressSlider(
            progress: Float,
            currentTime: String,
            totalTime: String,
            isLoading: Boolean,
            onProgressChange: (Float) -> Unit
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                if (!isLoading) {
                    Slider(
                        value = progress,
                        onValueChange = onProgressChange,
                        colors = SliderDefaults.colors(
                            activeTrackColor = RoseRed,
                            inactiveTrackColor = Color(0xFFE0E0E0),
                            thumbColor = RoseRed
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = currentTime,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = totalTime,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

@Composable
    fun PlaybackControls(
            isPlaying: Boolean,
            isLoading: Boolean,
            musicFileUrl: String?,
            playMode: PlayMode,
            onPlayPauseClick: () -> Unit,
            onPreviousClick: () -> Unit,
            onNextClick: () -> Unit,
            onPlaylistClick: () -> Unit,
            onPlayModeClick: () -> Unit
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：播放模式、上一首
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 播放模式按钮
                    IconButton(
                        onClick = onPlayModeClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        val iconRes = when (playMode) {
                            PlayMode.LIST_LOOP -> R.drawable.list_loop
                            PlayMode.SINGLE_LOOP -> R.drawable.single_loop
                            PlayMode.SHUFFLE -> R.drawable.shuffle
                        }
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(iconRes),
                            contentDescription = "Play Mode",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 上一首按钮
                    IconButton(
                        onClick = onPreviousClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.previous_song),
                            contentDescription = "Previous",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 中间：播放/暂停按钮
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(RoseRed, CircleShape)
                        .clickable(
                            enabled = !isLoading && musicFileUrl != null,
                            onClick = onPlayPauseClick,
                            indication = null,
                            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource()
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        musicFileUrl == null -> {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Loading",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        else -> {
                            Icon(
                                painter = painterResource(
                                    id = if (isPlaying) R.drawable.pause else R.drawable.play
                                ),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                // 右侧：下一首、播放列表
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 下一首按钮
                    IconButton(
                        onClick = onNextClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.next_song),
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 播放列表按钮
                    IconButton(
                        onClick = onPlaylistClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.playlist),
                            contentDescription = "Playlist",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }


@Composable
fun ShareDialog(
    music: Music,
    onDismiss: () -> Unit,
    onCopyLink: () -> Unit,
    onDownload: () -> Unit,
    onShareToTwitter: () -> Unit,
    onSpeedChange: (Float) -> Unit = {},
    currentSpeed: Float = 1.0f,
    onSleepTimerChange: (Int) -> Unit = {},
    currentSleepTimerMinutes: Int = 0,
    playlists: List<PlaylistInfo> = emptyList(),
    selectedPlaylistId: Int? = null,
    onPlaylistSelected: (PlaylistInfo) -> Unit = {},
    playlistFirstMusicCovers: Map<Int, String> = emptyMap(),
    onCreatePlaylist: () -> Unit = {}
) {
    var showCustomSleepTimerDialog by remember { mutableStateOf(false) }
    var customHours by remember { mutableStateOf(0) }
    var customMinutes by remember { mutableStateOf(0) }

    // 预加载额外的字符串资源
    val customLabel = stringResource(id = R.string.custom)
    val closeLabel = stringResource(id = R.string.close)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            // 底部弹出面板
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))

                        // 横向滚动的分享列表
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            item {
                                ShareGridItem(
                                    iconRes = R.drawable.twitter,
                                    label = stringResource(id = R.string.share_to_twitter),
                                    color = Color(0xFF1DA1F2),
                                    onClick = onShareToTwitter
                                )
                            }
                            item {
                                ShareGridItem(
                                    iconRes = R.drawable.copy_link,
                                    label = stringResource(id = R.string.copy_link),
                                    color = RoseRed,
                                    onClick = onCopyLink
                                )
                            }
                            item {
                                ShareGridItem(
                                    iconRes = R.drawable.download,
                                    label = stringResource(id = R.string.download),
                                    color = Color(0xFF6B5B95),
                                    onClick = onDownload
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 添加到歌单选择器
                        if (playlists.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.add_to_playlist),
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(
                                        count = playlists.size,
                                        key = { index -> playlists[index].id }
                                    ) { index ->
                                        val playlist = playlists[index]
                                        PlaylistChip(
                                            playlist = playlist,
                                            isSelected = selectedPlaylistId == playlist.id,
                                            firstMusicCover = playlistFirstMusicCovers[playlist.id],
                                            onClick = { onPlaylistSelected(playlist) }
                                        )
                                    }
                                    // 新建歌单按钮
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .width(70.dp)
                                                .height(56.dp)
                                                .border(
                                                    width = 2.dp,
                                                    color = RoseRed,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .background(
                                                    color = RoseRed.copy(alpha = 0.1f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable(onClick = onCreatePlaylist),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = stringResource(id = R.string.create_new),
                                                    tint = RoseRed,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Text(
                                                    text = stringResource(id = R.string.create_new),
                                                    fontSize = 10.sp,
                                                    color = RoseRed,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // 倍速选择器
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.playback_speed),
                                fontSize = 14.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    count = 6,
                                    key = { index -> index }
                                ) { index ->
                                    val speed = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)[index]
                                    SpeedChip(
                                        speed = speed,
                                        isSelected = speed == currentSpeed,
                                        onClick = { onSpeedChange(speed) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 定时关闭选择器
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.sleep_timer),
                                fontSize = 14.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    count = 7,
                                    key = { index -> index }
                                ) { index ->
                                    val presetMinutes = listOf(0, 10, 20, 30, 45, 60)
                                    if (index == 6) {
                                        val isCustomSelected = currentSleepTimerMinutes > 0 && currentSleepTimerMinutes !in presetMinutes
                                        SleepTimerChip(
                                            minutes = -1,
                                            isSelected = isCustomSelected,
                                            customMinutes = if (isCustomSelected) currentSleepTimerMinutes else null,
                                            onClick = { showCustomSleepTimerDialog = true },
                                            customLabel = customLabel,
                                            closeLabel = closeLabel
                                        )
                                    } else {
                                        val minutes = presetMinutes[index]
                                        SleepTimerChip(
                                            minutes = minutes,
                                            isSelected = minutes == currentSleepTimerMinutes,
                                            onClick = { onSleepTimerChange(minutes) },
                                            customLabel = customLabel,
                                            closeLabel = closeLabel
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 分割线
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(Color(0xFFE8E8E8))
                        )

                        // 取消按钮
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(id = R.string.cancel),
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 自定义时间设置对话框
        if (showCustomSleepTimerDialog) {
            CustomSleepTimerDialog(
                initialMinutes = currentSleepTimerMinutes,
                onDismiss = { showCustomSleepTimerDialog = false },
                onConfirm = { hours, minutes ->
                    val totalMinutes = hours * 60 + minutes
                    onSleepTimerChange(totalMinutes)
                    showCustomSleepTimerDialog = false
                }
            )
        }
    }
}

@Composable
fun ShareGridItem(
    icon: String? = null,
    iconRes: Int? = null,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
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
                .size(56.dp)
                .clip(CircleShape)
                .background(color)
                .shadow(
                    elevation = 4.dp,
                    spotColor = color.copy(alpha = 0.3f),
                    ambientColor = color.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            } else if (icon != null) {
                Text(
                    text = icon,
                    fontSize = 28.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.Gray.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SpeedChip(
    speed: Float,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) RoseRed else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .height(36.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${speed}x",
            fontSize = 14.sp,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun SleepTimerChip(
    minutes: Int,
    isSelected: Boolean,
    customMinutes: Int? = null,
    onClick: () -> Unit,
    customLabel: String = "",
    closeLabel: String = ""
) {
    val backgroundColor = if (isSelected) RoseRed else Color(0xFFF5F5F5)
    val textColor = if (isSelected) Color.White else Color.Gray
    val label = when {
        minutes == -1 && customMinutes != null -> {
            val hours = customMinutes / 60
            val mins = customMinutes % 60
            if (hours > 0 && mins > 0) {
                "${hours}h${mins}m"
            } else if (hours > 0) {
                "${hours}h"
            } else {
                "${mins}m"
            }
        }
        minutes == -1 -> customLabel
        minutes == 0 -> closeLabel
        else -> "${minutes}分钟"
    }

    Box(
        modifier = Modifier
            .height(36.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun CustomSleepTimerDialog(
    initialMinutes: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var hours by remember { mutableStateOf(initialMinutes / 60) }
    var minutes by remember { mutableStateOf(initialMinutes % 60) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isSystemInDarkTheme()) {
                        Color.Black.copy(alpha = 0.7f)
                    } else {
                        Color.Black.copy(alpha = 0.4f)
                    }
                )
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.custom_sleep_timer),
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 小时选择
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(id = R.string.hours),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { if (hours > 0) hours-- },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.previous_song),
                                            contentDescription = stringResource(id = R.string.decrease),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Text(
                                        text = "$hours",
                                        fontSize = 32.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )

                                    IconButton(
                                        onClick = { if (hours < 23) hours++ },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.next_song),
                                            contentDescription = stringResource(id = R.string.increase),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = ":",
                                fontSize = 32.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )

                            // 分钟选择
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(id = R.string.minutes),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { if (minutes > 0) minutes-- },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.previous_song),
                                            contentDescription = stringResource(id = R.string.decrease),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Text(
                                        text = String.format("%02d", minutes),
                                        fontSize = 32.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )

                                    IconButton(
                                        onClick = { if (minutes < 59) minutes++ },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.next_song),
                                            contentDescription = stringResource(id = R.string.increase),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .clickable { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.cancel),
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(RoseRed, RoundedCornerShape(12.dp))
                                    .clickable { onConfirm(hours, minutes) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.confirm),
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistChip(
    playlist: PlaylistInfo,
    isSelected: Boolean,
    firstMusicCover: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(70.dp)
            .height(56.dp)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) RoseRed else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = if (isSelected) RoseRed.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 封面
            val coverUrl = if (!playlist.coverPath.isNullOrEmpty()) {
                "https://music.cnmsb.xin${playlist.coverPath}"
            } else {
                firstMusicCover
            }
            AsyncImage(
                model = coverUrl,
                contentDescription = stringResource(id = R.string.cover),
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            
            // 歌单名称
            Text(
                text = playlist.name,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            
            // 歌曲数量
            Text(
                text = "${playlist.musicCount}首",
                fontSize = 8.sp,
                color = Color.Gray
            )
        }
    }
}
