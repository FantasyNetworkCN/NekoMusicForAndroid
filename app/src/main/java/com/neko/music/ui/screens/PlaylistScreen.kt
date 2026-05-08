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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

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
            BackHandler(enabled = isVisible) {
                onBackClick()
            }

            val panelShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            val panelModifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(600.dp)
                .clip(panelShape)
                .clickable(enabled = false) {}

            GlassSurface(
                modifier = panelModifier,
                shape = panelShape,
                backgroundAlpha = if (isDark) 0.40f else 0.34f,
                borderAlpha = if (isDark) 0.16f else 0.20f,
                highlightAlpha = if (isDark) 0.08f else 0.11f,
                borderColor = if (isDark) Color.White else MaterialTheme.colorScheme.outline,
                liquidBlur = 10.dp,
                liquidLensHeight = 18.dp,
                liquidLensAmount = 28.dp
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

@Composable
fun PlaylistContent(
    playlist: List<Music>,
    currentMusicId: Int?,
    onBackClick: () -> Unit,
    onMusicClick: (Music) -> Unit,
    playlistManager: PlaylistManager,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = false) {},
        horizontalAlignment = Alignment.CenterHorizontally
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
                    tint = scheme.onSurface.copy(alpha = 0.88f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = stringResource(id = R.string.playback_list),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface
            )

            TextButton(
                onClick = {
                    scope.launch {
                        currentMusicId?.let { playlistManager.clearPlaylistExcept(it) }
                    }
                }
            ) {
                Text(
                    text = stringResource(id = R.string.clear),
                    fontSize = 14.sp,
                    color = scheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = scheme.outline.copy(alpha = 0.35f)
        )

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
                    color = scheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        backgroundAlpha = when {
            isPlaying && isDark -> 0.46f
            isPlaying && !isDark -> 0.36f
            isDark -> 0.22f
            else -> 0.20f
        },
        borderAlpha = when {
            isPlaying -> if (isDark) 0.24f else 0.22f
            else -> if (isDark) 0.12f else 0.16f
        },
        highlightAlpha = when {
            isPlaying -> if (isDark) 0.11f else 0.12f
            else -> if (isDark) 0.06f else 0.08f
        },
        borderColor = if (isDark) Color.White else scheme.outline,
        liquidBlur = 6.dp,
        liquidLensHeight = 14.dp,
        liquidLensAmount = 22.dp
    ) {
        PlaylistItemRow(
            music = music,
            isPlaying = isPlaying,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun PlaylistItemRow(
    music: Music,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceVariant.copy(alpha = 0.65f)),
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
                color = if (isPlaying) scheme.primary else scheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = music.artist,
                fontSize = 13.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
