package com.neko.music.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.data.manager.TokenManager
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
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

    val pageBackdrop = rememberLiquidPageBackdrop(MaterialTheme.colorScheme.background)

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
            when {
                loading && musicList.isEmpty() -> {
                    LatestLoadingState()
                }
                loadError && musicList.isEmpty() -> {
                    LatestErrorState(onRetry = { loadData() })
                }
                musicList.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.no_daily_recommendation),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                Text(
                                    text = stringResource(id = R.string.daily_recommendation_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(id = R.string.daily_recommendation_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    onClick = {
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
