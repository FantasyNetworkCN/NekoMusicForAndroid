package com.neko.music.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.neko.music.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LatestScreen(
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val musicApi = remember { MusicApi(context) }
    val playerManager = remember { MusicPlayerManager.getInstance(context) }
    val listState = rememberLazyListState()
    val isDarkMode = isSystemInDarkTheme()
    
    var musicList by remember { mutableStateOf<List<Music>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    
    fun loadData() {
        loading = true
        scope.launch {
            try {
                Log.d("LatestScreen", "开始加载最新音乐...")
                val result = musicApi.getLatest(300)
                result.onSuccess { list ->
                    musicList = list
                    Log.d("LatestScreen", "最新音乐加载成功: ${list.size}首")
                }.onFailure { error ->
                    Log.e("LatestScreen", "最新音乐加载失败: ${error.message}")
                    loadError = true
                }
            } catch (e: Exception) {
                Log.e("LatestScreen", "最新音乐异常: ${e.message}", e)
                loadError = true
            } finally {
                loading = false
            }
        }
    }
    
    fun refreshData() {
        refreshing = true
        scope.launch {
            try {
                val result = musicApi.getLatest(300)
                result.onSuccess { list ->
                    musicList = list
                    loadError = false
                    Log.d("LatestScreen", "刷新成功: ${list.size}首")
                }.onFailure { error ->
                    Log.e("LatestScreen", "刷新失败: ${error.message}")
                    loadError = true
                }
            } catch (e: Exception) {
                Log.e("LatestScreen", "刷新异常: ${e.message}", e)
                loadError = true
            } finally {
                refreshing = false
            }
        }
    }
    
    LaunchedEffect(Unit) {
        loadData()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.latest_music),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color.White.copy(alpha = 0.95f) else RoseRed
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                            tint = if (isDarkMode) Color.White.copy(alpha = 0.9f) else RoseRed
                        )
                    }
                },
                actions = {
                    if (musicList.isNotEmpty()) {
                        TextButton(
                            onClick = { 
                                Log.d("LatestScreen", "播放全部: ${musicList.size}首")
                                scope.launch {
                                    try {
                                        // 播放第一首，其他歌曲会在播放时自动添加到播放列表
                                        val firstMusic = musicList[0]
                                        val url = musicApi.getMusicFileUrl(firstMusic)
                                        val fullCoverUrl = "https://music.cnmsb.xin/api/music/cover/${firstMusic.id}"
                                        playerManager.playMusic(
                                            url,
                                            firstMusic.id,
                                            firstMusic.title,
                                            firstMusic.artist,
                                            firstMusic.coverFilePath ?: "",
                                            fullCoverUrl
                                        )
                                    } catch (e: Exception) {
                                        Log.e("LatestScreen", "播放全部失败", e)
                                    }
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(id = R.string.play_all),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDarkMode) Color.White.copy(alpha = 0.9f) else RoseRed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDarkMode) DeepBlue else Color.Transparent
                )
            )
        },
        containerColor = if (isDarkMode) DeepBlue else Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                loading && musicList.isEmpty() -> {
                    LatestLoadingState()
                }
                loadError && musicList.isEmpty() -> {
                    LatestErrorState(
                        onRetry = { loadData() }
                    )
                }
                musicList.isEmpty() -> {
                    LatestEmptyState()
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = if (isDarkMode) {
                                        listOf(
                                            DeepBlue.copy(alpha = 0.3f),
                                            DeepBlue.copy(alpha = 0.2f),
                                            DeepBlue.copy(alpha = 0.1f)
                                        )
                                    } else {
                                        listOf(
                                            SakuraPink.copy(alpha = 0.12f),
                                            SkyBlue.copy(alpha = 0.08f),
                                            Lilac.copy(alpha = 0.05f)
                                        )
                                    }
                                )
                            ),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 160.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(
                            items = musicList,
                            key = { _, music -> music.id }
                        ) { index, music ->
                            LatestItem(
                                music = music,
                                index = index,
                                onClick = {
                                    Log.d("LatestScreen", "点击歌曲: ${music.title}")
                                    scope.launch {
                                        try {
                                            // 播放当前点击的歌曲
                                            val url = musicApi.getMusicFileUrl(music)
                                            val fullCoverUrl = "https://music.cnmsb.xin/api/music/cover/${music.id}"
                                            playerManager.playMusic(
                                                url,
                                                music.id,
                                                music.title,
                                                music.artist,
                                                music.coverFilePath ?: "",
                                                fullCoverUrl
                                            )
                                        } catch (e: Exception) {
                                            Log.e("LatestScreen", "播放失败", e)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LatestLoadingState() {
    val isDarkMode = isSystemInDarkTheme()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDarkMode) {
                        listOf(
                            DeepBlue.copy(alpha = 0.3f),
                            DeepBlue.copy(alpha = 0.2f),
                            DeepBlue.copy(alpha = 0.1f)
                        )
                    } else {
                        listOf(
                            SakuraPink.copy(alpha = 0.12f),
                            SkyBlue.copy(alpha = 0.08f),
                            Lilac.copy(alpha = 0.05f)
                        )
                    }
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = RoseRed,
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.loading_latest_music),
            fontSize = 14.sp,
            color = if (isDarkMode) Color.White.copy(alpha = 0.7f) else RoseRed.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun LatestErrorState(
    onRetry: () -> Unit
) {
    val isDarkMode = isSystemInDarkTheme()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDarkMode) {
                        listOf(
                            DeepBlue.copy(alpha = 0.3f),
                            DeepBlue.copy(alpha = 0.2f),
                            DeepBlue.copy(alpha = 0.1f)
                        )
                    } else {
                        listOf(
                            SakuraPink.copy(alpha = 0.12f),
                            SkyBlue.copy(alpha = 0.08f),
                            Lilac.copy(alpha = 0.05f)
                        )
                    }
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(id = R.string.load_failed),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDarkMode) Color.White.copy(alpha = 0.95f) else RoseRed
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.network_error),
            fontSize = 14.sp,
            color = if (isDarkMode) Color.White.copy(alpha = 0.6f) else RoseRed.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = RoseRed
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text = stringResource(id = R.string.retry),
                fontSize = 14.sp,
                color = Color.White
            )
        }
    }
}

@Composable
fun LatestEmptyState() {
    val isDarkMode = isSystemInDarkTheme()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDarkMode) {
                        listOf(
                            DeepBlue.copy(alpha = 0.3f),
                            DeepBlue.copy(alpha = 0.2f),
                            DeepBlue.copy(alpha = 0.1f)
                        )
                    } else {
                        listOf(
                            SakuraPink.copy(alpha = 0.12f),
                            SkyBlue.copy(alpha = 0.08f),
                            Lilac.copy(alpha = 0.05f)
                        )
                    }
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(id = R.string.no_latest_music),
            fontSize = 16.sp,
            color = if (isDarkMode) Color.White.copy(alpha = 0.6f) else RoseRed.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun LatestItem(
    music: Music,
    index: Int,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val musicApi = remember { MusicApi(context) }
    val isDarkMode = isSystemInDarkTheme()
    var coverUrl by remember { mutableStateOf<String?>(null) }
    var isLoaded by remember { mutableStateOf(false) }
    
    LaunchedEffect(music.id) {
        coverUrl = musicApi.getMusicCoverUrl(music)
        isLoaded = true
    }
    
    val backgroundColor = Brush.horizontalGradient(
        colors = if (isDarkMode) {
            listOf(
                RoseRed.copy(alpha = 0.12f),
                Color.Transparent
            )
        } else {
            listOf(
                RoseRed.copy(alpha = 0.08f),
                Color.Transparent
            )
        }
    )
    
    // 格式化上传时间
    val uploadTime = remember(music.createdAt) {
        if (music.createdAt != null) {
            try {
                val timestamp = music.createdAt.toLong()
                formatUploadTime(context, timestamp)
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .shadow(
                elevation = 1.dp,
                spotColor = RoseRed.copy(alpha = 0.3f),
                ambientColor = RoseRed.copy(alpha = 0.1f)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = isLoaded,
            enter = fadeIn(
                animationSpec = tween(300, delayMillis = index * 20)
            ) + slideInHorizontally(
                animationSpec = tween(300, delayMillis = index * 20),
                initialOffsetX = { -50 }
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    RoseRed.copy(alpha = 0.2f),
                                    RoseRed.copy(alpha = 0.05f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = music.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = music.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkMode) Color.White.copy(alpha = 0.95f) else RoseRed.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = music.artist,
                        fontSize = 12.sp,
                        color = if (isDarkMode) Color.White.copy(alpha = 0.6f) else RoseRed.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                if (uploadTime.isNotEmpty()) {
                    Text(
                        text = uploadTime,
                        fontSize = 11.sp,
                        color = if (isDarkMode) Color.White.copy(alpha = 0.5f) else RoseRed.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

fun formatUploadTime(context: android.content.Context, timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60 * 1000 -> context.getString(R.string.just_now)
        diff < 60 * 60 * 1000 -> context.getString(R.string.minutes_ago_format, diff / (60 * 1000))
        diff < 24 * 60 * 60 * 1000 -> context.getString(R.string.hours_ago_format, diff / (60 * 60 * 1000))
        diff < 30 * 24 * 60 * 60 * 1000 -> context.getString(R.string.days_ago_format, diff / (24 * 60 * 60 * 1000))
        else -> {
            val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}