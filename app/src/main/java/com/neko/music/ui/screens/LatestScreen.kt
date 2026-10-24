package com.neko.music.ui.screens

import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.neko.music.ui.components.AppPageBackgroundImage
import com.neko.music.ui.components.EditorialCollectionHero
import com.neko.music.ui.components.EditorialCollectionLoading
import com.neko.music.ui.components.EditorialCollectionStateCard
import com.neko.music.ui.components.EditorialPlayAllButton
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.LrcBadge
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.list.LatestLiquidBarState
import com.neko.music.ui.theme.LightRose
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.util.UrlConfig
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun LatestScreen(
    liquidBarState: LatestLiquidBarState,
    onBackClick: () -> Unit = {},
    onNavigateToPlayer: (Music) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val musicApi = remember { MusicApi(context) }
    val playerManager = remember { MusicPlayerManager.getInstance(context) }
    val listState = rememberLazyListState()
    val scheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()

    var musicList by remember { mutableStateOf<List<Music>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }

    fun loadData() {
        loading = true
        scope.launch {
            try {
                musicApi.getLatest(300)
                    .onSuccess { list ->
                        musicList = list
                        loadError = false
                    }
                    .onFailure {
                        loadError = true
                    }
            } catch (e: Exception) {
                Log.e("LatestScreen", "最新音乐加载异常", e)
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
                musicApi.getLatest(300)
                    .onSuccess { list ->
                        musicList = list
                        loadError = false
                    }
                    .onFailure {
                        loadError = true
                    }
            } catch (e: Exception) {
                Log.e("LatestScreen", "最新音乐刷新异常", e)
                loadError = true
            } finally {
                refreshing = false
            }
        }
    }

    fun playMusic(music: Music) {
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
                    fullCoverUrl,
                )
                onNavigateToPlayer(music)
            } catch (e: Exception) {
                Log.e("LatestScreen", "播放失败", e)
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
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    val accent = if (isDark) LightRose else RoseRed
    val newestMusic = musicList.firstOrNull()
    val newestAge = remember(newestMusic?.createdAt) {
        newestMusic?.createdAt
            ?.toLongOrNull()
            ?.let { formatUploadTime(context, it) }
            .orEmpty()
    }
    val heroCoverUrl = remember(newestMusic?.id, newestMusic?.coverFilePath) {
        newestMusic?.latestCoverUrl().orEmpty()
    }
    val countText = stringResource(id = R.string.songs_count_short, musicList.size)
    val heroMeta = listOf(countText, newestAge)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop),
        ) {
            AppPageBackgroundImage(modifier = Modifier.fillMaxSize())
            PlaylistPageDarkTintOverlay(enabled = isDark)
        }

        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            when {
                loading && musicList.isEmpty() -> {
                    EditorialCollectionLoading(
                        isDark = isDark,
                        message = stringResource(id = R.string.loading_latest_music),
                        accent = accent,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topBarInsetDp),
                    )
                }
                loadError && musicList.isEmpty() -> {
                    EditorialCollectionStateCard(
                        icon = Icons.Default.Info,
                        title = stringResource(id = R.string.load_failed),
                        message = stringResource(id = R.string.network_error),
                        actionLabel = stringResource(id = R.string.retry),
                        onAction = { loadData() },
                        accent = accent,
                        isDark = isDark,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topBarInsetDp),
                    )
                }
                musicList.isEmpty() -> {
                    EditorialCollectionStateCard(
                        icon = Icons.Default.Info,
                        title = stringResource(id = R.string.no_latest_music),
                        message = stringResource(id = R.string.latest_empty_hint),
                        actionLabel = stringResource(id = R.string.refresh),
                        onAction = { loadData() },
                        accent = accent,
                        isDark = isDark,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topBarInsetDp),
                    )
                }
                else -> {
                    val pullRefreshState = rememberPullRefreshState(
                        refreshing = refreshing,
                        onRefresh = { refreshData() },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pullRefresh(pullRefreshState),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = topBarInsetDp + 6.dp,
                                bottom = 180.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item(key = "latest-hero") {
                                EditorialCollectionHero(
                                    eyebrow = stringResource(id = R.string.latest_hero_eyebrow),
                                    title = stringResource(id = R.string.latest_hero_title),
                                    description = stringResource(id = R.string.latest_hero_desc),
                                    coverUrl = heroCoverUrl,
                                    meta = heroMeta,
                                    badge = stringResource(id = R.string.latest_new_badge),
                                    accent = accent,
                                    isDark = isDark,
                                )
                            }
                            item(key = "latest-play-all") {
                                EditorialPlayAllButton(
                                    label = stringResource(id = R.string.play_all),
                                    countText = countText,
                                    accent = accent,
                                    isDark = isDark,
                                    onClick = {
                                        if (musicList.isNotEmpty()) {
                                            playerManager.playMusicList(musicList)
                                        }
                                    },
                                )
                            }
                            itemsIndexed(
                                items = musicList,
                                key = { _, music -> music.id },
                            ) { index, music ->
                                LatestTrackItem(
                                    music = music,
                                    index = index,
                                    isDark = isDark,
                                    accent = accent,
                                    onClick = { playMusic(music) },
                                )
                            }
                        }

                        PullRefreshIndicator(
                            refreshing = refreshing,
                            state = pullRefreshState,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = topBarInsetDp + 4.dp),
                            backgroundColor = scheme.surface,
                            contentColor = scheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LatestTrackItem(
    music: Music,
    index: Int,
    isDark: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val glass = LiquidGlassDefaults.latestListItem
    val titleColor = if (isDark) Color(0xFFF1F1F5) else Color.Black
    val secondaryColor = if (isDark) Color.White.copy(alpha = 0.58f) else Color.Black.copy(alpha = 0.52f)
    val metaColor = if (isDark) Color.White.copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.38f)
    val coverUrl = remember(music.id, music.coverFilePath) { music.latestCoverUrl() }
    val uploadTime = remember(music.createdAt) {
        music.createdAt
            ?.toLongOrNull()
            ?.let { formatUploadTime(context, it) }
            .orEmpty()
    }
    val subtitle = remember(music.artist, music.album) {
        listOf(music.artist, music.album)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }
    val interactionSource = remember(music.id) { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "latest-track-press-${music.id}",
    )

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(14.dp),
        backgroundAlpha = glass.tint.background(isDark),
        borderAlpha = glass.tint.border(isDark),
        highlightAlpha = glass.tint.highlight(isDark),
        borderColor = if (isDark) Color.White else scheme.outline,
        liquidBlur = glass.liquid.blur,
        liquidLensHeight = glass.liquid.lensHeight,
        liquidLensAmount = glass.liquid.lensAmount,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isDark) Color(0xFF353558).copy(alpha = 0.6f)
                        else Color(0xFFE0E0E0),
                    ),
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = stringResource(id = R.string.cover),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.music),
                    error = painterResource(R.drawable.music),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = music.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subtitle,
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = 11.sp,
                        color = secondaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (music.lrc) {
                        Spacer(modifier = Modifier.width(5.dp))
                        LrcBadge(
                            visible = true,
                            tint = accent.copy(alpha = 0.82f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            if (uploadTime.isNotBlank()) {
                Text(
                    text = uploadTime,
                    modifier = if (index < 3) {
                        Modifier
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.11f))
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    } else {
                        Modifier
                    },
                    fontSize = 10.sp,
                    fontWeight = if (index < 3) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (index < 3) accent else metaColor,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun Music.latestCoverUrl(): String =
    coverFilePath
        ?.takeIf { it.isNotBlank() }
        ?.let { UrlConfig.buildFullUrl(it) }
        ?: UrlConfig.getMusicCoverUrl(id)

private fun formatUploadTime(context: android.content.Context, timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60 * 1000 -> context.getString(R.string.just_now)
        diff < 60 * 60 * 1000 -> context.getString(R.string.minutes_ago_format, diff / (60 * 1000))
        diff < 24 * 60 * 60 * 1000 -> context.getString(R.string.hours_ago_format, diff / (60 * 60 * 1000))
        diff < 30L * 24 * 60 * 60 * 1000 -> context.getString(R.string.days_ago_format, diff / (24 * 60 * 60 * 1000))
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}
