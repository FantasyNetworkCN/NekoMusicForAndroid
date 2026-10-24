package com.neko.music.ui.screens

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import java.text.DateFormat
import java.util.Date
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.data.manager.PlaylistManager
import com.neko.music.data.manager.TokenManager
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LrcBadge
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.components.AppPageBackgroundImage
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.ui.theme.LightRose
import com.neko.music.ui.theme.RoseRed
import com.neko.music.util.UrlConfig
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun DailyRecommendationScreen(
    onNavigateToPlayer: (Music) -> Unit = {},
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val musicApi = remember { MusicApi(context) }
    val playerManager = remember { MusicPlayerManager.getInstance(context) }
    val playlistManager = remember { PlaylistManager.getInstance(context) }

    var musicList by remember { mutableStateOf<List<Music>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }

    fun loadData() {
        loading = true
        scope.launch {
            try {
                val token = TokenManager(context).getToken().orEmpty()
                if (token.isBlank()) {
                    musicList = emptyList()
                    loadError = false
                } else {
                    val result = musicApi.getDailyRecommendations(token)
                    result.onSuccess { list ->
                        musicList = list
                        loadError = false
                    }.onFailure {
                        loadError = true
                    }
                }
            } catch (e: Exception) {
                Log.e("DailyRecommendationScreen", "加载失败", e)
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
                val token = TokenManager(context).getToken().orEmpty()
                if (token.isBlank()) {
                    musicList = emptyList()
                    loadError = false
                } else {
                    val result = musicApi.getDailyRecommendations(token)
                    result.onSuccess { list ->
                        musicList = list
                        loadError = false
                    }.onFailure {
                        loadError = true
                    }
                }
            } catch (e: Exception) {
                loadError = true
            } finally {
                refreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    fun playMusicAndNavigate(music: Music) {
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
                Log.e("DailyRecommendationScreen", "播放失败", e)
            }
        }
    }

    fun playAllAndReplacePlaylist() {
        scope.launch {
            try {
                if (musicList.isEmpty()) return@launch
                playlistManager.clearPlaylist()
                musicList.forEach { music ->
                    val url = musicApi.getMusicFileUrl(music)
                    playlistManager.addToPlaylist(
                        Music(
                            id = music.id,
                            title = music.title,
                            artist = music.artist,
                            album = music.album,
                            duration = music.duration,
                            filePath = url,
                            coverFilePath = music.coverFilePath ?: "",
                            uploadUserId = music.uploadUserId,
                            createdAt = music.createdAt
                        )
                    )
                }
                playMusicAndNavigate(musicList.first())
            } catch (e: Exception) {
                Log.e("DailyRecommendationScreen", "播放全部失败", e)
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()
    val pageTitleColor = if (isDark) Color.White else scheme.onSurface
    val pageBodyColor =
        if (isDark) Color.White.copy(alpha = 0.78f) else scheme.onSurface.copy(alpha = 0.72f)
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    val today = remember { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date()) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop)
        ) {
            AppPageBackgroundImage(
                modifier = Modifier.fillMaxSize(),
            )
            PlaylistPageDarkTintOverlay(enabled = isDark)
        }

        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                DailyTopBar(
                    date = today,
                    isDark = isDark,
                    onBackClick = onBackClick
                )

                when {
                    loading && musicList.isEmpty() -> {
                        DailyLoadingState(
                            isDark = isDark,
                            messageColor = pageBodyColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                    loadError && musicList.isEmpty() -> {
                        DailyStateCard(
                            icon = Icons.Default.Info,
                            title = stringResource(id = R.string.daily_error_title),
                            message = stringResource(id = R.string.daily_error_hint),
                            actionLabel = stringResource(id = R.string.daily_refresh),
                            onAction = { loadData() },
                            isDark = isDark,
                            titleColor = pageTitleColor,
                            bodyColor = pageBodyColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                    musicList.isEmpty() -> {
                        DailyStateCard(
                            icon = Icons.Default.PlayArrow,
                            title = stringResource(id = R.string.no_daily_recommendation),
                            message = stringResource(id = R.string.daily_empty_hint),
                            actionLabel = stringResource(id = R.string.daily_refresh),
                            onAction = { loadData() },
                            isDark = isDark,
                            titleColor = pageTitleColor,
                            bodyColor = pageBodyColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                    else -> {
                        val pullRefreshState = rememberPullRefreshState(
                            refreshing = refreshing,
                            onRefresh = { refreshData() }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .pullRefresh(pullRefreshState)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 6.dp,
                                    bottom = 180.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item(key = "daily-hero") {
                                    DailyHero(
                                        coverUrl = musicList.firstOrNull()
                                            ?.displayDailyCoverUrl()
                                            .orEmpty(),
                                        trackCount = musicList.size,
                                        date = today,
                                        isDark = isDark,
                                        titleColor = pageTitleColor,
                                        bodyColor = pageBodyColor
                                    )
                                }
                                item(key = "daily-play-all") {
                                    DailyPlayAllButton(
                                        trackCount = musicList.size,
                                        isDark = isDark,
                                        onClick = { playAllAndReplacePlaylist() }
                                    )
                                }
                                itemsIndexed(
                                    items = musicList,
                                    key = { _, music -> music.id }
                                ) { index, music ->
                                    DailyTrackItem(
                                        music = music,
                                        index = index,
                                        isDark = isDark,
                                        onClick = { playMusicAndNavigate(music) }
                                    )
                                }
                            }

                            PullRefreshIndicator(
                                refreshing = refreshing,
                                state = pullRefreshState,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 4.dp),
                                backgroundColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyTopBar(
    date: String,
    isDark: Boolean,
    onBackClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val backScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "daily-back-press"
    )
    val glass = LiquidGlassDefaults.playlistDetail
    val titleColor = if (isDark) Color(0xFFF4F4F7) else Color.Black
    val secondaryColor = if (isDark) Color.White.copy(alpha = 0.52f) else Color.Black.copy(alpha = 0.46f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassSurface(
            modifier = Modifier
                .size(42.dp)
                .scale(backScale),
            shape = CircleShape,
            material3Container = scheme.surfaceContainerHigh,
            backgroundAlpha = if (isDark) {
                glass.musicRowBackgroundAlphaDark
            } else {
                glass.musicRowBackgroundAlphaLight
            },
            borderAlpha = if (isDark) glass.musicRowBorderAlphaDark else glass.musicRowBorderAlphaLight,
            highlightAlpha = if (isDark) {
                glass.musicRowHighlightAlphaDark
            } else {
                glass.musicRowHighlightAlphaLight
            },
            borderColor = if (isDark) Color.White else scheme.outline,
            liquidBlur = glass.musicRowLiquidBlur,
            liquidLensHeight = glass.musicRowLiquidLensHeight,
            liquidLensAmount = glass.musicRowLiquidLensAmount
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onBackClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(id = R.string.back),
                    tint = titleColor.copy(alpha = 0.84f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = stringResource(id = R.string.daily_recommendation_title),
            modifier = Modifier.weight(1f),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = date,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = secondaryColor,
            maxLines = 1
        )
    }
}

@Composable
private fun DailyHero(
    coverUrl: String,
    trackCount: Int,
    date: String,
    isDark: Boolean,
    titleColor: Color,
    bodyColor: Color,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LiquidGlassDefaults.playlistDetail
    val accent = if (isDark) LightRose else RoseRed

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp),
        shape = RoundedCornerShape(28.dp),
        material3Shape = RoundedCornerShape(16.dp),
        material3Container = scheme.surfaceContainer,
        backgroundAlpha = if (isDark) {
            glass.batchDockBackgroundAlphaDark
        } else {
            glass.batchDockBackgroundAlphaLight
        },
        borderAlpha = if (isDark) glass.batchDockBorderAlphaDark else glass.batchDockBorderAlphaLight,
        highlightAlpha = if (isDark) {
            glass.batchDockHighlightAlphaDark
        } else {
            glass.batchDockHighlightAlphaLight
        },
        borderColor = if (isDark) Color.White else scheme.outline,
        liquidBlur = glass.batchDockLiquidBlur,
        liquidLensHeight = glass.batchDockLiquidLensHeight,
        liquidLensAmount = glass.batchDockLiquidLensAmount,
        surfaceTint = accent.copy(alpha = if (isDark) 0.07f else 0.035f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(accent, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = stringResource(id = R.string.daily_hero_eyebrow),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = accent,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(id = R.string.daily_recommendation_title),
                    fontSize = 27.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.daily_recommendation_desc),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = bodyColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(id = R.string.daily_track_count, trackCount),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor.copy(alpha = 0.78f)
                    )
                    Text(
                        text = "  ·  ",
                        fontSize = 11.sp,
                        color = bodyColor.copy(alpha = 0.55f)
                    )
                    Text(
                        text = date,
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = bodyColor.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.34f),
                                scheme.surfaceVariant.copy(alpha = 0.18f)
                            )
                        )
                    )
                    .border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.24f),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = stringResource(id = R.string.cover),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.music),
                    error = painterResource(R.drawable.music)
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f))
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun DailyPlayAllButton(
    trackCount: Int,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LiquidGlassDefaults.playlistDetail
    val accent = if (isDark) LightRose else RoseRed
    val titleColor = if (isDark) Color(0xFFF4F4F7) else Color.Black
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "daily-play-all-press"
    )

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(18.dp),
        material3Shape = RoundedCornerShape(50),
        material3Container = MaterialTheme.colorScheme.secondaryContainer,
        backgroundAlpha = if (isDark) {
            glass.batchDockBackgroundAlphaDark
        } else {
            glass.batchDockBackgroundAlphaLight
        },
        borderAlpha = if (isDark) glass.batchDockBorderAlphaDark else glass.batchDockBorderAlphaLight,
        highlightAlpha = if (isDark) {
            glass.batchDockHighlightAlphaDark
        } else {
            glass.batchDockHighlightAlphaLight
        },
        borderColor = if (isDark) Color.White else scheme.outline,
        liquidBlur = glass.batchDockLiquidBlur,
        liquidLensHeight = glass.batchDockLiquidLensHeight,
        liquidLensAmount = glass.batchDockLiquidLensAmount,
        surfaceTint = accent.copy(alpha = if (isDark) 0.1f else 0.06f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(accent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(id = R.string.play_all),
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            Text(
                text = stringResource(id = R.string.daily_track_count, trackCount),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = titleColor.copy(alpha = 0.52f)
            )
        }
    }
}

@Composable
private fun DailyTrackItem(
    music: Music,
    index: Int,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LiquidGlassDefaults.playlistDetail
    val accent = if (isDark) LightRose else RoseRed
    val titleColor = if (isDark) Color(0xFFF1F1F5) else Color.Black
    val secondaryColor = if (isDark) Color.White.copy(alpha = 0.58f) else Color.Black.copy(alpha = 0.52f)
    val metaColor = if (isDark) Color.White.copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.38f)
    val coverUrl = remember(music.id, music.coverFilePath) { music.displayDailyCoverUrl() }
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
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "daily-track-press-${music.id}"
    )

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(glass.musicRowCornerDp),
        material3Shape = RoundedCornerShape(12.dp),
        material3Container = MaterialTheme.colorScheme.surfaceContainer,
        backgroundAlpha = if (isDark) {
            glass.musicRowBackgroundAlphaDark
        } else {
            glass.musicRowBackgroundAlphaLight
        },
        borderAlpha = if (isDark) glass.musicRowBorderAlphaDark else glass.musicRowBorderAlphaLight,
        highlightAlpha = if (isDark) {
            glass.musicRowHighlightAlphaDark
        } else {
            glass.musicRowHighlightAlphaLight
        },
        borderColor = if (isDark) Color.White else scheme.outline,
        liquidBlur = glass.musicRowLiquidBlur,
        liquidLensHeight = glass.musicRowLiquidLensHeight,
        liquidLensAmount = glass.musicRowLiquidLensAmount
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${index + 1}",
                modifier = Modifier.width(22.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = metaColor
            )
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isDark) Color(0xFF353558).copy(alpha = 0.6f)
                        else Color(0xFFE0E0E0)
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = stringResource(id = R.string.cover),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.music),
                    error = painterResource(R.drawable.music)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = music.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subtitle,
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = 11.sp,
                        color = secondaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (music.lrc) {
                        Spacer(modifier = Modifier.width(5.dp))
                        LrcBadge(
                            visible = true,
                            tint = accent.copy(alpha = 0.82f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Text(
                text = formatDailyDuration(music.duration),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = metaColor
            )
        }
    }
}

@Composable
private fun DailyLoadingState(
    isDark: Boolean,
    messageColor: Color,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LiquidGlassDefaults.playlistDetail
    val skeletonColor = if (isDark) Color.White else scheme.onSurface
    val transition = rememberInfiniteTransition(label = "daily-loading")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "daily-loading-pulse"
    )

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp)
                .alpha(0.5f + pulse * 0.25f),
            shape = RoundedCornerShape(28.dp),
            material3Shape = RoundedCornerShape(16.dp),
            material3Container = scheme.surfaceContainer,
            backgroundAlpha = if (isDark) {
                glass.batchDockBackgroundAlphaDark
            } else {
                glass.batchDockBackgroundAlphaLight
            },
            borderAlpha = if (isDark) {
                glass.batchDockBorderAlphaDark
            } else {
                glass.batchDockBorderAlphaLight
            },
            highlightAlpha = if (isDark) {
                glass.batchDockHighlightAlphaDark
            } else {
                glass.batchDockHighlightAlphaLight
            },
            borderColor = if (isDark) Color.White else scheme.outline,
            liquidBlur = glass.batchDockLiquidBlur,
            liquidLensHeight = glass.batchDockLiquidLensHeight,
            liquidLensAmount = glass.batchDockLiquidLensAmount
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DailySkeletonBlock(
                        modifier = Modifier
                            .width(58.dp)
                            .height(8.dp),
                        color = skeletonColor
                    )
                    DailySkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .height(25.dp),
                        color = skeletonColor
                    )
                    DailySkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        color = skeletonColor
                    )
                    DailySkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.56f)
                            .height(10.dp),
                        color = skeletonColor
                    )
                }
                DailySkeletonBlock(
                    modifier = Modifier.size(112.dp),
                    color = skeletonColor,
                    corner = 24.dp
                )
            }
        }

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .alpha(0.5f + pulse * 0.25f),
            shape = RoundedCornerShape(18.dp),
            material3Shape = RoundedCornerShape(50),
            material3Container = scheme.secondaryContainer,
            backgroundAlpha = if (isDark) {
                glass.batchDockBackgroundAlphaDark
            } else {
                glass.batchDockBackgroundAlphaLight
            },
            borderAlpha = if (isDark) {
                glass.batchDockBorderAlphaDark
            } else {
                glass.batchDockBorderAlphaLight
            },
            highlightAlpha = if (isDark) {
                glass.batchDockHighlightAlphaDark
            } else {
                glass.batchDockHighlightAlphaLight
            },
            borderColor = if (isDark) Color.White else scheme.outline,
            liquidBlur = glass.batchDockLiquidBlur,
            liquidLensHeight = glass.batchDockLiquidLensHeight,
            liquidLensAmount = glass.batchDockLiquidLensAmount
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DailySkeletonBlock(
                    modifier = Modifier.size(28.dp),
                    color = skeletonColor,
                    corner = 14.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                DailySkeletonBlock(
                    modifier = Modifier
                        .width(104.dp)
                        .height(12.dp),
                    color = skeletonColor
                )
            }
        }

        repeat(4) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .alpha(0.45f + pulse * 0.22f),
                shape = RoundedCornerShape(glass.musicRowCornerDp),
                material3Shape = RoundedCornerShape(12.dp),
                material3Container = scheme.surfaceContainer,
                backgroundAlpha = if (isDark) {
                    glass.musicRowBackgroundAlphaDark
                } else {
                    glass.musicRowBackgroundAlphaLight
                },
                borderAlpha = if (isDark) {
                    glass.musicRowBorderAlphaDark
                } else {
                    glass.musicRowBorderAlphaLight
                },
                highlightAlpha = if (isDark) {
                    glass.musicRowHighlightAlphaDark
                } else {
                    glass.musicRowHighlightAlphaLight
                },
                borderColor = if (isDark) Color.White else scheme.outline,
                liquidBlur = glass.musicRowLiquidBlur,
                liquidLensHeight = glass.musicRowLiquidLensHeight,
                liquidLensAmount = glass.musicRowLiquidLensAmount
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DailySkeletonBlock(
                        modifier = Modifier
                            .width(18.dp)
                            .height(8.dp),
                        color = skeletonColor
                    )
                    DailySkeletonBlock(
                        modifier = Modifier.size(46.dp),
                        color = skeletonColor,
                        corner = 10.dp
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DailySkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.66f)
                                .height(11.dp),
                            color = skeletonColor
                        )
                        DailySkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.42f)
                                .height(8.dp),
                            color = skeletonColor
                        )
                    }
                    DailySkeletonBlock(
                        modifier = Modifier
                            .width(24.dp)
                            .height(8.dp),
                        color = skeletonColor
                    )
                }
            }
        }

        Text(
            text = stringResource(id = R.string.daily_loading),
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = messageColor.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun DailySkeletonBlock(
    modifier: Modifier,
    color: Color,
    corner: androidx.compose.ui.unit.Dp = 6.dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(color.copy(alpha = 0.13f))
    )
}

@Composable
private fun DailyStateCard(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    isDark: Boolean,
    titleColor: Color,
    bodyColor: Color,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LiquidGlassDefaults.playlistDetail
    val accent = if (isDark) LightRose else RoseRed
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "daily-state-action-press"
    )

    Box(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            material3Shape = RoundedCornerShape(16.dp),
            material3Container = scheme.surfaceContainerHigh,
            backgroundAlpha = if (isDark) {
                glass.batchDockBackgroundAlphaDark
            } else {
                glass.batchDockBackgroundAlphaLight
            },
            borderAlpha = if (isDark) {
                glass.batchDockBorderAlphaDark
            } else {
                glass.batchDockBorderAlphaLight
            },
            highlightAlpha = if (isDark) {
                glass.batchDockHighlightAlphaDark
            } else {
                glass.batchDockHighlightAlphaLight
            },
            borderColor = if (isDark) Color.White else scheme.outline,
            liquidBlur = glass.batchDockLiquidBlur,
            liquidLensHeight = glass.batchDockLiquidLensHeight,
            liquidLensAmount = glass.batchDockLiquidLensAmount,
            surfaceTint = accent.copy(alpha = if (isDark) 0.04f else 0.025f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(accent.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(23.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = message,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = bodyColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                GlassSurface(
                    modifier = Modifier
                        .height(42.dp)
                        .scale(scale)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onAction
                        ),
                    shape = RoundedCornerShape(21.dp),
                    material3Shape = RoundedCornerShape(50),
                    material3Container = MaterialTheme.colorScheme.secondaryContainer,
                    backgroundAlpha = if (isDark) {
                        glass.batchDockBackgroundAlphaDark
                    } else {
                        glass.batchDockBackgroundAlphaLight
                    },
                    borderAlpha = if (isDark) {
                        glass.batchDockBorderAlphaDark
                    } else {
                        glass.batchDockBorderAlphaLight
                    },
                    highlightAlpha = if (isDark) {
                        glass.batchDockHighlightAlphaDark
                    } else {
                        glass.batchDockHighlightAlphaLight
                    },
                    borderColor = if (isDark) Color.White else scheme.outline,
                    liquidBlur = glass.batchDockLiquidBlur,
                    liquidLensHeight = glass.batchDockLiquidLensHeight,
                    liquidLensAmount = glass.batchDockLiquidLensAmount,
                    surfaceTint = accent.copy(alpha = if (isDark) 0.1f else 0.06f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = actionLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = titleColor
                        )
                    }
                }
            }
        }
    }
}

private fun Music.displayDailyCoverUrl(): String =
    coverFilePath
        ?.takeIf { it.isNotBlank() }
        ?.let { UrlConfig.buildFullUrl(it) }
        ?: UrlConfig.getMusicCoverUrl(id)

private fun formatDailyDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    return String.format("%d:%02d", minutes, remainingSeconds)
}
