package com.neko.music.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.neko.music.R
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.util.UrlConfig
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.neko.music.ui.theme.RoseRed
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.launch

@Composable
fun RecentPlayScreen(
    onBackClick: () -> Unit,
    onMusicClick: (Music) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playerManager = MusicPlayerManager.getInstance(context)
    val scope = rememberCoroutineScope()
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    val glassTint = LiquidGlassDefaults.screenListCard
    val glassBg = glassTint.background(isDarkTheme)
    val glassBorder = glassTint.border(isDarkTheme)
    val glassHighlight = glassTint.highlight(isDarkTheme)

    var playHistory by remember { mutableStateOf<List<Music>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    
    // 加载最近播放历史
    LaunchedEffect(Unit) {
        isLoading = true
        scope.launch {
            val recentPlayManager = com.neko.music.data.manager.RecentPlayManager(context)
            playHistory = recentPlayManager.getRecentPlays()
            isLoading = false
        }
    }
    
    // 过滤后的列表
    val filteredList = if (searchQuery.isEmpty()) {
        playHistory
    } else {
        playHistory.filter { music ->
            music.title.contains(searchQuery, ignoreCase = true) ||
            music.artist.contains(searchQuery, ignoreCase = true)
        }
    }
    
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
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
                        tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.9f) else scheme.onSurface
                    )
                }

                Text(
                    text = stringResource(id = R.string.recent_play),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface
                )

                Spacer(modifier = Modifier.size(48.dp))
            }

            RecentPlaySearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                isDarkTheme = isDarkTheme,
                glassBg = glassBg,
                glassBorder = glassBorder,
                glassHighlight = glassHighlight,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
                        filteredList.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isEmpty()) {
                                        stringResource(id = R.string.no_play_history)
                                    } else {
                                        stringResource(id = R.string.no_related_songs)
                                    },
                                    fontSize = 16.sp,
                                    color = if (isDarkTheme) {
                                        Color(0xFFB8B8D1).copy(alpha = 0.8f)
                                    } else {
                                        Color.Gray
                                    }
                                )
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = 150.dp
                                )
                            ) {
                                items(filteredList) { music ->
                                    RecentPlayItem(
                                        music = music,
                                        onClick = { onMusicClick(music) },
                                        glassBg = glassBg,
                                        glassBorder = glassBorder,
                                        glassHighlight = glassHighlight
                                    )
                                }
                            }
                        }
                    }
            }
        }
        }
    }
}

@Composable
fun RecentPlayItem(
    music: Music,
    onClick: () -> Unit,
    glassBg: Float = 0.28f,
    glassBorder: Float = 0.14f,
    glassHighlight: Float = 0.08f
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playerManager = MusicPlayerManager.getInstance(context)
    val scope = rememberCoroutineScope()
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme

    val currentMusicId by playerManager.currentMusicId.collectAsState()

    val fullCoverUrl = if (!music.coverFilePath.isNullOrEmpty()) {
        UrlConfig.buildFullUrl("${music.coverFilePath}")
    } else {
        UrlConfig.getMusicCoverUrl(music.id)
    }

    val isCurrent = currentMusicId == music.id
    val shape = RoundedCornerShape(12.dp)

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isCurrent) {
                    Modifier.border(1.dp, RoseRed.copy(alpha = 0.55f), shape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        shape = shape,
        backgroundAlpha = glassBg,
        borderAlpha = glassBorder,
        highlightAlpha = glassHighlight
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        RoseRed.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = fullCoverUrl,
                    contentDescription = stringResource(id = R.string.cover),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = music.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = music.artist,
                    fontSize = 14.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = {
                    scope.launch {
                        val musicApi = com.neko.music.data.api.MusicApi(context)
                        val url = musicApi.getMusicFileUrl(music)
                        playerManager.playMusic(
                            url,
                            music.id,
                            music.title,
                            music.artist,
                            music.coverFilePath,
                            fullCoverUrl
                        )
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(id = R.string.play),
                    tint = RoseRed,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun RecentPlaySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isDarkTheme: Boolean,
    glassBg: Float,
    glassBorder: Float,
    glassHighlight: Float,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    GlassSurface(
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(20.dp),
        backgroundAlpha = glassBg,
        borderAlpha = glassBorder,
        highlightAlpha = glassHighlight
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(id = R.string.search),
                tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.7f) else scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface
                ),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(id = R.string.search_songs),
                            fontSize = 14.sp,
                            color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.6f) else scheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}