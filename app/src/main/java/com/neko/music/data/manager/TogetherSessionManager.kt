package com.neko.music.data.manager

import android.content.Context
import android.util.Log
import com.neko.music.data.api.MusicApi
import com.neko.music.data.api.TogetherApi
import com.neko.music.data.api.TogetherEvent
import com.neko.music.data.api.TogetherPlayback
import com.neko.music.data.api.TogetherPlaylist
import com.neko.music.data.api.TogetherPlaylistItem
import com.neko.music.data.api.TogetherTrack
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.neko.music.service.RoomPlaybackDelegate
import com.neko.music.util.UrlConfig
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class TogetherRoomViewState(
    val token: String,
    val ownerUserId: Int = 0,
    val memberCount: Int = 0,
    val maxMembers: Int = 10,
    val members: List<com.neko.music.data.api.TogetherMember> = emptyList(),
    val playlist: TogetherPlaylist = TogetherPlaylist(),
    val playback: TogetherPlayback = TogetherPlayback(),
) {
    val currentItem: TogetherPlaylistItem?
        get() = playlist.items.firstOrNull { it.entryId == playback.currentEntryId }
}

/**
 * 全局一起听会话。
 *
 * 房间不再拥有独立页面和独立播放器。WebSocket 状态在这里持续存在，现有
 * PlayerScreen / 系统播放器始终是唯一播放引擎，播放页只把控制意图转成 WS 操作。
 */
object TogetherSessionManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<TogetherRoomViewState?>(null)
    private val _connected = MutableStateFlow(false)
    private val _lastError = MutableStateFlow<String?>(null)

    val state: StateFlow<TogetherRoomViewState?> = _state.asStateFlow()
    val connected: StateFlow<Boolean> = _connected.asStateFlow()
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var appContext: Context? = null
    private var api: TogetherApi? = null
    private var musicApi: MusicApi? = null
    private var playerManager: MusicPlayerManager? = null
    private var connectionJob: Job? = null
    private var syncJob: Job? = null
    private var outgoing: Channel<String>? = null
    private var closing = false
    private var serverOffsetMs = 0L
    private var bestClockRttMs = Long.MAX_VALUE
    private val clockSentAt = ConcurrentHashMap<String, Long>()

    val isActive: Boolean
        get() = _state.value != null

    fun roomUrl(): String? = _state.value?.token?.let(UrlConfig::getTogetherRoomUrl)

    private fun ensure(context: Context) {
        if (appContext == null) {
            val app = context.applicationContext
            appContext = app
            api = TogetherApi()
            musicApi = MusicApi(app)
            playerManager = MusicPlayerManager.getInstance(app)
        }
    }

    private fun installPlaybackDelegate() {
        playerManager?.setRoomPlaybackDelegate(object : RoomPlaybackDelegate {
            override fun playTrack(musicId: Int) {
                sendControl("playback.playTrack") { put("trackId", musicId) }
            }

            override fun playTracks(musicIds: List<Int>, startIndex: Int) {
                sendControl("playlist.playList") {
                    put("startIndex", startIndex)
                    put("trackIds", buildJsonArray {
                        musicIds.forEach { add(it) }
                    })
                }
            }

            override fun playNext(musicId: Int) {
                sendControl("playlist.addNext") { put("trackId", musicId) }
            }

            override fun toggle() = TogetherSessionManager.toggle()
            override fun pause() = sendControl("playback.pause")
            override fun next() = TogetherSessionManager.next()
            override fun previous() = TogetherSessionManager.previous()
            override fun seekTo(positionMs: Long) =
                TogetherSessionManager.seekTo(positionMs)
            override fun togglePlayMode() = TogetherSessionManager.cyclePlayMode()
        })
    }

    /**
     * 从播放页分享菜单创建房间，并把当前歌曲加入空临时列表后开始播放。
     * 返回可分享的房间 URL。
     */
    suspend fun startWithCurrentMusic(
        context: Context,
        userToken: String,
        music: Music,
    ): Result<String> = runCatching {
        ensure(context)
        stopInternal(leaveServer = true)
        installPlaybackDelegate()
        closing = false
        val created = api!!.createRoom(userToken).getOrThrow()
        val roomToken = URI(created).path.substringAfterLast('/')
        connect(roomToken, userToken)
        addMusic(music)
        sendControl("playback.play")
        created
    }.onFailure { stopInternal(leaveServer = false) }

    /** 分享链接进入房间；不创建独立页面，现有播放页继续作为唯一操控入口。 */
    suspend fun join(
        context: Context,
        userToken: String,
        roomToken: String,
    ): Result<Unit> = runCatching {
        ensure(context)
        stopInternal(leaveServer = false)
        installPlaybackDelegate()
        closing = false
        connect(roomToken, userToken)
    }.onFailure { stopInternal(leaveServer = false) }

    fun addMusic(music: Music) {
        if (!isActive || music.id <= 0) return
        sendControl("playlist.add") { put("trackId", music.id) }
    }

    fun toggle() {
        val playback = _state.value?.playback ?: return
        if (playback.playing) {
            sendControl("playback.pause")
        } else {
            sendControl("playback.play") {
                _state.value?.playback?.currentEntryId?.let { put("entryId", it) }
            }
        }
    }

    fun next() {
        sendControl("playback.next")
    }

    fun previous() {
        sendControl("playback.prev")
    }

    fun seekTo(positionMs: Long) {
        sendControl("playback.seek") { put("positionMs", positionMs.coerceAtLeast(0L)) }
    }

    fun cyclePlayMode() {
        val next = when (_state.value?.playlist?.repeatMode) {
            "list_repeat" -> "single_repeat"
            "single_repeat" -> "shuffle"
            else -> "list_repeat"
        }
        sendControl("playlist.mode") { put("mode", next) }
    }

    fun select(entryId: String) {
        sendControl("playback.select") { put("entryId", entryId) }
    }

    fun remove(entryId: String) {
        val revision = _state.value?.playlist?.revision ?: return
        sendControl("playlist.remove") {
            put("entryId", entryId)
            put("playlistRevision", revision)
        }
    }

    fun leave() {
        stopInternal(leaveServer = true)
    }

    private suspend fun connect(roomToken: String, userToken: String) {
        bestClockRttMs = Long.MAX_VALUE
        serverOffsetMs = 0L
        clockSentAt.clear()
        val joined = api!!.joinRoom(roomToken, userToken).getOrThrow()
        applyEvent(joined.room)
        val channel = Channel<String>(Channel.UNLIMITED)
        outgoing = channel
        connectionJob?.cancel()
        connectionJob = scope.launch {
            var currentTicket = joined.ticket
            val clockJob = launch {
                while (isActive && !closing) {
                    sendClockPing()
                    delay(15_000)
                }
            }
            try {
                while (isActive && !closing) {
                    _connected.value = true
                    try {
                        api!!.runSocket(currentTicket, channel) { event -> applyEvent(event) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _lastError.value = e.message
                        Log.w("TogetherSession", "WebSocket disconnected", e)
                    } finally {
                        _connected.value = false
                    }
                    if (!isActive || closing) break
                    delay(2_000)
                    currentTicket = api!!.issueTicket(roomToken, userToken).getOrElse {
                        _lastError.value = it.message
                        delay(2_000)
                        return@launch
                    }
                }
            } finally {
                clockJob.cancel()
            }
        }
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive && !closing) {
                syncPlayer()
                delay(1_500)
            }
        }
    }

    private fun applyEvent(event: TogetherEvent) {
        if (event.serverTime > 0) {
            serverOffsetMs = event.serverTime - System.currentTimeMillis()
        }
        when (event.type) {
            "snapshot" -> _state.value = event.toUiState()
            "room.updated" -> _state.value?.let { current ->
                _state.value = current.copy(
                    ownerUserId = event.ownerUserId.takeIf { it > 0 } ?: current.ownerUserId,
                    memberCount = event.memberCount.takeIf { it > 0 } ?: current.memberCount,
                    maxMembers = event.maxMembers.takeIf { it > 0 } ?: current.maxMembers,
                    members = event.members ?: current.members,
                )
            }
            "playlist.updated" -> _state.value?.let { current ->
                _state.value = current.copy(playlist = event.playlist ?: current.playlist)
            }
            "playback.updated" -> _state.value?.let { current ->
                _state.value = current.copy(
                    playlist = event.playlist ?: current.playlist,
                    playback = event.playback ?: current.playback,
                )
            }
            "clock" -> {
                applyClockSample(event)
            }
            "room.closed" -> {
                _lastError.value = event.message ?: "房间已关闭"
                stopInternal(leaveServer = false)
            }
            "error" -> _lastError.value = event.message ?: event.code
        }
        scope.launch { syncPlayer() }
    }

    private suspend fun syncPlayer() {
        val current = _state.value ?: return
        val player = playerManager ?: return
        val item = current.currentItem
        val track = item?.track
        if (track == null) {
            if (player.isPlaying.value) {
                player.applyRoomPlayback { player.pause() }
            }
            return
        }
        if (player.currentMusicId.value != track.id) {
            val music = track.toMusic()
            val url = musicApi!!.getMusicFileUrl(music)
            player.applyRoomPlayback {
                player.playMusic(
                    url,
                    track.id,
                    track.title,
                    track.artist,
                    "",
                    UrlConfig.getMusicCoverUrl(track.id),
                )
            }
            for (attempt in 0 until 20) {
                if (player.currentMusicId.value == track.id && player.duration.value > 0) {
                    break
                }
                delay(100)
            }
        }
        // 切歌/加载会耗时，必须拿最新的房间状态重新计算目标进度，不能复用加载前的值。
        val latest = _state.value ?: return
        val latestItem = latest.currentItem ?: return
        val nowServer = System.currentTimeMillis() + serverOffsetMs
        val target = if (latest.playback.playing) {
            latest.playback.positionMs +
                (nowServer - latest.playback.effectiveAt).coerceAtLeast(0L)
        } else {
            latest.playback.positionMs
        }.coerceIn(0L, latestItem.track.duration.coerceAtLeast(0) * 1000L)

        if (abs(player.currentPosition.value - target) > 500L) {
            player.applyRoomPlayback { player.seekTo(target) }
        }
        if (player.isPlaying.value != latest.playback.playing) {
            player.applyRoomPlayback { player.togglePlayPause() }
        }
    }

    private fun sendClockPing() {
        val channel = outgoing ?: return
        val requestId = UUID.randomUUID().toString()
        val sentAt = System.currentTimeMillis()
        clockSentAt[requestId] = sentAt
        if (clockSentAt.size > 8) {
            clockSentAt.keys.firstOrNull()?.let(clockSentAt::remove)
        }
        channel.trySend(
            buildJsonObject {
                put("type", "clock")
                put("requestId", requestId)
                put("clientTime", sentAt)
            }.toString()
        )
    }

    private fun applyClockSample(event: TogetherEvent) {
        val requestId = event.requestId ?: return
        val sentAt = clockSentAt.remove(requestId) ?: return
        val receivedAt = System.currentTimeMillis()
        val rtt = (receivedAt - sentAt).coerceAtLeast(0L)
        val sampleOffset = event.serverTime - (sentAt + rtt / 2)
        if (rtt <= bestClockRttMs || rtt <= bestClockRttMs + 20L) {
            bestClockRttMs = minOf(bestClockRttMs, rtt)
            serverOffsetMs = sampleOffset
            scope.launch { syncPlayer() }
        }
    }

    private fun sendControl(action: String, extra: JsonObjectBuilder.() -> Unit = {}) {
        val channel = outgoing ?: return
        val body = buildJsonObject {
            put("type", "control")
            put("action", action)
            put("opId", UUID.randomUUID().toString())
            extra()
        }
        channel.trySend(body.toString())
    }

    private fun stopInternal(leaveServer: Boolean) {
        closing = true
        val token = _state.value?.token
        val userToken = appContext?.let { TokenManager(it).getToken() }
        if (leaveServer && token != null && userToken != null) {
            val leaveApi = TogetherApi()
            scope.launch {
                try {
                    leaveApi.leaveRoom(token, userToken)
                } finally {
                    leaveApi.close()
                }
            }
        }
        outgoing?.close()
        outgoing = null
        connectionJob?.cancel()
        connectionJob = null
        syncJob?.cancel()
        syncJob = null
        _connected.value = false
        _state.value = null
        playerManager?.setRoomPlaybackDelegate(null)
        closing = false
    }

    private fun TogetherEvent.toUiState(): TogetherRoomViewState = TogetherRoomViewState(
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
}
