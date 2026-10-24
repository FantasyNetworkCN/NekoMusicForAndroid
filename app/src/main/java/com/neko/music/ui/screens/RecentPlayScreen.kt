package com.neko.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.data.manager.PlaylistManager
import com.neko.music.data.manager.RecentPlayManager
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.neko.music.ui.components.AppPageBackgroundImage
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.PlaylistDetailLiquidGlassParams
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.components.isLiquidGlassEffectActive
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.util.UrlConfig
import kotlinx.coroutines.launch

@Composable
fun RecentPlayScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recentPlayManager = remember(context) { RecentPlayManager(context) }
    val playerManager = remember(context) { MusicPlayerManager.getInstance(context) }
    val playlistManager = remember(context) { PlaylistManager.getInstance(context) }
    val musicApi = remember(context) { MusicApi(context) }

    var playHistory by remember { mutableStateOf<List<Music>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        isLoading = true
        playHistory = recentPlayManager.getRecentPlays()
        isLoading = false
    }

    val filteredList = if (searchQuery.isEmpty()) {
        playHistory
    } else {
        playHistory.filter { music ->
            music.title.contains(searchQuery, ignoreCase = true) ||
                music.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    suspend fun playAllRecent() {
        playlistManager.clearPlaylist()
        filteredList.forEach { music ->
            playlistManager.addToPlaylist(music)
        }

        filteredList.firstOrNull()?.let { music ->
            playerManager.playMusic(
                musicApi.getMusicFileUrl(music),
                music.id,
                music.title,
                music.artist,
                music.coverFilePath,
                music.displayCoverUrl()
            )
        }
    }

    suspend fun playRecent(music: Music) {
        playlistManager.addToPlaylist(music)
        playerManager.playMusic(
            musicApi.getMusicFileUrl(music),
            music.id,
            music.title,
            music.artist,
            music.coverFilePath,
            music.displayCoverUrl()
        )
    }

    val addAllRecentToQueue: () -> Unit = {
        if (filteredList.isNotEmpty()) {
            scope.launch {
                filteredList.forEach { music ->
                    playlistManager.addToPlaylist(music)
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.playlist_added_to_queue_format, filteredList.size),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val removeRecent: (Music) -> Unit = { music ->
        recentPlayManager.removeRecentPlay(music.id)
        playHistory = playHistory.filterNot { it.id == music.id }
        Toast.makeText(
            context,
            context.getString(R.string.remove_from_recent_play_success),
            Toast.LENGTH_SHORT
        ).show()
    }

    val isDarkTheme = isAppDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val liquidGlass = LiquidGlassDefaults.playlistDetail
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    val useLiquidStyle = isLiquidGlassEffectActive(pageBackdrop)
    val collectionCoverUrl = remember(playHistory) {
        playHistory.firstOrNull()?.displayCoverUrl().orEmpty()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop)
        ) {
            AppPageBackgroundImage(
                modifier = Modifier.fillMaxSize(),
            )
            PlaylistPageDarkTintOverlay(enabled = isDarkTheme)
        }

        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                            tint = scheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.recent_play),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!isLoading && playHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PlaylistSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        liquidGlass = liquidGlass,
                        placeholder = stringResource(id = R.string.search_songs),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = scheme.primary)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(scheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = collectionCoverUrl,
                                    contentDescription = stringResource(id = R.string.recent_play),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    placeholder = painterResource(R.drawable.music),
                                    error = painterResource(R.drawable.music)
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.recent_play),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(
                                        R.string.songs_count_label,
                                        playHistory.size
                                    ),
                                    fontSize = 14.sp,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { scope.launch { playAllRecent() } },
                                enabled = filteredList.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = scheme.primary,
                                    contentColor = scheme.onPrimary,
                                    disabledContainerColor = scheme.primary.copy(alpha = 0.38f),
                                    disabledContentColor = scheme.onPrimary.copy(alpha = 0.38f),
                                ),
                                shape = RoundedCornerShape(21.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = stringResource(id = R.string.play_all),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            PlaylistAddToQueueButton(
                                enabled = filteredList.isNotEmpty(),
                                onClick = addAllRecentToQueue,
                                liquidGlass = liquidGlass,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (filteredList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (searchQuery.isBlank()) {
                                            stringResource(id = R.string.no_play_history)
                                        } else {
                                            stringResource(id = R.string.no_search_results)
                                        },
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (searchQuery.isBlank()) {
                                            stringResource(id = R.string.go_discover)
                                        } else {
                                            stringResource(id = R.string.try_other_keywords)
                                        },
                                        fontSize = 14.sp,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = 150.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(
                                    if (useLiquidStyle) 8.dp else 4.dp
                                )
                            ) {
                                itemsIndexed(
                                    items = filteredList,
                                    key = { _, music -> music.id }
                                ) { _, music ->
                                    RecentPlayItem(
                                        music = music,
                                        position = playHistory.indexOfFirst {
                                            it.id == music.id
                                        } + 1,
                                        onClick = { scope.launch { playRecent(music) } },
                                        onPlayNext = { playerManager.playNext(music) },
                                        onRemove = { removeRecent(music) },
                                        liquidGlass = liquidGlass
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
    position: Int,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onRemove: () -> Unit,
    liquidGlass: PlaylistDetailLiquidGlassParams = LiquidGlassDefaults.playlistDetail,
) {
    val coverUrl = remember(music.id, music.coverFilePath) { music.displayCoverUrl() }
    val isDarkTheme = isAppDarkTheme()
    val scheme = MaterialTheme.colorScheme
    var actionsExpanded by remember { mutableStateOf(false) }
    val playNextText = stringResource(id = R.string.play_next)
    val moreActionsText = stringResource(id = R.string.more)
    val removeRecentText = stringResource(id = R.string.remove_from_recent_play)

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .combinedClickable(
                interactionSource = remember(music.id) { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
                onLongClick = { actionsExpanded = true },
            ),
        shape = RoundedCornerShape(liquidGlass.musicRowCornerDp),
        material3Shape = RoundedCornerShape(16.dp),
        material3Container = scheme.surfaceContainerLow,
        backgroundAlpha = if (isDarkTheme) {
            liquidGlass.musicRowBackgroundAlphaDark
        } else {
            liquidGlass.musicRowBackgroundAlphaLight
        },
        borderAlpha = if (isDarkTheme) {
            liquidGlass.musicRowBorderAlphaDark
        } else {
            liquidGlass.musicRowBorderAlphaLight
        },
        highlightAlpha = if (isDarkTheme) {
            liquidGlass.musicRowHighlightAlphaDark
        } else {
            liquidGlass.musicRowHighlightAlphaLight
        },
        borderColor = if (isDarkTheme) Color.White else scheme.outline,
        liquidBlur = liquidGlass.musicRowLiquidBlur,
        liquidLensHeight = liquidGlass.musicRowLiquidLensHeight,
        liquidLensAmount = liquidGlass.musicRowLiquidLensAmount
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "$position",
                fontSize = 14.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.surfaceContainerHighest),
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

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = music.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = music.artist,
                    fontSize = 13.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = formatDuration(music.duration),
                fontSize = 13.sp,
                color = scheme.onSurfaceVariant
            )

            Box {
                IconButton(
                    onClick = { actionsExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = moreActionsText,
                        tint = scheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = actionsExpanded,
                    onDismissRequest = { actionsExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(playNextText, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            actionsExpanded = false
                            onPlayNext()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(removeRecentText, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            actionsExpanded = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

private fun Music.displayCoverUrl(): String =
    coverFilePath
        ?.takeIf { it.isNotEmpty() }
        ?.let { UrlConfig.buildFullUrl(it) }
        ?: UrlConfig.getMusicCoverUrl(id)

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%d:%02d", minutes, remainingSeconds)
}
