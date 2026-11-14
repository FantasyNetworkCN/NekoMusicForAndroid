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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.neko.music.util.UrlConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
@OptIn(ExperimentalMaterial3Api::class)
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

    DisposableEffect(Unit) {
        onDispose {
            leaving = true
            outgoing?.close()
            api.close()
            musicApi.close()
        }
    }

    BackHandler { leaveAndExit() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.together_title)) },
                navigationIcon = {
                    TextButton(onClick = { leaveAndExit() }) {
                        Text("‹", fontSize = 28.sp)
                    }
                },
                actions = {
                    if (room?.ownerUserId == currentUserId && room != null) {
                        TextButton(onClick = { leaveAndExit(closeRoom = true) }) {
                            Text(stringResource(R.string.together_close_room))
                        }
                    }
                    TextButton(
                        enabled = shareUrl.isNotBlank(),
                        onClick = {
                            clipboard.setText(AnnotatedString(shareUrl))
                            Toast.makeText(context, R.string.together_link_copied, Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text(stringResource(R.string.together_share))
                    }
                },
            )
        },
        floatingActionButton = {
            if (room != null) {
                FloatingActionButton(onClick = { showSearch = true }) {
                    Text("+", fontSize = 28.sp)
                }
            }
        },
    ) { padding ->
        when {
            !isLoggedIn -> TogetherCenteredMessage(
                modifier = Modifier.padding(padding),
                title = stringResource(R.string.together_login_required),
                action = stringResource(R.string.together_login),
                onAction = onLoginClick,
            )
            activeToken.isBlank() -> TogetherCenteredMessage(
                modifier = Modifier.padding(padding),
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
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            room == null -> TogetherCenteredMessage(
                modifier = Modifier.padding(padding),
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
                modifier = Modifier.padding(padding),
                room = room!!,
                currentItem = currentItem,
                connected = connected,
                statusText = statusText,
                sliderPosition = sliderPosition,
                onSliderChange = {
                    dragging = true
                    sliderPosition = it
                },
                onSliderFinished = {
                    dragging = false
                    sendControl("playback.seek") { put("positionMs", sliderPosition.toLong()) }
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
            )
        }
    }

    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text(stringResource(R.string.together_add_music)) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.together_search_hint)) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = !searching && searchQuery.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    searching = true
                                    musicApi.searchMusic(searchQuery).fold(
                                        onSuccess = { searchResults = it },
                                        onFailure = { searchResults = emptyList() },
                                    )
                                    searching = false
                                }
                            },
                        ) {
                            Text(stringResource(R.string.together_search))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (searching) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(searchResults, key = { it.id }) { music ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            sendControl("playlist.add") { put("trackId", music.id) }
                                            showSearch = false
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                ) {
                                    Text(music.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        music.artist,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSearch = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun TogetherCenteredMessage(
    modifier: Modifier,
    title: String,
    action: String,
    enabled: Boolean = true,
    onAction: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(enabled = enabled, onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun TogetherRoomContent(
    modifier: Modifier,
    room: TogetherUiState,
    currentItem: TogetherPlaylistItem?,
    connected: Boolean,
    statusText: String,
    sliderPosition: Float,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(
                R.string.together_member_count_format,
                room.memberCount,
                room.maxMembers,
            ) + if (connected) "" else stringResource(R.string.together_reconnecting),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        if (statusText.isNotBlank()) {
            Text(
                text = statusText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
            )
        }
        Text(
            text = room.members.joinToString(" · ") { it.nickname },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )

        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp),
        ) {
            Text(
                text = currentItem?.track?.title ?: stringResource(R.string.together_playlist_empty),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                text = currentItem?.track?.artist ?: "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Slider(
                value = sliderPosition,
                onValueChange = onSliderChange,
                onValueChangeFinished = onSliderFinished,
                valueRange = 0f..((currentItem?.track?.duration ?: 0) * 1000f).coerceAtLeast(1f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPrevious) { Text("⏮") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = onTogglePlay, enabled = currentItem != null) {
                    Text(if (room.playback.playing) "⏸" else "▶")
                }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = onNext, enabled = room.playlist.items.isNotEmpty()) {
                    Text("⏭")
                }
            }
        }

        Text(
            text = stringResource(R.string.together_temporary_playlist),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            fontWeight = FontWeight.SemiBold,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            items(room.playlist.items, key = { it.entryId }) { item ->
                val selected = item.entryId == room.playlist.currentEntryId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(item.entryId) }
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.track.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            item.track.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    TextButton(onClick = { onRemove(item.entryId) }) {
                        Text(stringResource(R.string.together_remove))
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
