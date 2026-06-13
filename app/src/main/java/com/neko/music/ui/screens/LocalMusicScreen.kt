package com.neko.music.ui.screens

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.R
import com.neko.music.data.manager.LocalMusicManager
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.neko.music.ui.components.AppPageBackgroundImage
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.SakuraPink
import com.neko.music.ui.theme.SkyBlue
import com.neko.music.ui.theme.isAppDarkTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocalMusicScreen(
    onBackClick: () -> Unit,
    onNavigateToPlayer: (Music) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val playerManager = remember { MusicPlayerManager.getInstance(context) }
    val localMusicManager = remember { LocalMusicManager(context) }
    val readAudioPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    val permissionState = rememberPermissionState(readAudioPermission)
    val hasPermission = permissionState.status.isGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.M

    val isDarkTheme = isAppDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    val glassTint = LiquidGlassDefaults.screenListCard
    val glassBg = glassTint.background(isDarkTheme)
    val glassBorder = glassTint.border(isDarkTheme)
    val glassHighlight = glassTint.highlight(isDarkTheme)

    var localMusic by remember { mutableStateOf<List<Music>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    fun loadLocalMusic() {
        if (!hasPermission || isLoading) return
        isLoading = true
        scope.launch {
            localMusic = localMusicManager.scanLocalMusic()
            isLoading = false
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            loadLocalMusic()
        }
    }

    val filteredMusic = remember(localMusic, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            localMusic
        } else {
            localMusic.filter { music ->
                music.title.contains(query, ignoreCase = true) ||
                    music.artist.contains(query, ignoreCase = true) ||
                    music.album.contains(query, ignoreCase = true)
            }
        }
    }

    fun playMusicList(startIndexInFiltered: Int) {
        if (filteredMusic.isEmpty()) return
        scope.launch {
            playerManager.playMusicListAndAwait(filteredMusic, startIndexInFiltered)
            onNavigateToPlayer(filteredMusic[startIndexInFiltered])
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop)
        ) {
            AppPageBackgroundImage(modifier = Modifier.fillMaxSize())
            PlaylistPageDarkTintOverlay(enabled = isDarkTheme)
        }

        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                LocalMusicTopBar(
                    count = localMusic.size,
                    isDarkTheme = isDarkTheme,
                    onBackClick = onBackClick,
                    onRefreshClick = { loadLocalMusic() },
                    canRefresh = hasPermission && !isLoading,
                )

                if (hasPermission) {
                    LocalMusicSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        isDarkTheme = isDarkTheme,
                        glassBg = glassBg,
                        glassBorder = glassBorder,
                        glassHighlight = glassHighlight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) {
                                androidx.compose.ui.res.stringResource(R.string.local_music_song_count, localMusic.size)
                            } else {
                                androidx.compose.ui.res.stringResource(R.string.local_music_search_count, filteredMusic.size)
                            },
                            color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.82f) else scheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                        TextButton(
                            onClick = { playMusicList(0) },
                            enabled = filteredMusic.isNotEmpty() && !isLoading,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (filteredMusic.isNotEmpty()) RoseRed else scheme.onSurfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(R.string.play_all),
                                color = if (filteredMusic.isNotEmpty()) RoseRed else scheme.onSurfaceVariant.copy(alpha = 0.45f),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when {
                        !hasPermission -> {
                            LocalMusicPermissionState(
                                onRequestPermission = { permissionState.launchPermissionRequest() },
                                isDarkTheme = isDarkTheme,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        isLoading && localMusic.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = RoseRed)
                            }
                        }

                        filteredMusic.isEmpty() -> {
                            LocalMusicEmptyState(
                                isSearching = searchQuery.isNotBlank(),
                                isDarkTheme = isDarkTheme,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 10.dp,
                                    bottom = 150.dp,
                                ),
                            ) {
                                itemsIndexed(
                                    items = filteredMusic,
                                    key = { _, music -> music.id },
                                ) { index, music ->
                                    LocalMusicItem(
                                        music = music,
                                        isDarkTheme = isDarkTheme,
                                        glassBg = glassBg,
                                        glassBorder = glassBorder,
                                        glassHighlight = glassHighlight,
                                        onClick = { playMusicList(index) },
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
private fun LocalMusicTopBar(
    count: Int,
    isDarkTheme: Boolean,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    canRefresh: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = androidx.compose.ui.res.stringResource(R.string.back),
                tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.9f) else scheme.onSurface,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.local_music),
                color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            if (count > 0) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.local_music_song_count, count),
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.7f) else scheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }
        IconButton(
            onClick = onRefreshClick,
            enabled = canRefresh,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = androidx.compose.ui.res.stringResource(R.string.refresh),
                tint = if (canRefresh) {
                    if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.9f) else scheme.onSurface
                } else {
                    scheme.onSurfaceVariant.copy(alpha = 0.35f)
                },
            )
        }
    }
}

@Composable
private fun LocalMusicSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isDarkTheme: Boolean,
    glassBg: Float,
    glassBorder: Float,
    glassHighlight: Float,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    GlassSurface(
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(20.dp),
        backgroundAlpha = glassBg,
        borderAlpha = glassBorder,
        highlightAlpha = glassHighlight,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = androidx.compose.ui.res.stringResource(R.string.search),
                tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.7f) else scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface,
                ),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (query.isEmpty()) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(R.string.search_songs),
                                fontSize = 14.sp,
                                color = if (isDarkTheme) {
                                    Color(0xFFB8B8D1).copy(alpha = 0.55f)
                                } else {
                                    scheme.onSurfaceVariant.copy(alpha = 0.65f)
                                },
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
private fun LocalMusicItem(
    music: Music,
    isDarkTheme: Boolean,
    glassBg: Float,
    glassBorder: Float,
    glassHighlight: Float,
    onClick: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playerManager = remember { MusicPlayerManager.getInstance(context) }
    val currentMusicId by playerManager.currentMusicId.collectAsState()
    val isCurrent = currentMusicId == music.id
    val scheme = MaterialTheme.colorScheme
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
        highlightAlpha = glassHighlight,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(SkyBlue.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (!music.coverFilePath.isNullOrBlank()) {
                    AsyncImage(
                        model = music.coverFilePath,
                        contentDescription = music.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.music),
                        contentDescription = null,
                        tint = if (isCurrent) RoseRed else SkyBlue,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = music.title,
                    color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildPrimaryLocalMeta(music),
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.78f) else scheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
//                val secondaryMeta = buildSecondaryLocalMeta(music)
//                if (secondaryMeta.isNotBlank()) {
//                    Spacer(modifier = Modifier.height(3.dp))
//                    Text(
//                        text = secondaryMeta,
//                        color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.58f) else scheme.onSurfaceVariant.copy(alpha = 0.78f),
//                        fontSize = 12.sp,
//                        maxLines = 1,
//                        overflow = TextOverflow.Ellipsis,
//                    )
//                }
//                if (!music.lyricsPreview.isNullOrBlank()) {
//                    Spacer(modifier = Modifier.height(3.dp))
//                    Text(
//                        text = music.lyricsPreview,
//                        color = RoseRed.copy(alpha = if (isDarkTheme) 0.82f else 0.72f),
//                        fontSize = 12.sp,
//                        maxLines = 1,
//                        overflow = TextOverflow.Ellipsis,
//                    )
//                }
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = androidx.compose.ui.res.stringResource(R.string.play),
                tint = if (isCurrent) RoseRed else SakuraPink,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun LocalMusicPermissionState(
    onRequestPermission: () -> Unit,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.music),
            contentDescription = null,
            tint = RoseRed,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.local_music_permission_title),
            color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.local_music_permission_message),
            color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.78f) else scheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = RoseRed),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(text = androidx.compose.ui.res.stringResource(R.string.authorize))
        }
    }
}

@Composable
private fun LocalMusicEmptyState(
    isSearching: Boolean,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isSearching) {
                androidx.compose.ui.res.stringResource(R.string.no_related_songs)
            } else {
                androidx.compose.ui.res.stringResource(R.string.local_music_empty)
            },
            color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.82f) else scheme.onSurfaceVariant,
            fontSize = 16.sp,
        )
    }
}

private fun formatDurationSeconds(seconds: Int): String {
    if (seconds <= 0) return ""
    val minutes = seconds / 60
    val remain = seconds % 60
    return "%d:%02d".format(minutes, remain)
}

private fun buildPrimaryLocalMeta(music: Music): String {
    return listOf(
        music.artist,
        music.album,
        formatDurationSeconds(music.duration),
    ).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun buildSecondaryLocalMeta(music: Music): String {
    val track = listOfNotNull(
        music.discNumber?.takeIf { it.isNotBlank() }?.let { "D$it" },
        music.trackNumber?.takeIf { it.isNotBlank() }?.let { "#$it" },
    ).joinToString(" ")
    return listOf(
        music.year.orEmpty(),
        music.genre.orEmpty(),
        music.albumArtist?.takeIf { it != music.artist }.orEmpty(),
        music.composer?.takeIf { it != music.artist }.orEmpty(),
        track,
        if (music.lrc) "LRC" else "",
    ).filter { it.isNotBlank() }.joinToString(" · ")
}
