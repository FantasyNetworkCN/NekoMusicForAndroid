package com.neko.music.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.asDrawable
import androidx.core.graphics.drawable.toBitmap
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.neko.music.data.manager.PlaylistManager
import com.neko.music.data.model.Music
import com.neko.music.ui.screens.baseUrl
import com.neko.music.util.UrlConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PlayMode {
    LIST_LOOP,    // 列表循环
    SINGLE_LOOP,  // 单曲循环
    SHUFFLE       // 随机播放
}

class MusicPlayerManager private constructor(context: Context) {
    
    private val playlistManager = PlaylistManager.getInstance(context)
    private val appContext = context.applicationContext
    private val imageLoader = ImageLoader.Builder(appContext)
        .diskCache(null)
        .memoryCache(null)
        .build()
    
    // SharedPreferences 用于持久化播放模式
    private val prefs = appContext.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
    private val KEY_PLAY_MODE = "play_mode"
    
    private val player = ExoPlayer.Builder(context).build().apply {
        // 设置音频属性，确保后台播放
        setAudioAttributes(
            com.google.android.exoplayer2.audio.AudioAttributes.Builder()
                .setContentType(com.google.android.exoplayer2.C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(com.google.android.exoplayer2.C.USAGE_MEDIA)
                .build(),
            true // handleAudioFocus = true
        )
        // 设置唤醒模式，确保播放时 CPU 不会休眠
        setHandleAudioBecomingNoisy(true)
    }
    private val scope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // 播放器永远不会被释放，始终保持活跃状态
    private val isReleased = false

    // WakeLock 用于在播放时保持 CPU 唤醒
    private var wakeLock: PowerManager.WakeLock? = null
    private val wakeLockTag = "NekoMusic:WakeLock"

    // 预加载缓存
    private var preloadedNextMusic: com.neko.music.data.model.Music? = null
    private var preloadedNextMusicUrl: String? = null
    private var preloadedNextMusicFullCoverUrl: String? = null

    // 重试计数器 - 用于跟踪当前音乐的重试次数
    private var retryCount = 0
    private val MAX_RETRY_COUNT = 2 // 最大重试次数

    // 媒体会话 - 延迟初始化
    private var mediaSession: MediaSessionCompat? = null

    // 标记 MediaSession 是否已初始化
    private var isMediaSessionInitialized = false

    // 桌面歌词启用状态
    private var isDesktopLyricEnabled = false

    fun ensureMediaSessionInitialized(serviceContext: Context) {
        if (isMediaSessionInitialized) {
            return
        }

        try {
            // 创建 MediaSession，使用组件名以确保兼容性
            mediaSession = MediaSessionCompat(serviceContext, "MusicPlayerSession")

            // 设置 MediaSession 标志以兼容国产厂商
            mediaSession?.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            // 激活 MediaSession
            mediaSession?.isActive = true

            // 延迟激活以确保系统完全准备好
            // 某些国产厂商 ROM 需要延迟激活
            scope.launch {
                delay(300) // 延迟 300ms
                mediaSession?.isActive = true
                Log.d("MusicPlayerManager", "MediaSession 延迟激活完成")
            }

            isMediaSessionInitialized = true
            Log.d("MusicPlayerManager", "MediaSession 初始化成功")
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "MediaSession 初始化失败", e)
        }
    }

    fun getMediaSessionToken(): android.support.v4.media.session.MediaSessionCompat.Token? {
        return mediaSession?.sessionToken
    }

    fun updateDesktopLyricState(enabled: Boolean) {
        isDesktopLyricEnabled = enabled
        // 更新MediaSession以显示正确的歌词按钮状态
        updatePlaybackState()
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    private val _currentMusicUrl = MutableStateFlow<String?>(null)
    val currentMusicUrl: StateFlow<String?> = _currentMusicUrl.asStateFlow()
    
    private val _currentMusicTitle = MutableStateFlow<String?>(null)
    val currentMusicTitle: StateFlow<String?> = _currentMusicTitle.asStateFlow()
    
    private val _currentMusicArtist = MutableStateFlow<String?>(null)
    val currentMusicArtist: StateFlow<String?> = _currentMusicArtist.asStateFlow()
    
    private val _currentMusicCover = MutableStateFlow<String?>(null)
    val currentMusicCover: StateFlow<String?> = _currentMusicCover.asStateFlow()
    
    private val _currentMusicId = MutableStateFlow<Int?>(null)
    val currentMusicId: StateFlow<Int?> = _currentMusicId.asStateFlow()
    
    // 播放历史记录栈（用于上一曲）
    private val playHistory = mutableListOf<Int>()
    
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private lateinit var context: android.content.Context
    private lateinit var tokenManager: com.neko.music.data.manager.TokenManager
    private lateinit var favoriteApi: com.neko.music.data.api.FavoriteApi

    fun initializeFavoriteManager() {
        context = appContext.applicationContext
        tokenManager = com.neko.music.data.manager.TokenManager(context)
        favoriteApi = com.neko.music.data.api.FavoriteApi(context)
    }

    private val _playMode = MutableStateFlow(
        PlayMode.valueOf(
            prefs.getString(KEY_PLAY_MODE, PlayMode.LIST_LOOP.name) ?: PlayMode.LIST_LOOP.name
        )
    )
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()
    
    private val _playModeChanged = MutableStateFlow(0)
    val playModeChanged: StateFlow<Int> = _playModeChanged.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    fun setPlaybackSpeed(speed: Float) {
        if (isReleased) {
            Log.w("MusicPlayerManager", "ExoPlayer 已释放，忽略 setPlaybackSpeed 操作")
            return
        }
        _playbackSpeed.value = speed
        player.setPlaybackSpeed(speed)
        Log.d("MusicPlayerManager", "播放速度设置为: $speed")
    }

    /**
     * 更新音频属性，根据焦点锁定状态设置是否处理音频焦点
     * @param focusLockEnabled 是否启用焦点锁定
     */
    fun updateAudioAttributes(focusLockEnabled: Boolean) {
        if (isReleased) {
            Log.w("MusicPlayerManager", "ExoPlayer 已释放，忽略 updateAudioAttributes 操作")
            return
        }

        val handleAudioFocus = !focusLockEnabled
        Log.d("MusicPlayerManager", "更新音频属性: 焦点锁定=$focusLockEnabled, 处理音频焦点=$handleAudioFocus")

        // 设置音频属性
        player.setAudioAttributes(
            com.google.android.exoplayer2.audio.AudioAttributes.Builder()
                .setContentType(com.google.android.exoplayer2.C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(com.google.android.exoplayer2.C.USAGE_MEDIA)
                .build(),
            handleAudioFocus
        )
    }

    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var sleepTimerEndTime: Long = 0

    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    private val _sleepTimerRemainingSeconds = MutableStateFlow(0)
    val sleepTimerRemainingSeconds: StateFlow<Int> = _sleepTimerRemainingSeconds.asStateFlow()

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = minutes

        if (minutes > 0) {
            sleepTimerEndTime = System.currentTimeMillis() + minutes * 60 * 1000L

            sleepTimerJob = scope.launch {
                while (true) {
                    val remaining = sleepTimerEndTime - System.currentTimeMillis()
                    if (remaining <= 0) {
                        pause()
                        _sleepTimerMinutes.value = 0
                        _sleepTimerRemainingSeconds.value = 0
                        sleepTimerEndTime = 0
                        Log.d("MusicPlayerManager", "定时关闭已触发")
                        break
                    }
                    _sleepTimerRemainingSeconds.value = (remaining / 1000).toInt()
                    delay(1000)
                }
            }
            Log.d("MusicPlayerManager", "定时关闭设置为: $minutes 分钟")
        } else {
            sleepTimerEndTime = 0
            _sleepTimerRemainingSeconds.value = 0
            Log.d("MusicPlayerManager", "定时关闭已取消")
        }
    }

    fun toggleFavorite() {
        val musicId = currentMusicId.value ?: return

        scope.launch {
            try {
                val currentFavorite = _isFavorite.value
                val token = tokenManager.getToken()
                if (token == null) {
                    Log.e("MusicPlayerManager", "Token 为空，无法切换收藏状态")
                    return@launch
                }

                val success = if (currentFavorite) {
                    // 取消收藏
                    favoriteApi.removeFavorite(token, musicId)
                } else {
                    // 添加收藏
                    favoriteApi.addFavorite(token, musicId)
                }
                _isFavorite.value = !_isFavorite.value
            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "切换收藏状态失败", e)
            }
        }
    }

    /**
     * 检查当前音乐是否已收藏
     */
    fun checkFavoriteStatus() {
        val token = tokenManager.getToken()
        val musicId = currentMusicId.value

        if (token == null || musicId == null) {
            // 未登录或没有音乐，设置为未收藏
            _isFavorite.value = false
            return
        }

        // 调用后端 API 获取收藏列表，检查当前音乐是否在列表中
        scope.launch {
            try {
                val response = favoriteApi.getFavorites(token)
                if (response.success) {
                    val isFav = response.favorites.any { it.id == musicId }
                    _isFavorite.value = isFav
                } else {
                    _isFavorite.value = false
                }
            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "检查收藏状态失败", e)
                _isFavorite.value = false
            }
        }
    }
    
    fun setFavorite(isFavorite: Boolean) {
        _isFavorite.value = isFavorite
        updatePlaybackState()
    }
    
    fun togglePlayMode() {
        _playMode.value = when (_playMode.value) {
            PlayMode.LIST_LOOP -> PlayMode.SINGLE_LOOP
            PlayMode.SINGLE_LOOP -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.LIST_LOOP
        }
        // 保存播放模式到 SharedPreferences
        prefs.edit().putString(KEY_PLAY_MODE, _playMode.value.name).apply()
        _playModeChanged.value++
    }
    
    fun setPlayMode(mode: PlayMode) {
        _playMode.value = mode
        // 保存播放模式到 SharedPreferences
        prefs.edit().putString(KEY_PLAY_MODE, _playMode.value.name).apply()
    }
    
    // 下一曲
    fun next() {
        val currentId = _currentMusicId.value ?: return
        android.util.Log.d("MusicPlayerManager", "next() called, currentId: $currentId, playMode: ${_playMode.value}")

        // 检查是否有预加载的下一首音乐
        if (preloadedNextMusic != null && preloadedNextMusicUrl != null && preloadedNextMusicFullCoverUrl != null) {
            android.util.Log.d("MusicPlayerManager", "使用预加载的下一首音乐: ${preloadedNextMusic!!.title}")
            playMusic(
                preloadedNextMusicUrl!!,
                preloadedNextMusic!!.id,
                preloadedNextMusic!!.title,
                preloadedNextMusic!!.artist,
                preloadedNextMusic!!.coverFilePath ?: "",
                preloadedNextMusicFullCoverUrl!!
            )
            // 清空预加载缓存
            preloadedNextMusic = null
            preloadedNextMusicUrl = null
            preloadedNextMusicFullCoverUrl = null
            return
        }

        // 没有预加载，按正常流程获取下一首
        kotlinx.coroutines.runBlocking {
            when (_playMode.value) {
                PlayMode.LIST_LOOP -> {
                    // 列表循环：播放下一首，如果没有下一首则回到第一首
                    android.util.Log.d("MusicPlayerManager", "LIST_LOOP mode, getting next music")
                    val nextMusic = playlistManager.getNextMusic(currentId)
                    android.util.Log.d("MusicPlayerManager", "nextMusic: $nextMusic")
                    if (nextMusic != null) {
                                                    val fullCoverUrl = if (!nextMusic.coverFilePath.isNullOrEmpty()) {
                                                        if (nextMusic.coverFilePath.startsWith("http")) {
                                                            android.util.Log.d("MusicPlayerManager", "使用完整URL作为封面: ${nextMusic.coverFilePath}")
                                                            nextMusic.coverFilePath
                                                        } else {
                                                            android.util.Log.d("MusicPlayerManager", "拼接URL作为封面: $baseUrl${nextMusic.coverFilePath}")
                                                            UrlConfig.buildFullUrl("${nextMusic.coverFilePath}")
                                                        }
                                                    } else {
                                                        android.util.Log.d("MusicPlayerManager", "使用默认API作为封面: $baseUrl/api/music/cover/${nextMusic.id}")
                                                        UrlConfig.getMusicCoverUrl(nextMusic.id)
                                                    }
                                                    android.util.Log.d("MusicPlayerManager", "最终封面URL: $fullCoverUrl")
                                                    // 使用 MusicApi 获取正确的播放 URL（包括缓存逻辑）
                                                    val musicApi = com.neko.music.data.api.MusicApi(context)
                                                    val musicUrl = musicApi.getMusicFileUrl(nextMusic)
                                                    playMusic(musicUrl, nextMusic.id, nextMusic.title, nextMusic.artist, nextMusic.coverFilePath ?: "", fullCoverUrl)
                    } else {
                        // 没有下一首，回到第一首
                        android.util.Log.d("MusicPlayerManager", "No next music found, getting first music")
                        val firstMusic = playlistManager.getFirstMusic()
                        android.util.Log.d("MusicPlayerManager", "firstMusic: $firstMusic")
                        if (firstMusic != null) {
                            val fullCoverUrl = if (!firstMusic.coverFilePath.isNullOrEmpty()) {
                                UrlConfig.buildFullUrl("${firstMusic.coverFilePath}")
                            } else {
                                UrlConfig.getMusicCoverUrl(firstMusic.id)
                            }
                            // 使用 MusicApi 获取正确的播放 URL（包括缓存逻辑）
                            val musicApi = com.neko.music.data.api.MusicApi(context)
                            val musicUrl = musicApi.getMusicFileUrl(firstMusic)
                            playMusic(musicUrl, firstMusic.id, firstMusic.title, firstMusic.artist, firstMusic.coverFilePath ?: "", fullCoverUrl)
                        }
                    }
                }
                PlayMode.SINGLE_LOOP -> {
                    // 单曲循环：用户手动点击下一首时，可以切换到下一首歌曲
                    // 只有在播放结束时才自动循环当前歌曲
                    android.util.Log.d("MusicPlayerManager", "SINGLE_LOOP mode, getting next music")
                    val nextMusic = playlistManager.getNextMusic(currentId)
                    android.util.Log.d("MusicPlayerManager", "nextMusic: $nextMusic")
                    if (nextMusic != null) {
                        val fullCoverUrl = if (!nextMusic.coverFilePath.isNullOrEmpty()) {
                            if (nextMusic.coverFilePath.startsWith("http")) {
                                android.util.Log.d("MusicPlayerManager", "使用完整URL作为封面: ${nextMusic.coverFilePath}")
                                nextMusic.coverFilePath
                            } else {
                                android.util.Log.d("MusicPlayerManager", "拼接URL作为封面: $baseUrl${nextMusic.coverFilePath}")
                                UrlConfig.buildFullUrl("${nextMusic.coverFilePath}")
                            }
                        } else {
                            android.util.Log.d("MusicPlayerManager", "使用默认API作为封面: $baseUrl/api/music/cover/${nextMusic.id}")
                            UrlConfig.getMusicCoverUrl(nextMusic.id)
                        }
                        android.util.Log.d("MusicPlayerManager", "最终封面URL: $fullCoverUrl")
                        // 使用 MusicApi 获取正确的播放 URL（包括缓存逻辑）
                        val musicApi = com.neko.music.data.api.MusicApi(context)
                        val musicUrl = musicApi.getMusicFileUrl(nextMusic)
                        playMusic(musicUrl, nextMusic.id, nextMusic.title, nextMusic.artist, nextMusic.coverFilePath ?: "", fullCoverUrl)
                    } else {
                        // 没有下一首，回到第一首
                        android.util.Log.d("MusicPlayerManager", "No next music found, getting first music")
                        val firstMusic = playlistManager.getFirstMusic()
                        android.util.Log.d("MusicPlayerManager", "firstMusic: $firstMusic")
                        if (firstMusic != null) {
                            val fullCoverUrl = if (!firstMusic.coverFilePath.isNullOrEmpty()) {
                                UrlConfig.buildFullUrl("${firstMusic.coverFilePath}")
                            } else {
                                UrlConfig.getMusicCoverUrl(firstMusic.id)
                            }
                            // 使用 MusicApi 获取正确的播放 URL（包括缓存逻辑）
                            val musicApi = com.neko.music.data.api.MusicApi(context)
                            val musicUrl = musicApi.getMusicFileUrl(firstMusic)
                            playMusic(musicUrl, firstMusic.id, firstMusic.title, firstMusic.artist, firstMusic.coverFilePath ?: "", fullCoverUrl)
                        }
                    }
                }
                PlayMode.SHUFFLE -> {
                    // 随机播放：随机选择一首不同的歌曲
                    android.util.Log.d("MusicPlayerManager", "SHUFFLE mode, getting random music")
                    val randomMusic = playlistManager.getRandomMusic(currentId)
                    android.util.Log.d("MusicPlayerManager", "randomMusic: $randomMusic")
                    if (randomMusic != null) {
                        val fullCoverUrl = if (!randomMusic.coverFilePath.isNullOrEmpty()) {
                            UrlConfig.buildFullUrl("${randomMusic.coverFilePath}")
                        } else {
                            UrlConfig.getMusicCoverUrl(randomMusic.id)
                        }
                        // 使用 MusicApi 获取正确的播放 URL（包括缓存逻辑）
                        val musicApi = com.neko.music.data.api.MusicApi(context)
                        val musicUrl = musicApi.getMusicFileUrl(randomMusic)
                        playMusic(musicUrl, randomMusic.id, randomMusic.title, randomMusic.artist, randomMusic.coverFilePath ?: "", fullCoverUrl)
                    } else {
                        android.util.Log.d("MusicPlayerManager", "No random music found")
                    }
                }
            }
        }
    }
    
    // 上一曲
    fun previous() {
        val currentId = _currentMusicId.value ?: return
        android.util.Log.d("MusicPlayerManager", "previous() called, currentId: $currentId, playHistory size: ${playHistory.size}")

        // 使用 runBlocking 同步执行 suspend 函数
        kotlinx.coroutines.runBlocking {
            // 从历史记录中获取上一首
            if (playHistory.size > 1) {
                // 移除当前歌曲
                playHistory.removeAt(playHistory.size - 1)
                // 获取上一首
                val previousId = playHistory.lastOrNull()
                android.util.Log.d("MusicPlayerManager", "previousId: $previousId")
                if (previousId != null) {
                    // 从数据库中获取上一首歌曲信息
                    val previousMusic = playlistManager.getPlaylistMusicById(previousId)
                    android.util.Log.d("MusicPlayerManager", "previousMusic: $previousMusic")
                    if (previousMusic != null) {
                        val fullCoverUrl = if (!previousMusic.coverFilePath.isNullOrEmpty()) {
                            UrlConfig.buildFullUrl("${previousMusic.coverFilePath}")
                        } else {
                            UrlConfig.getMusicCoverUrl(previousMusic.id)
                        }
                        // 使用 MusicApi 获取正确的播放 URL（包括缓存逻辑）
                        val musicApi = com.neko.music.data.api.MusicApi(context)
                        val musicUrl = musicApi.getMusicFileUrl(previousMusic)
                        playMusic(musicUrl, previousMusic.id, previousMusic.title, previousMusic.artist, previousMusic.coverFilePath ?: "", fullCoverUrl)
                        return@runBlocking
                    }
                }
            }

            // 历史记录中没有更多歌曲，按照列表顺序向上播放
            android.util.Log.d("MusicPlayerManager", "No more history, getting previous music by list order")
            val previousMusic = playlistManager.getPreviousMusic(currentId)
            android.util.Log.d("MusicPlayerManager", "previousMusic by list: $previousMusic")

            if (previousMusic != null) {
                val fullCoverUrl = if (!previousMusic.coverFilePath.isNullOrEmpty()) {
                    UrlConfig.buildFullUrl("${previousMusic.coverFilePath}")
                } else {
                    UrlConfig.getMusicCoverUrl(previousMusic.id)
                }
                // 使用 MusicApi 获取正确的播放 URL（包括缓存逻辑）
                val musicApi = com.neko.music.data.api.MusicApi(context)
                val musicUrl = musicApi.getMusicFileUrl(previousMusic)
                playMusic(musicUrl, previousMusic.id, previousMusic.title, previousMusic.artist, previousMusic.coverFilePath ?: "", fullCoverUrl)
            } else {
                // 列表上面没有了，循环到最后一首
                android.util.Log.d("MusicPlayerManager", "No previous music in list, getting last music")
                val lastMusic = playlistManager.getLastMusic()
                android.util.Log.d("MusicPlayerManager", "lastMusic: $lastMusic")
                if (lastMusic != null) {
                    val fullCoverUrl = if (!lastMusic.coverFilePath.isNullOrEmpty()) {
                        UrlConfig.buildFullUrl("${lastMusic.coverFilePath}")
                    } else {
                        UrlConfig.getMusicCoverUrl(lastMusic.id)
                    }
                    // 使用 MusicApi 获取正确的播放 URL（包括缓存逻辑）
                    val musicApi = com.neko.music.data.api.MusicApi(context)
                    val musicUrl = musicApi.getMusicFileUrl(lastMusic)
                    playMusic(musicUrl, lastMusic.id, lastMusic.title, lastMusic.artist, lastMusic.coverFilePath ?: "", fullCoverUrl)
                }
            }
        }
    }
    
    private var updateJob: Job? = null
    private var fadeJob: Job? = null
    private var coverBitmap: Bitmap? = null

    init {
        // 设置 MediaSession 回调（如果 MediaSession 已初始化）
        setupMediaSessionCallback()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isReleased) {
                    _isPlaying.value = isPlaying
                    updatePlaybackState()
                }
            }

            override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                Log.e("MusicPlayerManager", "播放错误: ${error.message}, 重试次数: $retryCount", error)
                // 增加重试计数器
                retryCount++

                // 尝试重新播放当前歌曲
                val currentUrl = _currentMusicUrl.value
                val currentId = _currentMusicId.value
                val currentTitle = _currentMusicTitle.value
                val currentArtist = _currentMusicArtist.value
                val currentCover = _currentMusicCover.value

                if (currentUrl != null && retryCount < MAX_RETRY_COUNT) {
                    Log.d("MusicPlayerManager", "尝试重新播放: $currentTitle (第 $retryCount 次重试)")
                    // 延迟 500ms 后重试
                    mainHandler.postDelayed({
                        try {
                            player.stop()
                            player.clearMediaItems()
                            val mediaItem = MediaItem.fromUri(currentUrl)
                            player.setMediaItem(mediaItem)
                            player.prepare()
                            player.play()
                        } catch (e: Exception) {
                            Log.e("MusicPlayerManager", "重试播放失败: ${e.message}", e)
                        }
                    }, 500)
                } else {
                    // 重试次数达到上限,自动切换到下一首
                    Log.d("MusicPlayerManager", "重试次数达到上限 ($MAX_RETRY_COUNT), 自动切换到下一首")
                    next()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (isReleased) return

                when (playbackState) {
                    Player.STATE_IDLE -> {}
                    Player.STATE_BUFFERING -> {}
                    Player.STATE_READY -> {
                        if (!isReleased) {
                            _duration.value = player.duration
                            // 音乐加载完成，预加载下一首
                            preloadNextMusic()
                        }
                    }

                    Player.STATE_ENDED -> {
                        if (isReleased) return

                        _isPlaying.value = false
                        player.seekTo(0)
                        updatePlaybackState()

                        // 根据播放模式自动切歌（直接调用 next() 方法）
                        when (_playMode.value) {
                            PlayMode.SINGLE_LOOP -> {
                                // 单曲循环：重新播放当前歌曲
                                val currentUrl = _currentMusicUrl.value
                                if (currentUrl != null) {
                                    player.seekTo(0)
                                    player.play()
                                }
                            }

                            else -> {
                                // 列表循环和随机播放都调用 next()
                                next()
                            }
                        }
                    }
                }
            }
        })

        updatePlaybackState()
    }

    private fun setupMediaSessionCallback() {
        // 延迟设置回调，等待 MediaSession 初始化
        scope.launch {
            // 等待 MediaSession 初始化
            while (mediaSession == null) {
                delay(100)
            }

            mediaSession?.setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    fadeIn()
                }

                override fun onPause() {
                    fadeOut {}
                }

                override fun onSeekTo(pos: Long) {
                    player.seekTo(pos)
                }

                override fun onSkipToNext() {
                    next()
                }

                override fun onSkipToPrevious() {
                    previous()
                }

                override fun onCustomAction(action: String, extras: android.os.Bundle?) {
                    when (action) {
                        "ACTION_TOGGLE_FAVORITE" -> {
                            toggleFavorite()
                        }
                        "ACTION_TOGGLE_LYRIC" -> {
                            // 切换桌面歌词状态
                            val context = appContext
                            if (context != null) {
                                val lyricPrefs = context.getSharedPreferences("desktop_lyric", Context.MODE_PRIVATE)
                                val newState = !isDesktopLyricEnabled

                                // 如果要开启但没有悬浮窗权限，先请求权限
                                if (newState && !android.provider.Settings.canDrawOverlays(context)) {
                                    Log.d("MusicPlayerManager", "桌面歌词需要悬浮窗权限")
                                    return
                                }

                                // 切换状态
                                lyricPrefs.edit().putBoolean("desktop_lyric_enabled", newState).apply()
                                isDesktopLyricEnabled = newState

                                // 控制桌面歌词服务
                                val lyricServiceIntent = Intent(context, com.neko.music.desktoplyric.DesktopLyricService::class.java)
                                if (newState) {
                                    lyricServiceIntent.action = com.neko.music.desktoplyric.DesktopLyricService.ACTION_SHOW
                                    context.startService(lyricServiceIntent)
                                } else {
                                    lyricServiceIntent.action = com.neko.music.desktoplyric.DesktopLyricService.ACTION_HIDE
                                    context.startService(lyricServiceIntent)
                                }

                                Log.d("MusicPlayerManager", "桌面歌词已${if (newState) "开启" else "关闭"}")
                            }
                        }
                    }
                }
            })

            // 设置完成后立即更新一次播放状态
            updatePlaybackState()
            Log.d("MusicPlayerManager", "MediaSession 回调设置完成")
        }
    }

    private suspend fun loadCoverBitmap(url: String?): Bitmap? {
        if (url == null) return null
        return try {
            val request = ImageRequest.Builder(appContext)
                .data(url)
                .build()
            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                result.image.asDrawable(appContext.resources).toBitmap()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun updatePlaybackState() {
        // 检查 MediaSession 是否已初始化
        if (mediaSession == null) {
            return
        }

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    "ACTION_TOGGLE_FAVORITE",
                    if (_isFavorite.value) "取消收藏" else "收藏",
                    if (_isFavorite.value) com.neko.music.R.drawable.ic_favorite_filled else com.neko.music.R.drawable.ic_favorite_border
                ).build()
            )
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    "ACTION_TOGGLE_LYRIC",
                    "词",
                    if (isDesktopLyricEnabled) com.neko.music.R.drawable.music else com.neko.music.R.drawable.music
                ).build()
            )
            .setState(
                if (player.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                player.currentPosition,
                1.0f
            )

        mediaSession?.setPlaybackState(stateBuilder.build())

        // 更新媒体元数据
        val title = _currentMusicTitle.value ?: ""
        val artist = _currentMusicArtist.value ?: ""
        val coverUrl = _currentMusicCover.value

        if (title.isNotEmpty() || artist.isNotEmpty()) {
            scope.launch {
                val bitmap = coverBitmap ?: loadCoverBitmap(coverUrl)
                coverBitmap = bitmap

                val metadataBuilder = android.support.v4.media.MediaMetadataCompat.Builder()
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, coverUrl)
                    .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, player.duration)

                if (bitmap != null) {
                    metadataBuilder.putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                }

                mediaSession?.setMetadata(metadataBuilder.build())
            }
        }
    }
    
    private fun startPositionUpdate() {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (true) {
                delay(100)
                if (player.isPlaying) {
                    _currentPosition.value = player.currentPosition
                }
            }
        }
    }
    
    private fun stopPositionUpdate() {
        updateJob?.cancel()
        updateJob = null
    }
    
    // 淡入效果
    private fun fadeIn() {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            // 获取 WakeLock 以保持 CPU 唤醒
            acquireWakeLock()

            player.volume = 0f
            player.play()
            _isPlaying.value = true
            startPositionUpdate()
            updatePlaybackState()

            val steps = 20
            val stepDelay = 300L / steps
            for (i in 1..steps) {
                delay(stepDelay)
                player.volume = i.toFloat() / steps
            }
            player.volume = 1f
        }
    }
    
    // 淡出效果
    private fun fadeOut(onComplete: () -> Unit) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val steps = 20
            val stepDelay = 300L / steps
            for (i in steps downTo 1) {
                delay(stepDelay)
                player.volume = i.toFloat() / steps
            }
            player.volume = 0f
            player.pause()
            _isPlaying.value = false
            stopPositionUpdate()
            player.volume = 1f
            updatePlaybackState()

            // 释放 WakeLock
            releaseWakeLock()

            onComplete()
        }
    }

    // 获取 WakeLock 以保持 CPU 唤醒
    private fun acquireWakeLock() {
        if (wakeLock == null || !wakeLock!!.isHeld) {
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                wakeLockTag
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10分钟超时，防止永久持有
            }
            Log.d("MusicPlayerManager", "WakeLock 已获取")
        }
    }

    // 释放 WakeLock
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d("MusicPlayerManager", "WakeLock 已释放")
            }
        }
        wakeLock = null
    }
    
    fun playMusic(url: String, id: Int? = null, title: String? = null, artist: String? = null, cover: String? = null, fullCoverUrl: String? = null) {
        // 重置重试计数器
        retryCount = 0

        // 清空预加载缓存（因为要播放新音乐了）
        preloadedNextMusic = null
        preloadedNextMusicUrl = null
        preloadedNextMusicFullCoverUrl = null

        if (_currentMusicUrl.value != url) {
            // 添加到历史记录（如果不是重复播放同一首歌）
            if (id != null && _currentMusicId.value != id) {
                playHistory.add(id)
            }

            // 立即更新 UI 状态
            _currentMusicUrl.value = url
            _currentMusicId.value = id
            _currentMusicTitle.value = title
            _currentMusicArtist.value = artist
            _currentMusicCover.value = fullCoverUrl ?: cover
            coverBitmap = null

            // 优先使用缓存文件
            val cacheManager = com.neko.music.data.cache.MusicCacheManager.getInstance(context)
            val playUrl = if (id != null) {
                cacheManager.getCachedMusicFile(id)?.absolutePath ?: url
            } else {
                url
            }

            // 先停止当前播放，避免状态冲突
            try {
                player.stop()
                player.clearMediaItems()
            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "停止播放失败: ${e.message}", e)
            }

            // 立即执行 ExoPlayer 操作（同步）
            try {
                val mediaItem = MediaItem.fromUri(playUrl)
                player.setMediaItem(mediaItem)
                player.prepare()
            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "ExoPlayer 操作失败: ${e.message}", e)
                // 如果准备失败，尝试重新设置
                try {
                    val mediaItem = MediaItem.fromUri(url)
                    player.setMediaItem(mediaItem)
                    player.prepare()
                } catch (e2: Exception) {
                    Log.e("MusicPlayerManager", "ExoPlayer 重试失败: ${e2.message}", e2)
                }
            }

            // 淡入播放（立即执行）
            fadeIn()

            // 异步保存到播放列表（不阻塞切歌）
            if (id != null && title != null && artist != null && id > 0) {
                scope.launch {
                    val music = com.neko.music.data.model.Music(
                        id = id,
                        title = title,
                        artist = artist,
                        album = "",
                        duration = 0,
                        filePath = url,
                        coverFilePath = cover ?: "",
                        uploadUserId = 0,
                        createdAt = ""
                    )
                    playlistManager.addToPlaylist(music)
                    // 更新addedAt时间，标记为最近播放
                    playlistManager.updateAddedAt(id)

                    // 缓存管理 - 仅在缓存启用时执行
                    val cacheManager = com.neko.music.data.cache.MusicCacheManager.getInstance(context)
                    
                    if (cacheManager.isCacheEnabled()) {
                        // 检查是否已缓存，如果没有则开始缓存
                        if (cacheManager.getCachedMusicFile(id) == null) {
                            cacheManager.cacheMusicFile(id, url, title, artist)
                                .onSuccess { 
                                    Log.d("MusicPlayerManager", "音乐缓存成功: $title")
                                }
                                .onFailure { e ->
                                    Log.e("MusicPlayerManager", "音乐缓存失败: $title", e)
                                }
                        } else {
                            // 更新缓存中的音乐标题和演唱者
                            cacheManager.updateMusicTitle(id, title)
                            cacheManager.updateMusicArtist(id, artist)
                        }

                        // 缓存封面
                        if (fullCoverUrl != null && fullCoverUrl.isNotEmpty()) {
                            if (cacheManager.getCachedCoverFile(id) == null) {
                                cacheManager.cacheCover(id, fullCoverUrl)
                                    .onSuccess {
                                        Log.d("MusicPlayerManager", "封面缓存成功: $title")
                                    }
                                    .onFailure { e ->
                                        Log.e("MusicPlayerManager", "封面缓存失败: $title", e)
                                    }
                            }
                        }
                    }

                    // 添加到最近播放列表
                    val recentPlayManager = com.neko.music.data.manager.RecentPlayManager(context)
                    recentPlayManager.addRecentPlay(music)
                }
            }

            // 异步检查收藏状态（不阻塞切歌）
            checkFavoriteStatus()
        } else {
            // 已有音乐，直接播放
            fadeIn()
        }
    }
    
    fun pause() {
        fadeOut {}
    }
    
    fun togglePlayPause() {
        if (_isPlaying.value) {
            fadeOut {}
        } else {
            fadeIn()
        }
    }
    
    fun seekTo(position: Long) {
        player.seekTo(position)
        _currentPosition.value = position
        updatePlaybackState()
    }
    
    // 注意：ExoPlayer 不再被释放，保持播放器始终活跃状态
    // 释放函数已被禁用以防止 "Ignoring messages sent after release" 错误
    
    // 预加载下一首音乐
    private fun preloadNextMusic() {
        val currentId = _currentMusicId.value ?: return

        scope.launch {
            try {
                // 根据播放模式获取下一首音乐
                val nextMusic = when (_playMode.value) {
                    PlayMode.LIST_LOOP -> {
                        playlistManager.getNextMusic(currentId)
                    }
                    PlayMode.SHUFFLE -> {
                        playlistManager.getRandomMusic(currentId)
                    }
                    PlayMode.SINGLE_LOOP -> {
                        // 单曲循环不需要预加载
                        null
                    }
                }

                if (nextMusic != null) {
                    val musicApi = com.neko.music.data.api.MusicApi(context)
                    val nextUrl = musicApi.getMusicFileUrl(nextMusic)

                    val fullCoverUrl = if (!nextMusic.coverFilePath.isNullOrEmpty()) {
                                                    if (nextMusic.coverFilePath.startsWith("http")) {
                                                        nextMusic.coverFilePath
                                                    } else {
                                                        UrlConfig.buildFullUrl("${nextMusic.coverFilePath}")
                                                    }
                                                } else {
                                                    UrlConfig.getMusicCoverUrl(nextMusic.id)
                                                }
                    // 缓存下一首音乐信息
                    preloadedNextMusic = nextMusic
                    preloadedNextMusicUrl = nextUrl
                    preloadedNextMusicFullCoverUrl = fullCoverUrl

                    Log.d("MusicPlayerManager", "预加载下一首音乐: ${nextMusic.title}")
                }
            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "预加载下一首音乐失败: ${e.message}", e)
            }
        }
    }

    suspend fun restoreLastPlayed(context: Context) {
        val lastPlayed = playlistManager.getLastPlayed()
        lastPlayed?.let { music ->
            val musicApi = com.neko.music.data.api.MusicApi(context)
            val url = musicApi.getMusicFileUrl(music)
            val fullCoverUrl = if (!music.coverFilePath.isNullOrEmpty()) {
                UrlConfig.buildFullUrl("${music.coverFilePath}")
            } else {
                UrlConfig.getMusicCoverUrl(music.id)
            }
            
            _currentMusicUrl.value = url
            _currentMusicId.value = music.id
            _currentMusicTitle.value = music.title
            _currentMusicArtist.value = music.artist
            _currentMusicCover.value = fullCoverUrl
            coverBitmap = null
            
            // 准备但不自动播放
            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.prepare()
        }
    }
    
    // 获取最近播放的历史记录
    suspend fun getPlayHistory(): List<Music> {
        val historyIds = playHistory.toList().reversed() // 反转，最新的在前面
        val result = mutableListOf<Music>()
        for (id in historyIds) {
            val music = playlistManager.getPlaylistMusicById(id)
            if (music != null) {
                result.add(music)
            }
        }
        return result
    }
    
    companion object {
        @Volatile
        private var instance: MusicPlayerManager? = null
        
        fun getInstance(context: Context): MusicPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: MusicPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }
}