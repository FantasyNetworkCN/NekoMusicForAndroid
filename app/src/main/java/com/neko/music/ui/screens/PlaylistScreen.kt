package com.neko.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import coil3.compose.AsyncImage
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.data.manager.PlaylistManager
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.theme.SakuraPink
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.util.UrlConfig
import java.util.Collections
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PlaylistScreen(
    isVisible: Boolean,
    currentMusicId: Int?,
    onBackClick: () -> Unit,
    onMusicClick: (Music) -> Unit
) {
    val context = LocalContext.current
    val playlistManager = PlaylistManager.getInstance(context)
    val playlist by playlistManager.playlist.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val isDark = isAppDarkTheme()

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

            val modalGlass = LiquidGlassDefaults.playlistModalBottomSheet
            GlassSurface(
                modifier = panelModifier,
                shape = panelShape,
                backgroundAlpha = modalGlass.tint.background(isDark),
                borderAlpha = modalGlass.tint.border(isDark),
                highlightAlpha = modalGlass.tint.highlight(isDark),
                borderColor = if (isDark) SakuraPink else MaterialTheme.colorScheme.outline,
                liquidBlur = modalGlass.liquid.blur,
                liquidLensHeight = modalGlass.liquid.lensHeight,
                liquidLensAmount = modalGlass.liquid.lensAmount
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
    val context = LocalContext.current
    val density = LocalDensity.current
    val itemHeightPx = with(density) { (64.dp + 8.dp).toPx() }

    val ordered = remember { mutableStateListOf<Music>() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playlist, draggingIndex) {
        if (draggingIndex == null) {
            ordered.clear()
            ordered.addAll(playlist)
        }
    }

    fun removeFromQueue(music: Music) {
        scope.launch {
            val wasCurrent = music.id == currentMusicId
            val nextBeforeRemove = if (wasCurrent) playlistManager.getNextMusic(music.id) else null
            playlistManager.removeFromPlaylist(music.id)
            if (wasCurrent) {
                val player = MusicPlayerManager.getInstance(context)
                val api = MusicApi(context)
                val target = nextBeforeRemove ?: playlistManager.getFirstMusic()
                if (target != null && target.id != music.id) {
                    val url = api.getMusicFileUrl(target)
                    val fullCoverUrl = if (!target.coverFilePath.isNullOrEmpty()) {
                        UrlConfig.buildFullUrl(target.coverFilePath)
                    } else {
                        UrlConfig.getMusicCoverUrl(target.id)
                    }
                    player.playMusic(
                        url,
                        target.id,
                        target.title,
                        target.artist,
                        target.coverFilePath ?: "",
                        fullCoverUrl
                    )
                } else {
                    player.pause()
                }
            }
        }
    }

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

        Text(
            text = stringResource(id = R.string.content_description_drag_reorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            fontSize = 12.sp,
            color = scheme.onSurfaceVariant.copy(alpha = 0.85f)
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
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(ordered, key = { _, m -> m.id }) { index, music ->
                    PlaylistItem(
                        music = music,
                        isPlaying = music.id == currentMusicId,
                        isDragging = draggingIndex == index,
                        dragOffsetY = if (draggingIndex == index) dragOffsetY else 0f,
                        modifier = Modifier.zIndex(if (draggingIndex == index) 1f else 0f),
                        onPlayClick = { onMusicClick(music) },
                        onRemoveClick = { removeFromQueue(music) },
                        onReorderDragStart = {
                            draggingIndex = index
                            dragOffsetY = 0f
                        },
                        onReorderDrag = { dy ->
                            dragOffsetY += dy
                            while (true) {
                                val i = draggingIndex ?: break
                                if (dragOffsetY > itemHeightPx / 2f && i < ordered.lastIndex) {
                                    Collections.swap(ordered, i, i + 1)
                                    draggingIndex = i + 1
                                    dragOffsetY -= itemHeightPx
                                } else if (dragOffsetY < -itemHeightPx / 2f && i > 0) {
                                    Collections.swap(ordered, i, i - 1)
                                    draggingIndex = i - 1
                                    dragOffsetY += itemHeightPx
                                } else {
                                    break
                                }
                            }
                        },
                        onReorderDragEnd = {
                            val ids = ordered.map { it.id }
                            scope.launch {
                                playlistManager.applyQueueOrder(ids)
                                draggingIndex = null
                                dragOffsetY = 0f
                            }
                        },
                        onReorderDragCancel = {
                            draggingIndex = null
                            dragOffsetY = 0f
                        }
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
    isDragging: Boolean,
    dragOffsetY: Float,
    modifier: Modifier = Modifier,
    onPlayClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onReorderDragStart: () -> Unit,
    onReorderDrag: (dy: Float) -> Unit,
    onReorderDragEnd: () -> Unit,
    onReorderDragCancel: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()
    val rowGlass = LiquidGlassDefaults.playlistQueueRow

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffsetY.roundToInt()) },
        shape = RoundedCornerShape(12.dp),
        backgroundAlpha = rowGlass.backgroundAlpha(isPlaying, isDark),
        borderAlpha = rowGlass.borderAlpha(isPlaying, isDark),
        highlightAlpha = rowGlass.highlightAlpha(isPlaying, isDark),
        borderColor = if (isDark) SakuraPink.copy(alpha = rowGlass.darkBorderSakuraAlpha) else scheme.outline,
        liquidBlur = rowGlass.liquid.blur,
        liquidLensHeight = rowGlass.liquid.lensHeight,
        liquidLensAmount = rowGlass.liquid.lensAmount
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .pointerInput(music.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onReorderDragStart() },
                            onDrag = { _, dragAmount -> onReorderDrag(dragAmount.y) },
                            onDragEnd = { onReorderDragEnd() },
                            onDragCancel = { onReorderDragCancel() }
                        )
                    }
                    .clickable(
                        interactionSource = remember(music.id) { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                        onClick = onPlayClick
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaylistItemRow(
                    music = music,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(id = R.string.playback_queue_remove_from_list),
                    tint = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
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
        modifier = modifier,
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
