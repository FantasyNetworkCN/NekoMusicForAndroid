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
import androidx.compose.foundation.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.list.LatestLiquidBarState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun LatestScreen(
    liquidBarState: LatestLiquidBarState,
    onBackClick: () -> Unit = {},
    onNavigateToPlayer: (Music) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val musicApi = remember { MusicApi(context) }
    val playerManager = remember { MusicPlayerManager.getInstance(context) }
    val listState = rememberLazyListState()
    val scheme = MaterialTheme.colorScheme

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

    val density = LocalDensity.current
    val topBarInsetDp = remember(liquidBarState.barInsetPx, density) {
        if (liquidBarState.barInsetPx > 0) with(density) { liquidBarState.barInsetPx.toDp() } else 88.dp
    }

    // 仅底图进 pageBackdrop；列表在兄弟层可 Kyant 液态。顶栏在 MainActivity 与 NavHost 同级，采主 liquidBackdrop。
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop)
        ) {
            Image(
                painter = painterResource(id = R.drawable.playlist_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    loading && musicList.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = topBarInsetDp + 20.dp)
                        ) {
                            LatestLoadingState()
                        }
                    }
                    loadError && musicList.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = topBarInsetDp + 20.dp)
                        ) {
                            LatestErrorState(onRetry = { loadData() })
                        }
                    }
                    musicList.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = topBarInsetDp + 20.dp)
                        ) {
                            LatestEmptyState()
                        }
                    }
                    else -> {
                        val pullRefreshState =
                            rememberPullRefreshState(refreshing, onRefresh = { refreshData() })

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pullRefresh(pullRefreshState)
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = topBarInsetDp + 48.dp,
                                    bottom = 240.dp
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
                                                    val fullCoverUrl =
                                                        UrlConfig.getMusicCoverUrl(music.id)
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
                                    .padding(top = topBarInsetDp + 36.dp),
                                backgroundColor = scheme.surface,
                                contentColor = scheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LatestLoadingState(
    messageColor: Color? = null
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize(),
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
            color = messageColor ?: scheme.onSurfaceVariant
        )
    }
}

@Composable
fun LatestErrorState(
    onRetry: () -> Unit,
    titleColor: Color? = null,
    messageColor: Color? = null
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(id = R.string.load_failed),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = titleColor ?: scheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.network_error),
            fontSize = 14.sp,
            color = messageColor ?: scheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        val retryGlass = LiquidGlassDefaults.rankingRetryButton
        GlassSurface(
            modifier = Modifier
                .clickable { onRetry() }
                .padding(horizontal = 24.dp, vertical = 10.dp),
            shape = RoundedCornerShape(20.dp),
            backgroundAlpha = retryGlass.tint.background(isDark),
            borderAlpha = retryGlass.tint.border(isDark),
            highlightAlpha = retryGlass.tint.highlight(isDark),
            liquidBlur = retryGlass.liquid.blur,
            liquidLensHeight = retryGlass.liquid.lensHeight,
            liquidLensAmount = retryGlass.liquid.lensAmount,
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
    Column(
        modifier = Modifier.fillMaxSize(),
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
    onClick: () -> Unit,
    titleColor: Color? = null,
    subtitleColor: Color? = null,
    metaColor: Color? = null
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

    val resolvedTitleColor = titleColor ?: scheme.onSurface
    val resolvedSubtitleColor = subtitleColor ?: scheme.onSurfaceVariant
    val resolvedMetaColor = metaColor ?: scheme.onSurfaceVariant.copy(alpha = 0.85f)

    val latestGlass = LiquidGlassDefaults.latestListItem
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        backgroundAlpha = latestGlass.tint.background(isDark),
        borderAlpha = latestGlass.tint.border(isDark),
        highlightAlpha = latestGlass.tint.highlight(isDark),
        liquidBlur = latestGlass.liquid.blur,
        liquidLensHeight = latestGlass.liquid.lensHeight,
        liquidLensAmount = latestGlass.liquid.lensAmount,
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
                            color = resolvedTitleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = music.artist,
                            fontSize = 12.sp,
                            color = resolvedSubtitleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (uploadTime.isNotEmpty()) {
                        Text(
                            text = uploadTime,
                            fontSize = 11.sp,
                            color = resolvedMetaColor,
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