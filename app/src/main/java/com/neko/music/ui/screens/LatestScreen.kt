package com.neko.music.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.neko.music.R
import com.neko.music.util.UrlConfig
import com.neko.music.data.api.MusicApi
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.list.LatestLiquidBarState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun LatestScreen(
    liquidBarState: LatestLiquidBarState,
    topBarInsetDp: Dp,
    onBackClick: () -> Unit = {},
    onNavigateToPlayer: (Music) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val musicApi = remember { MusicApi(context) }
    val playerManager = remember { MusicPlayerManager.getInstance(context) }
    val listState = rememberLazyListState()
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    
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

    SideEffect {
        liquidBarState.musicList = musicList
        liquidBarState.loading = loading
        liquidBarState.loadError = loadError
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        when {
            loading && musicList.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topBarInsetDp)
                ) {
                    LatestLoadingState()
                }
            }
            loadError && musicList.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topBarInsetDp)
                ) {
                    LatestErrorState(onRetry = { loadData() })
                }
            }
            musicList.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topBarInsetDp)
                ) {
                    LatestEmptyState()
                }
            }
            else -> {
                val pullRefreshState = rememberPullRefreshState(refreshing, onRefresh = { refreshData() })

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = if (isDark) {
                                        listOf(
                                            scheme.background,
                                            scheme.surface.copy(alpha = 0.5f),
                                            scheme.surface.copy(alpha = 0.35f)
                                        )
                                    } else {
                                        listOf(
                                            scheme.background,
                                            scheme.surfaceVariant.copy(alpha = 0.35f),
                                            scheme.surface.copy(alpha = 0.55f)
                                        )
                                    }
                                )
                            ),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = topBarInsetDp + 28.dp,
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
                                            val url = musicApi.getMusicFileUrl(music)
                                            val fullCoverUrl = UrlConfig.getMusicCoverUrl(music.id)
                                            playerManager.playMusic(
                                                url,
                                                music.id,
                                                music.title,
                                                music.artist,
                                                music.coverFilePath ?: "",
                                                fullCoverUrl
                                            )
                                            onNavigateToPlayer(music)
                                        } catch (e: Exception) {
                                            Log.e("LatestScreen", "播放失败", e)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    PullRefreshIndicator(
                        refreshing = refreshing,
                        state = pullRefreshState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topBarInsetDp + 16.dp),
                        backgroundColor = scheme.surface,
                        contentColor = scheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun LatestLoadingState() {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            scheme.background,
                            scheme.surface.copy(alpha = 0.5f),
                            scheme.surface.copy(alpha = 0.35f)
                        )
                    } else {
                        listOf(
                            scheme.background,
                            scheme.surfaceVariant.copy(alpha = 0.35f),
                            scheme.surface.copy(alpha = 0.55f)
                        )
                    }
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = scheme.primary,
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.loading_latest_music),
            fontSize = 14.sp,
            color = scheme.onSurfaceVariant
        )
    }
}

@Composable
fun LatestErrorState(
    onRetry: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            scheme.background,
                            scheme.surface.copy(alpha = 0.5f),
                            scheme.surface.copy(alpha = 0.35f)
                        )
                    } else {
                        listOf(
                            scheme.background,
                            scheme.surfaceVariant.copy(alpha = 0.35f),
                            scheme.surface.copy(alpha = 0.55f)
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
            color = scheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.network_error),
            fontSize = 14.sp,
            color = scheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        GlassSurface(
            modifier = Modifier
                .clickable { onRetry() }
                .padding(horizontal = 24.dp, vertical = 10.dp),
            shape = RoundedCornerShape(20.dp),
            backgroundAlpha = if (isDark) 0.28f else 0.12f,
            borderAlpha = if (isDark) 0.18f else 0.14f,
            liquidBlur = 8.dp,
            liquidLensHeight = 18.dp,
            liquidLensAmount = 26.dp,
            borderColor = if (isDark) Color.White else scheme.outline
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.retry),
                    fontSize = 14.sp,
                    color = scheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun LatestEmptyState() {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            scheme.background,
                            scheme.surface.copy(alpha = 0.5f),
                            scheme.surface.copy(alpha = 0.35f)
                        )
                    } else {
                        listOf(
                            scheme.background,
                            scheme.surfaceVariant.copy(alpha = 0.35f),
                            scheme.surface.copy(alpha = 0.55f)
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
            color = scheme.onSurfaceVariant
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
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    var coverUrl by remember { mutableStateOf<String?>(null) }
    var isLoaded by remember { mutableStateOf(false) }
    
    LaunchedEffect(music.id) {
        coverUrl = musicApi.getMusicCoverUrl(music)
        isLoaded = true
    }
    
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

    val titleColor = scheme.onSurface
    val subtitleColor = scheme.onSurfaceVariant
    val metaColor = scheme.onSurfaceVariant.copy(alpha = 0.85f)

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        backgroundAlpha = if (isDark) 0.22f else 0.12f,
        borderAlpha = if (isDark) 0.14f else 0.12f,
        highlightAlpha = if (isDark) 0.08f else 0.06f,
        liquidBlur = 6.dp,
        liquidLensHeight = 14.dp,
        liquidLensAmount = 22.dp,
        borderColor = if (isDark) Color.White else scheme.outline
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                                        scheme.surfaceVariant.copy(alpha = 0.5f),
                                        scheme.surface.copy(alpha = 0.15f)
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
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = music.artist,
                            fontSize = 12.sp,
                            color = subtitleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (uploadTime.isNotEmpty()) {
                        Text(
                            text = uploadTime,
                            fontSize = 11.sp,
                            color = metaColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
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