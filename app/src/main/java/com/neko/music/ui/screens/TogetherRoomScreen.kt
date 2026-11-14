package com.neko.music.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.data.api.TogetherApi
import com.neko.music.data.api.TogetherEvent
import com.neko.music.data.api.TogetherPlayback
import com.neko.music.data.api.TogetherPlaylist
import com.neko.music.data.api.TogetherPlaylistItem
import com.neko.music.data.api.TogetherTrack
import com.neko.music.data.manager.TokenManager
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.neko.music.service.PlayMode
import com.neko.music.ui.components.AppPageBackgroundImage
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LiquidGlassSlider
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.PlaylistDetailLiquidGlassParams
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.isLiquidGlassEffectActive
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.util.UrlConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.util.UUID
import kotlin.math.abs

private data class TogetherUiState(
    val token: String,
    val ownerUserId: Int = 0,
    val memberCount: Int = 0,
    val maxMembers: Int = 10,
    val members: List<com.neko.music.data.api.TogetherMember> = emptyList(),
    val playlist: TogetherPlaylist = TogetherPlaylist(),
    val playback: TogetherPlayback = TogetherPlayback(),
)

/**
 * 安卓一起听房间。房间内的播放与临时歌单全部以服务端 WebSocket 广播为准，
 * 客户端只提交操作意图，不在本地直接修改共享列表。
 */
@Composable
fun TogetherRoomScreen(
    roomToken: String,
    isLoggedIn: Boolean,
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val tokenManager = remember { TokenManager(context) }
    val playerManager = remember { MusicPlayerManager.getInstance(context) }
    val api = remember { TogetherApi() }
    val musicApi = remember { MusicApi(context) }

    var activeToken by remember(roomToken) { mutableStateOf(roomToken.trim()) }
    var room by remember { mutableStateOf<TogetherUiState?>(null) }
    var shareUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var outgoing by remember { mutableStateOf<Channel<String>?>(null) }
    var leaving by remember { mutableStateOf(false) }
    var retryNonce by remember { mutableIntStateOf(0) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Music>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var serverOffsetMs by remember { mutableLongStateOf(0L) }
    var displayPositionMs by remember { mutableLongStateOf(0L) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val currentUserId = tokenManager.getUserId()
    val currentItem = room?.playlist?.items?.firstOrNull {
        it.entryId == room?.playback?.currentEntryId
    }

    fun applyEvent(event: TogetherEvent) {
        if (event.serverTime > 0) {
            serverOffsetMs = event.serverTime - System.currentTimeMillis()
        }
        when (event.type) {
            "snapshot" -> {
                room = event.toUiState()
                statusText = ""
            }
            "room.updated" -> room = room?.copy(
                ownerUserId = if (event.ownerUserId > 0) event.ownerUserId else room!!.ownerUserId,
                memberCount = if (event.memberCount > 0) event.memberCount else room!!.memberCount,
                maxMembers = event.maxMembers.takeIf { it > 0 } ?: room!!.maxMembers,
                members = event.members ?: room!!.members,
            )
            "playlist.updated" -> room = room?.copy(
                playlist = event.playlist ?: room!!.playlist,
            )
            "playback.updated" -> room = room?.copy(
                playlist = event.playlist ?: room!!.playlist,
                playback = event.playback ?: room!!.playback,
            )
            "room.closed" -> {
                statusText = context.getString(R.string.together_room_closed)
                leaving = true
            }
            "error" -> {
                statusText = event.message ?: event.code
                    ?: context.getString(R.string.together_operation_failed)
            }
        }
    }

    fun sendControl(action: String, extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) {
        val channel = outgoing ?: return
        val body = buildJsonObject {
            put("type", "control")
            put("action", action)
            put("opId", UUID.randomUUID().toString())
            extra()
        }
        channel.trySend(body.toString())
    }

    fun leaveAndExit(closeRoom: Boolean = false) {
        if (leaving) return
        leaving = true
        val token = activeToken
        val userToken = tokenManager.getToken()
        val leaveApi = TogetherApi()
        if (token.isNotBlank() && !userToken.isNullOrBlank()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                if (closeRoom) leaveApi.closeRoom(token, userToken)
                else leaveApi.leaveRoom(token, userToken)
                leaveApi.close()
            }
        } else {
            leaveApi.close()
        }
        outgoing?.close()
        api.close()
        onBackClick()
    }

    LaunchedEffect(activeToken, isLoggedIn, retryNonce) {
        val userToken = tokenManager.getToken()
        if (activeToken.isBlank() || !isLoggedIn || userToken.isNullOrBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        statusText = ""
        val joined = api.joinRoom(activeToken, userToken)
        if (joined.isFailure) {
            loading = false
            statusText = joined.exceptionOrNull()?.message
                ?: context.getString(R.string.together_join_failed)
            return@LaunchedEffect
        }
        applyEvent(joined.getOrThrow().room)
        shareUrl = UrlConfig.getTogetherRoomUrl(activeToken)
        loading = false

        while (isActive && !leaving) {
            val ticket = api.issueTicket(activeToken, userToken)
            if (ticket.isFailure) {
                statusText = ticket.exceptionOrNull()?.message
                    ?: context.getString(R.string.together_connect_failed)
                delay(2_000)
                continue
            }
            val channel = Channel<String>(Channel.UNLIMITED)
            outgoing = channel
            connected = true
            try {
                api.runSocket(ticket.getOrThrow(), channel) { event -> applyEvent(event) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                statusText = e.message ?: context.getString(R.string.together_connect_failed)
            } finally {
                channel.close()
                outgoing = null
                connected = false
            }
            if (!leaving) delay(2_000)
        }
    }

    LaunchedEffect(room?.playback?.currentEntryId, room?.playback?.playing,
        room?.playback?.effectiveAt, room?.playback?.positionMs, room?.playlist?.items) {
        val playback = room?.playback ?: return@LaunchedEffect
        val item = room?.playlist?.items?.firstOrNull { it.entryId == playback.currentEntryId }
        val track = item?.track
        if (track == null) {
            if (playerManager.isPlaying.value) playerManager.pause()
            return@LaunchedEffect
        }

        val nowServer = System.currentTimeMillis() + serverOffsetMs
        val target = if (playback.playing) {
            playback.positionMs + (nowServer - playback.effectiveAt).coerceAtLeast(0L)
        } else {
            playback.positionMs
        }.coerceIn(0L, track.duration.coerceAtLeast(0) * 1000L)

        if (playerManager.currentMusicId.value != track.id) {
            val music = track.toMusic()
            val url = musicApi.getMusicFileUrl(music)
            playerManager.playMusic(
                url,
                track.id,
                track.title,
                track.artist,
                "",
                UrlConfig.getMusicCoverUrl(track.id),
            )
            delay(450)
        }
        if (abs(playerManager.currentPosition.value - target) > 800L) {
            playerManager.seekTo(target)
        }
        if (playerManager.isPlaying.value != playback.playing) {
            playerManager.togglePlayPause()
        }
    }

    LaunchedEffect(room?.playback, serverOffsetMs) {
        while (true) {
            val playback = room?.playback
            val item = room?.playlist?.items?.firstOrNull {
                it.entryId == playback?.currentEntryId
            }
            if (playback != null && item != null) {
                val target = if (playback.playing) {
                    playback.positionMs +
                        (System.currentTimeMillis() + serverOffsetMs - playback.effectiveAt).coerceAtLeast(0L)
                } else {
                    playback.positionMs
                }.coerceIn(0L, item.track.duration.coerceAtLeast(0) * 1000L)
                displayPositionMs = target
                if (!dragging) sliderPosition = target.toFloat()
                if (!dragging && playerManager.currentMusicId.value == item.track.id) {
                    if (abs(playerManager.currentPosition.value - target) > 1_200L) {
                        playerManager.seekTo(target)
                    }
                }
            } else {
                displayPositionMs = 0
                if (!dragging) sliderPosition = 0f
            }
            delay(500)
        }
    }

    LaunchedEffect(showSearch, searchQuery) {
        if (!showSearch || searchQuery.isBlank()) {
            if (!showSearch) searchResults = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        searching = true
        musicApi.searchMusic(searchQuery).fold(
            onSuccess = { searchResults = it },
            onFailure = { searchResults = emptyList() },
        )
        searching = false
    }

    LaunchedEffect(sliderPosition, dragging) {
        if (!dragging || currentItem == null) return@LaunchedEffect
        delay(260)
        dragging = false
        sendControl("playback.seek") { put("positionMs", sliderPosition.toLong()) }
    }

    DisposableEffect(Unit) {
        onDispose {
            leaving = true
            outgoing?.close()
            api.close()
            musicApi.close()
        }
    }

    BackHandler { leaveAndExit() }

    val scheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()
    val liquidGlass = LiquidGlassDefaults.playlistDetail
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop)
        ) {
            AppPageBackgroundImage(modifier = Modifier.fillMaxSize())
            PlaylistPageDarkTintOverlay(enabled = isDark)
        }

        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                TogetherTopBar(
                    title = stringResource(R.string.together_title),
                    shareEnabled = shareUrl.isNotBlank(),
                    canCloseRoom = room?.ownerUserId == currentUserId && room != null,
                    onBack = { leaveAndExit() },
                    onShare = {
                        clipboard.setText(AnnotatedString(shareUrl))
                        Toast.makeText(context, R.string.together_link_copied, Toast.LENGTH_SHORT).show()
                    },
                    onCloseRoom = { leaveAndExit(closeRoom = true) },
                )

                when {
                    !isLoggedIn -> TogetherPageMessage(
                        title = stringResource(R.string.together_login_required),
                        action = stringResource(R.string.together_login),
                        onAction = onLoginClick,
                    )
                    activeToken.isBlank() -> TogetherPageMessage(
                        title = statusText.ifBlank { stringResource(R.string.together_create_hint) },
                        action = stringResource(R.string.together_create_room),
                        enabled = !loading,
                        onAction = {
                            tokenManager.getToken()?.let { userToken ->
                                scope.launch {
                                    loading = true
                                    statusText = ""
                                    api.createRoom(userToken).fold(
                                        onSuccess = { url ->
                                            val token = URI(url).path.substringAfterLast('/')
                                            activeToken = token
                                            shareUrl = url
                                            loading = false
                                        },
                                        onFailure = {
                                            loading = false
                                            statusText = it.message
                                                ?: context.getString(R.string.together_create_failed)
                                        },
                                    )
                                }
                            }
                        },
                    )
                    loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = scheme.primary)
                    }
                    room == null -> TogetherPageMessage(
                        title = statusText.ifBlank { stringResource(R.string.together_join_failed) },
                        action = stringResource(R.string.together_retry),
                        onAction = {
                            if (activeToken.isNotBlank()) {
                                loading = true
                                statusText = ""
                                retryNonce++
                            }
                        },
                    )
                    else -> TogetherRoomContent(
                        room = room!!,
                        currentItem = currentItem,
                        connected = connected,
                        statusText = statusText,
                        displayPositionMs = displayPositionMs,
                        sliderPosition = sliderPosition,
                        liquidGlass = liquidGlass,
                        pageBackdrop = pageBackdrop,
                        onSliderChange = {
                            dragging = true
                            sliderPosition = it
                        },
                        onTogglePlay = {
                            if (room!!.playback.playing) {
                                sendControl("playback.pause")
                            } else {
                                sendControl("playback.play") {
                                    currentItem?.entryId?.let { put("entryId", it) }
                                }
                            }
                        },
                        onPrevious = { sendControl("playback.prev") },
                        onNext = { sendControl("playback.next") },
                        onSelect = { entryId ->
                            sendControl("playback.select") { put("entryId", entryId) }
                        },
                        onRemove = { entryId ->
                            sendControl("playlist.remove") {
                                put("entryId", entryId)
                                put("playlistRevision", room!!.playlist.revision)
                            }
                        },
                        onAddMusic = { showSearch = true },
                    )
                }
            }
        }
    }

    if (showSearch) {
        TogetherSearchSheet(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            results = searchResults,
            searching = searching,
            liquidGlass = liquidGlass,
            onDismiss = { showSearch = false },
            onAdd = { music ->
                sendControl("playlist.add") { put("trackId", music.id) }
                showSearch = false
            },
        )
    }
}

@Composable
private fun TogetherTopBar(
    title: String,
    shareEnabled: Boolean,
    canCloseRoom: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onCloseRoom: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canCloseRoom) {
                TextButton(onClick = onCloseRoom) {
                    Text(stringResource(R.string.together_close_room))
                }
            }
            TextButton(enabled = shareEnabled, onClick = onShare) {
                Text(stringResource(R.string.together_share))
            }
        }
    }
}

@Composable
private fun TogetherPageMessage(
    title: String,
    action: String,
    enabled: Boolean = true,
    onAction: () -> Unit,
) {
    val isDark = isAppDarkTheme()
    val glass = LiquidGlassDefaults.playlistDetail
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 190.dp),
            shape = RoundedCornerShape(24.dp),
            material3Shape = RoundedCornerShape(24.dp),
            material3Container = MaterialTheme.colorScheme.surfaceContainerHigh,
            backgroundAlpha = if (isDark) glass.musicRowBackgroundAlphaDark else glass.musicRowBackgroundAlphaLight,
            borderAlpha = if (isDark) glass.musicRowBorderAlphaDark else glass.musicRowBorderAlphaLight,
            highlightAlpha = if (isDark) glass.musicRowHighlightAlphaDark else glass.musicRowHighlightAlphaLight,
            borderColor = if (isDark) Color.White else MaterialTheme.colorScheme.outline,
            liquidBlur = glass.musicRowLiquidBlur,
            liquidLensHeight = glass.musicRowLiquidLensHeight,
            liquidLensAmount = glass.musicRowLiquidLensAmount,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    enabled = enabled,
                    onClick = onAction,
                    shape = RoundedCornerShape(21.dp),
                ) {
                    Text(action)
                }
            }
        }
    }
}

@Composable
private fun TogetherRoomContent(
    room: TogetherUiState,
    currentItem: TogetherPlaylistItem?,
    connected: Boolean,
    statusText: String,
    displayPositionMs: Long,
    sliderPosition: Float,
    liquidGlass: PlaylistDetailLiquidGlassParams,
    pageBackdrop: com.kyant.backdrop.Backdrop,
    onSliderChange: (Float) -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAddMusic: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()
    val useLiquid = isLiquidGlassEffectActive(LocalLiquidLayerBackdrop.current)
    val coverUrl = currentItem?.track?.let { UrlConfig.getMusicCoverUrl(it.id) }
    val durationMs = (currentItem?.track?.duration ?: 0) * 1000L

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = stringResource(R.string.cover),
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.music),
                error = painterResource(R.drawable.music),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.together_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (useLiquid && isDark) Color.White else scheme.onSurface,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = stringResource(
                        R.string.together_member_count_format,
                        room.memberCount,
                        room.maxMembers,
                    ) + if (connected) "" else stringResource(R.string.together_reconnecting),
                    fontSize = 13.sp,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = room.members.joinToString(" · ") { it.nickname },
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (statusText.isNotBlank()) {
            Text(
                text = statusText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                color = scheme.error,
                fontSize = 12.sp,
            )
        }

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            material3Shape = RoundedCornerShape(24.dp),
            material3Container = scheme.surfaceContainerHigh,
            backgroundAlpha = if (isDark) {
                liquidGlass.musicRowBackgroundAlphaDark
            } else {
                liquidGlass.musicRowBackgroundAlphaLight
            },
            borderAlpha = if (isDark) {
                liquidGlass.musicRowBorderAlphaDark
            } else {
                liquidGlass.musicRowBorderAlphaLight
            },
            highlightAlpha = if (isDark) {
                liquidGlass.musicRowHighlightAlphaDark
            } else {
                liquidGlass.musicRowHighlightAlphaLight
            },
            borderColor = if (isDark) Color.White else scheme.outline,
            liquidBlur = liquidGlass.musicRowLiquidBlur,
            liquidLensHeight = liquidGlass.musicRowLiquidLensHeight,
            liquidLensAmount = liquidGlass.musicRowLiquidLensAmount,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = currentItem?.track?.title
                        ?: stringResource(R.string.together_playlist_empty),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Text(
                    text = currentItem?.track?.artist.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (currentItem != null) {
                    LiquidGlassSlider(
                        value = sliderPosition,
                        onValueChange = onSliderChange,
                        valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                        backdrop = pageBackdrop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatTime(displayPositionMs),
                            fontSize = 10.sp,
                            color = scheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatTime(durationMs),
                            fontSize = 10.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                PlaybackControls(
                    isPlaying = room.playback.playing,
                    isLoading = false,
                    musicFileUrl = currentItem?.let { "room:${it.entryId}" },
                    playMode = when (room.playlist.repeatMode) {
                        "single_repeat" -> PlayMode.SINGLE_LOOP
                        "shuffle" -> PlayMode.SHUFFLE
                        else -> PlayMode.LIST_LOOP
                    },
                    isFavorite = false,
                    compact = true,
                    showPlayMode = false,
                    showFavorite = false,
                    onPlayPauseClick = onTogglePlay,
                    onPreviousClick = onPrevious,
                    onNextClick = onNext,
                    onFavoriteClick = {},
                    onPlayModeClick = {},
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.together_temporary_playlist),
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Button(
                onClick = onAddMusic,
                modifier = Modifier.height(38.dp),
                shape = RoundedCornerShape(19.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (useLiquid) {
                        RoseRed.copy(alpha = 0.16f)
                    } else {
                        scheme.secondaryContainer
                    },
                    contentColor = if (useLiquid) RoseRed else scheme.onSecondaryContainer,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.together_add_music), fontSize = 13.sp)
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(room.playlist.items, key = { _, item -> item.entryId }) { index, item ->
                TogetherTrackRow(
                    index = index,
                    item = item,
                    selected = item.entryId == room.playlist.currentEntryId,
                    liquidGlass = liquidGlass,
                    onSelect = { onSelect(item.entryId) },
                    onRemove = { onRemove(item.entryId) },
                )
            }
            if (room.playlist.items.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.together_playlist_empty),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        textAlign = TextAlign.Center,
                        color = scheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TogetherTrackRow(
    index: Int,
    item: TogetherPlaylistItem,
    selected: Boolean,
    liquidGlass: PlaylistDetailLiquidGlassParams,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(liquidGlass.musicRowCornerDp),
        material3Shape = RoundedCornerShape(16.dp),
        material3Container = scheme.surfaceContainerLow,
        backgroundAlpha = if (selected) {
            if (isDark) 0.20f else 0.14f
        } else if (isDark) {
            liquidGlass.musicRowBackgroundAlphaDark
        } else {
            liquidGlass.musicRowBackgroundAlphaLight
        },
        borderAlpha = if (selected) 0.36f else if (isDark) {
            liquidGlass.musicRowBorderAlphaDark
        } else {
            liquidGlass.musicRowBorderAlphaLight
        },
        highlightAlpha = if (isDark) {
            liquidGlass.musicRowHighlightAlphaDark
        } else {
            liquidGlass.musicRowHighlightAlphaLight
        },
        borderColor = if (selected) RoseRed.copy(alpha = 0.7f) else if (isDark) Color.White else scheme.outline,
        liquidBlur = liquidGlass.musicRowLiquidBlur,
        liquidLensHeight = liquidGlass.musicRowLiquidLensHeight,
        liquidLensAmount = liquidGlass.musicRowLiquidLensAmount,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${index + 1}",
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.Center,
                color = if (selected) RoseRed else scheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            AsyncImage(
                model = UrlConfig.getMusicCoverUrl(item.track.id),
                contentDescription = stringResource(R.string.cover),
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.music),
                error = painterResource(R.drawable.music),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.track.title,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) RoseRed else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.track.artist,
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.together_remove),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun TogetherSearchSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Music>,
    searching: Boolean,
    liquidGlass: PlaylistDetailLiquidGlassParams,
    onDismiss: () -> Unit,
    onAdd: (Music) -> Unit,
) {
    val isDark = isAppDarkTheme()
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.46f))
            .clickable(onClick = onDismiss)
    ) {
        GlassSurface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(min = 520.dp, max = 680.dp)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            material3Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            material3Container = scheme.surfaceContainerHigh,
            backgroundAlpha = if (isDark) {
                liquidGlass.musicRowBackgroundAlphaDark
            } else {
                liquidGlass.musicRowBackgroundAlphaLight
            },
            borderAlpha = if (isDark) {
                liquidGlass.musicRowBorderAlphaDark
            } else {
                liquidGlass.musicRowBorderAlphaLight
            },
            highlightAlpha = if (isDark) {
                liquidGlass.musicRowHighlightAlphaDark
            } else {
                liquidGlass.musicRowHighlightAlphaLight
            },
            borderColor = if (isDark) Color.White else scheme.outline,
            liquidBlur = liquidGlass.musicRowLiquidBlur,
            liquidLensHeight = liquidGlass.musicRowLiquidLensHeight,
            liquidLensAmount = liquidGlass.musicRowLiquidLensAmount,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.together_add_music),
                        modifier = Modifier.weight(1f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
                PlaylistSearchBar(
                    query = query,
                    onQueryChange = onQueryChange,
                    liquidGlass = liquidGlass,
                    placeholder = stringResource(R.string.together_search_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                if (searching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = scheme.primary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(results, key = { it.id }) { music ->
                            GlassSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .clickable { onAdd(music) },
                                shape = RoundedCornerShape(14.dp),
                                material3Shape = RoundedCornerShape(14.dp),
                                material3Container = scheme.surfaceContainerLow,
                                backgroundAlpha = if (isDark) {
                                    liquidGlass.musicRowBackgroundAlphaDark
                                } else {
                                    liquidGlass.musicRowBackgroundAlphaLight
                                },
                                borderAlpha = if (isDark) {
                                    liquidGlass.musicRowBorderAlphaDark
                                } else {
                                    liquidGlass.musicRowBorderAlphaLight
                                },
                                highlightAlpha = if (isDark) {
                                    liquidGlass.musicRowHighlightAlphaDark
                                } else {
                                    liquidGlass.musicRowHighlightAlphaLight
                                },
                                borderColor = if (isDark) Color.White else scheme.outline,
                                liquidBlur = liquidGlass.musicRowLiquidBlur,
                                liquidLensHeight = liquidGlass.musicRowLiquidLensHeight,
                                liquidLensAmount = liquidGlass.musicRowLiquidLensAmount,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            music.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            music.artist,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = scheme.onSurfaceVariant,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = stringResource(R.string.together_add_music),
                                        tint = RoseRed,
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

private fun TogetherEvent.toUiState(): TogetherUiState = TogetherUiState(
    token = token.orEmpty(),
    ownerUserId = ownerUserId,
    memberCount = memberCount,
    maxMembers = maxMembers,
    members = members.orEmpty(),
    playlist = playlist ?: TogetherPlaylist(),
    playback = playback ?: TogetherPlayback(),
)

private fun TogetherTrack.toMusic(): Music = Music(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    maxQuality = maxQuality,
)
