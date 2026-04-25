package com.neko.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.neko.music.R
import com.neko.music.util.UrlConfig
import com.neko.music.data.manager.PlaylistManager
import com.neko.music.data.model.Music
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.components.GlassSurface
import kotlinx.coroutines.launch

@Composable
fun PlaylistScreen(
    isVisible: Boolean,
    currentMusicId: Int?,
    onBackClick: () -> Unit,
    onMusicClick: (Music) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playlistManager = PlaylistManager.getInstance(context)
    val playlist by playlistManager.playlist.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onBackClick)
        ) {
            // 处理返回键
            BackHandler(enabled = isVisible) {
                onBackClick()
            }

            val panelShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            val panelModifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(600.dp)
                .clip(panelShape)

            if (isDarkTheme) {
                GlassSurface(
                    modifier = panelModifier,
                    shape = panelShape,
                    backgroundAlpha = 0.85f,
                    borderAlpha = 0.18f,
                    highlightAlpha = 0.10f
                ) {
                    PlaylistContent(
                        playlist = playlist,
                        currentMusicId = currentMusicId,
                        onBackClick = onBackClick,
                        onMusicClick = onMusicClick,
                        playlistManager = playlistManager,
                        scope = scope
                    )
                }
            } else {
                Column(
                    modifier = panelModifier
                        .background(Color.White)
                        .clickable(enabled = false) {},
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PlaylistContent(
                        playlist = playlist,
                        currentMusicId = currentMusicId,
                        onBackClick = onBackClick,
                        onMusicClick = onMusicClick,
                        playlistManager = playlistManager,
                        scope = scope
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistContent(
    playlist: List<Music>,
    currentMusicId: Int?,
    onBackClick: () -> Unit,
    onMusicClick: (Music) -> Unit,
    playlistManager: PlaylistManager,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = false) {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部栏
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
                    tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.9f) else Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = stringResource(id = R.string.playback_list),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black
            )

            androidx.compose.material3.TextButton(
                onClick = {
                    scope.launch {
                        currentMusicId?.let { playlistManager.clearPlaylistExcept(it) }
                    }
                }
            ) {
                Text(
                    text = stringResource(id = R.string.clear),
                    fontSize = 14.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                )
            }
        }

        if (!isDarkTheme) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFE0E0E0))
            )
        }

        if (playlist.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.playlist_empty),
                    fontSize = 14.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(playlist) { music ->
                    PlaylistItem(
                        music = music,
                        isPlaying = music.id == currentMusicId,
                        onClick = { onMusicClick(music) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistItem(
    music: Music,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    if (isDarkTheme) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(10.dp),
            backgroundAlpha = if (isPlaying) 0.45f else 0.22f,
            borderAlpha = if (isPlaying) 0.25f else 0.12f,
            highlightAlpha = if (isPlaying) 0.12f else 0.06f
        ) {
            PlaylistItemRow(music = music, isPlaying = isPlaying, isDarkTheme = true)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(
                    if (isPlaying) Color(0xFFF5F5F5) else Color.Transparent,
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistItemRow(music = music, isPlaying = isPlaying, isDarkTheme = false)
        }
    }
}

@Composable
private fun PlaylistItemRow(
    music: Music,
    isPlaying: Boolean,
    isDarkTheme: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isDarkTheme) Color(0xFF252545).copy(alpha = 0.6f) else Color(0xFFF5F5F5)
                ),
            contentAlignment = Alignment.Center
        ) {
            val coverUrl = if (!music.coverFilePath.isNullOrEmpty()) {
                UrlConfig.buildFullUrl("${music.coverFilePath}")
            } else {
                UrlConfig.getMusicCoverUrl(music.id)
            }
            AsyncImage(
                model = coverUrl,
                contentDescription = "封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = music.title,
                fontSize = 15.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isDarkTheme) {
                    Color.White.copy(alpha = if (isPlaying) 1.0f else 0.85f)
                } else {
                    if (isPlaying) Color.Black else Color.DarkGray
                },
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = music.artist,
                fontSize = 13.sp,
                color = if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color.Gray,
                maxLines = 1
            )
        }
    }
}