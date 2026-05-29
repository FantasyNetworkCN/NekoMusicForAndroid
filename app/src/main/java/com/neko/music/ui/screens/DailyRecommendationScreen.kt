package com.neko.music.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.data.manager.PlaylistManager
import com.neko.music.data.manager.TokenManager
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.util.UrlConfig
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun DailyRecommendationScreen(
    onNavigateToPlayer: (Music) -> Unit = {}
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
    val isDark = scheme.background.luminance() < 0.5f
    val pageTitleColor = if (isDark) Color.White else scheme.onSurface
    val pageBodyColor =
        if (isDark) Color.White.copy(alpha = 0.92f) else scheme.onSurface.copy(alpha = 0.84f)
    val itemMetaColor =
        if (isDark) Color.White.copy(alpha = 0.88f) else scheme.onSurface.copy(alpha = 0.78f)
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
            PlaylistPageDarkTintOverlay()
        }

        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            when {
                loading && musicList.isEmpty() -> {
                    LatestLoadingState(messageColor = pageBodyColor)
                }
                loadError && musicList.isEmpty() -> {
                    LatestErrorState(
                        onRetry = { loadData() },
                        titleColor = pageTitleColor,
                        messageColor = pageBodyColor
                    )
                }
                musicList.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.no_daily_recommendation),
                            color = pageBodyColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
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
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 60.dp,
                                bottom = 180.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.daily_recommendation_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = pageTitleColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val playAllGlass = LiquidGlassDefaults.rankingRetryButton
                                    GlassSurface(
                                        modifier = Modifier.clickable {
                                            playAllAndReplacePlaylist()
                                        },
                                        shape = RoundedCornerShape(18.dp),
                                        backgroundAlpha = playAllGlass.tint.background(isDark),
                                        borderAlpha = playAllGlass.tint.border(isDark),
                                        highlightAlpha = playAllGlass.tint.highlight(isDark),
                                        liquidBlur = playAllGlass.liquid.blur,
                                        liquidLensHeight = playAllGlass.liquid.lensHeight,
                                        liquidLensAmount = playAllGlass.liquid.lensAmount,
                                        borderColor = if (isDark) {
                                            androidx.compose.ui.graphics.Color.White
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = stringResource(id = R.string.play_all),
                                                tint = pageTitleColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(
                                                    id = R.string.play_all_count,
                                                    musicList.size
                                                ),
                                                color = pageTitleColor,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(id = R.string.daily_recommendation_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = pageBodyColor,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            itemsIndexed(
                                items = musicList,
                                key = { _, music -> music.id }
                            ) { index, music ->
                                LatestItem(
                                    music = music,
                                    index = index,
                                    onClick = { playMusicAndNavigate(music) },
                                    titleColor = pageTitleColor,
                                    subtitleColor = pageBodyColor,
                                    metaColor = itemMetaColor
                                )
                            }
                        }

                        PullRefreshIndicator(
                            refreshing = refreshing,
                            state = pullRefreshState,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 96.dp),
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
