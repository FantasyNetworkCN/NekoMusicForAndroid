package com.neko.music.ui.screens

import com.neko.music.ui.icons.NekoIcon
import com.neko.music.ui.icons.NekoSymbol
import com.neko.music.ui.icons.NekoSymbolIcon

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import com.neko.music.debug.NekoLog as Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import com.neko.music.data.api.CommentApi
import com.neko.music.data.api.VideoRenderApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.neko.music.config.AppConfig
import com.neko.music.R
import com.neko.music.util.UrlConfig
import com.neko.music.util.playerCoverMemoryKey
import com.neko.music.data.api.MusicApi
import com.neko.music.data.model.LyricsPayload
import com.neko.music.data.api.PlaylistApi
import com.neko.music.data.api.PlaylistInfo
import com.neko.music.data.api.PlaylistListResponse
import com.neko.music.data.model.Music
import com.neko.music.data.manager.TogetherSessionManager
import com.neko.music.service.MusicPlayerManager
import com.neko.music.service.PlayMode
import com.neko.music.service.AudioQuality
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.SakuraPink
import com.neko.music.ui.theme.SkyBlue
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.ui.components.AmllLyricsView
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LiquidGlassSlider
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.ShareSheetLiquidSection
import com.neko.music.ui.components.MoreSheetDragState
import com.neko.music.ui.components.PlayerVolumeModal
import com.neko.music.ui.components.isLiquidGlassEffectActive
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow
import org.json.JSONObject

private const val LYRICS_CONTROLS_AUTO_HIDE_MS = 5_000L

// 格式化时间显示（毫秒转 mm:ss）
fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    return String.format("%d:%02d", minutes, seconds)
}

/**
 * 分享视频剪辑用的曲目总长（秒）。
 * 优先用 ExoPlayer 的 [playerDurationMs]（毫秒）；部分曲目的 [musicDurationMeta] 误存为毫秒却被当秒用，
 * 会导致区间显示成「0:00 → 4817:30」这类异常值。
 */
fun resolveTrackDurationSeconds(musicDurationMeta: Int, playerDurationMs: Long): Int {
    if (playerDurationMs > 0) {
        return (playerDurationMs / 1000).toInt().coerceAtLeast(1)
    }
    if (musicDurationMeta <= 0) return 1
    // 元数据约定为秒；单轨超过 10 小时极罕见，更可能是毫秒
    return if (musicDurationMeta > 36_000) {
        (musicDurationMeta / 1000).coerceAtLeast(1)
    } else {
        musicDurationMeta.coerceAtLeast(1)
    }
}

// LRC 逐字时间戳
data class LrcSyllable(
    val time: Float,
    val text: String,
    val endTime: Float? = null,
)

// LRC歌词行数据类
data class LrcLine(
    val time: Float, // 时间（秒）
    val text: String, // 歌词文本
    val translation: String = "", // 翻译文本（可选）
    val romanization: String = "", // 罗马音（可选）
    val syllables: List<LrcSyllable> = emptyList(), // 增强 LRC 的逐字时间戳（可选）
    val endTime: Float? = null,
)

private val syllableTimestampRegex = Regex("<(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,5}))?>")

private const val AMLL_WORD_LYRICS_ENABLED = true

// 解析 LRC 格式歌词（支持双语）
fun parseLrcLyrics(lrcText: String): List<LrcLine> {
    val lines = lrcText.lines()
    val result = mutableListOf<LrcLine>()

    var i = 0
    val timeRegex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{1,5})\\]")

    while (i < lines.size) {
        val line = lines[i].trim()

        // 跳过空行
        if (line.isEmpty()) {
            i++
            continue
        }

        // 解析时间戳 [mm:ss.xxx]（支持1-5位毫秒）
        val match = timeRegex.find(line)

        if (match != null) {
            val minutes = match.groupValues[1].toInt()
            val seconds = match.groupValues[2].toInt()
            val millisecondsStr = match.groupValues[3]

            // 根据毫秒位数计算时间
            val milliseconds = millisecondsStr.toFloat()
            val time = minutes * 60 + seconds + milliseconds / 1000f

            // 提取歌词文本，并解析增强 LRC 的 <mm:ss.xxx> 逐字时间戳
            val rawText = line.substring(match.range.last + 1).trim()
            val (text, parsedSyllables) = parseSyllableTimestamps(rawText, time)
            val syllables = if (AMLL_WORD_LYRICS_ENABLED) parsedSyllables else emptyList()

            // 检查是否有翻译（下一行可能包含翻译）
            var translation: String? = null
            var hasTranslation = false
            if (i + 1 < lines.size) {
                val nextLine = lines[i + 1].trim()
                // 翻译行通常以 { } 包裹，且不包含时间戳
                if (nextLine.startsWith("{") && nextLine.endsWith("}") && !timeRegex.containsMatchIn(nextLine)) {
                    hasTranslation = true
                    // 提取花括号内的内容
                    var content = nextLine.substring(1, nextLine.length - 1)
                    // 去掉转义字符和引号
                    content = content.replace("\\", "").replace("\"", "").replace("'", "")
                    translation = cleanLyricDisplayText(content)
                }
            }

            result.add(
                LrcLine(
                    time = time,
                    text = cleanLyricDisplayText(text),
                    translation = translation.orEmpty(),
                    syllables = syllables,
                )
            )

            // 如果有翻译行，跳过它
            if (hasTranslation) {
                i++
            }
        }

        i++
    }

    return result
}

/** 优先使用后端 typesafe 字级行；没有时回退普通 LRC。 */
fun parseLyricsPayload(payload: LyricsPayload): List<LrcLine> {
    if (payload.richLines.isNotEmpty()) {
        return payload.richLines.map { line ->
            LrcLine(
                time = line.startMs / 1000f,
                text = cleanLyricDisplayText(line.text),
                translation = cleanLyricDisplayText(line.translation),
                romanization = cleanLyricDisplayText(line.romanization),
                syllables = line.words.map { word ->
                    LrcSyllable(
                        time = word.startMs / 1000f,
                        text = word.text,
                        endTime = word.endMs.takeIf { it > word.startMs }?.div(1000f),
                    )
                },
                endTime = line.endMs.takeIf { it > line.startMs }?.div(1000f),
            )
        }
    }
    return parseLrcLyrics(payload.lyrics)
}

internal fun cleanLyricDisplayText(value: String?): String {
    val trimmed = value?.trim().orEmpty()
    return if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) "" else value.orEmpty()
}

private fun parseSyllableTimestamps(
    rawText: String,
    fallbackTime: Float,
): Pair<String, List<LrcSyllable>> {
    val matches = syllableTimestampRegex.findAll(rawText).toList()
    if (matches.isEmpty()) return rawText to emptyList()

    val syllables = mutableListOf<LrcSyllable>()
    val leadingText = rawText.substring(0, matches.first().range.first)
    if (leadingText.isNotBlank()) {
        syllables += LrcSyllable(fallbackTime, leadingText)
    }

    matches.forEachIndexed { index, match ->
        val textStart = match.range.last + 1
        val textEnd = matches.getOrNull(index + 1)?.range?.first ?: rawText.length
        val syllableText = rawText.substring(textStart, textEnd)
        if (syllableText.isEmpty()) return@forEachIndexed

        val minutes = match.groupValues[1].toInt()
        val seconds = match.groupValues[2].toInt()
        val fractionText = match.groupValues[3]
        val fraction = if (fractionText.isEmpty()) {
            0f
        } else {
            fractionText.toFloat() / 10f.pow(fractionText.length)
        }
        syllables += LrcSyllable(minutes * 60 + seconds + fraction, syllableText)
    }

    val displayText = syllables.joinToString(separator = "") { it.text }
    return if (displayText.isBlank()) {
        rawText.replace(syllableTimestampRegex, "").trim() to emptyList()
    } else {
        displayText to syllables
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PlayerScreen(
    music: Music,
    onBackClick: () -> Unit,
    onPlaylistClick: () -> Unit = {},
    onDismissDragStart: () -> Unit = {},
    onDismissDrag: (Float) -> Unit = {},
    onDismissDragEnd: (Float) -> Unit = {},
    onDismissDragCancel: () -> Unit = {},
    commentsPageReady: Boolean = true,
    lyricsPageReady: Boolean = true,
    artworkSharedModifier: Modifier = Modifier,
    artworkExpansionProgress: () -> Float = { 1f },
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val playerManager = MusicPlayerManager.getInstance(context)
    val tokenManager = com.neko.music.data.manager.TokenManager(context)
    val togetherState by TogetherSessionManager.state.collectAsState()
    val togetherConnected by TogetherSessionManager.connected.collectAsState()
    val togetherActive = togetherState != null

    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val currentPosition = playerManager.currentPosition.collectAsState()
    val duration by playerManager.duration.collectAsState()
    val currentMusicId by playerManager.currentMusicId.collectAsState()
    val currentMusicTitle by playerManager.currentMusicTitle.collectAsState()
    val currentMusicArtist by playerManager.currentMusicArtist.collectAsState()
    val currentMusicCover by playerManager.currentMusicCover.collectAsState()
    val currentMusicUrl by playerManager.currentMusicUrl.collectAsState()
    val audioQuality by playerManager.audioQuality.collectAsState()

    // 检查登录状态
    val isLoggedIn = tokenManager.isLoggedIn()

    // 播放位置每 100ms 更新一次。这里保留 State 而不在 PlayerScreen 顶层解包，
    // 让订阅只发生在进度条与当前歌词行，避免封面、评论和整页玻璃层跟着重组。
    val currentProgressSeconds = remember(currentPosition) {
        derivedStateOf { currentPosition.value / 1000f }
    }

    var musicFileUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    // 路由只负责首次进入播放页（含深链）时的起播。Activity 因横竖屏重建后，
    // 路由里的歌曲可能已不是播放器当前曲目，不能再用它覆盖正在播放的队列位置。
    var routePlaybackHandled by rememberSaveable(music.id) {
        mutableStateOf(currentMusicId == music.id)
    }
    var lyrics by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
    var lyricScrubPosition by remember(currentMusicId) { mutableStateOf<Float?>(null) }
    val lyricsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isFavorite by playerManager.isFavorite.collectAsState()
    var favoriteCount by remember { mutableStateOf<Int?>(null) }
    var commentCount by remember { mutableStateOf(0) }
    val commentApi = remember { CommentApi(context) }
    val widePlayerLayout = LocalConfiguration.current.screenWidthDp >= 840
    val pagerState = rememberPagerState(
        // 0 = 评论，1 = 播放（大屏同屏显示歌词），2 = 独立歌词页（仅小屏）
        initialPage = 1,
        pageCount = { if (widePlayerLayout) 2 else 3 },
    )
    val showLyrics = !widePlayerLayout && pagerState.currentPage == 2
    LaunchedEffect(widePlayerLayout) {
        if (widePlayerLayout && pagerState.currentPage > 1) {
            pagerState.scrollToPage(1)
        }
    }
    var lyricsControlsVisible by remember { mutableStateOf(true) }
    var lyricsInteractionPulse by remember { mutableIntStateOf(0) }
    LaunchedEffect(showLyrics, lyricsInteractionPulse) {
        if (!showLyrics) {
            lyricsControlsVisible = true
            return@LaunchedEffect
        }
        lyricsControlsVisible = true
        delay(LYRICS_CONTROLS_AUTO_HIDE_MS)
        lyricsControlsVisible = false
    }
    val playMode by playerManager.playMode.collectAsState()
    val playbackSpeed by playerManager.playbackSpeed.collectAsState()
    val sleepTimerMinutes by playerManager.sleepTimerMinutes.collectAsState()
    val sleepTimerRemainingSeconds by playerManager.sleepTimerRemainingSeconds.collectAsState()

    // 通知权限
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }
    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    // 桌面歌词设置
    val desktopLyricPrefs = remember { context.getSharedPreferences("desktop_lyric", Context.MODE_PRIVATE) }
    var isDesktopLyricEnabled by remember { mutableStateOf(desktopLyricPrefs.getBoolean("desktop_lyric_enabled", false)) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }

    // 悬浮窗权限检查
    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.provider.Settings.canDrawOverlays(context)
            } else {
                true
            }
        )
    }

    // 登录提示
    
    val playModeChanged by playerManager.playModeChanged.collectAsState()

    // 分享对话框
    var showShareDialog by remember { mutableStateOf(false) }
    var showVolumeModal by rememberSaveable { mutableStateOf(false) }

    // 分享视频渲染
    var showVideoRenderDialog by remember { mutableStateOf(false) }
    var videoRenderBusy by remember { mutableStateOf(false) }
    var videoRenderJobId by remember { mutableStateOf<String?>(null) }
    var videoRenderJobStatus by remember { mutableStateOf<String?>(null) }

    // 添加到歌单
    var playlists by remember { mutableStateOf<List<com.neko.music.data.api.PlaylistInfo>>(emptyList()) }
    var selectedPlaylistId by remember { mutableStateOf<Int?>(null) }
    var playlistFirstMusicCovers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    // 创建歌单对话框状态
    var showCreateDialog by remember { mutableStateOf(false) }
    var dialogPlaylistName by remember { mutableStateOf("") }

    // 预加载字符串资源
    val sleepTimerCancelled = stringResource(id = R.string.sleep_timer_cancelled)
    val pleaseLoginFirst = stringResource(id = R.string.please_login_first)
    val createSuccess = stringResource(id = R.string.create_success)
    val listLoop = stringResource(id = R.string.list_loop)
    val singleLoop = stringResource(id = R.string.single_loop)
    val shufflePlay = stringResource(id = R.string.shuffle_play)
    val linkCopied = stringResource(id = R.string.link_copied)
    val copyFailed = stringResource(id = R.string.copy_failed)
    val shareFailed = stringResource(id = R.string.share_failed)
    val videoRenderSubmitted = stringResource(id = R.string.video_render_submitted)
    val videoRenderCreateFailed = stringResource(id = R.string.video_render_create_failed)
    val videoRenderDownloadStarted = stringResource(id = R.string.video_render_download_started)
    val videoRenderButtonBusy = stringResource(id = R.string.video_render_button_busy)
    val videoRenderStatusProcessing = stringResource(id = R.string.video_render_status_processing)

    // 从播放器获取当前音乐信息
    val isLocalCurrentMusic = (currentMusicId ?: music.id) < 0

    val currentMusic = remember(
        currentMusicId,
        currentMusicTitle,
        currentMusicArtist,
        currentMusicCover,
        currentMusicUrl,
        duration,
        music,
    ) {
        val id = currentMusicId
        val title = currentMusicTitle
        val artist = currentMusicArtist
        if (id != null && title != null && artist != null) {
            Music(
                id = id,
                title = title,
                artist = artist,
                album = "",
                duration = duration.toInt(),
                filePath = currentMusicUrl ?: musicFileUrl ?: "",
                coverFilePath = currentMusicCover ?: "",
                uploadUserId = 0,
                createdAt = ""
            )
        } else {
            music
        }
    }

    val musicApi = remember { MusicApi(context) }
    val scope = rememberCoroutineScope()
    val isDarkTheme = isAppDarkTheme()
    var maxQuality by remember { mutableStateOf("hq") }
    val qualityMusicId = currentMusicId ?: music.id

    LaunchedEffect(qualityMusicId) {
        favoriteCount = null
        commentCount = 0
        if (qualityMusicId > 0) {
            val info = musicApi.getMusicInfo(qualityMusicId).getOrNull()
            maxQuality = info?.maxQuality?.lowercase() ?: "hq"
            favoriteCount = info?.favoriteCount
            val comments = commentApi.getComments(
                musicId = qualityMusicId,
                page = 1,
                pageSize = 1,
                token = tokenManager.getToken(),
            )
            if (comments.success) {
                commentCount = comments.data?.totalComments ?: 0
            }
        } else {
            maxQuality = "standard"
        }
        playerManager.updateMusicMaxQuality(maxQuality)
    }

    // 分享面板打开后再拉歌单，避免点击菜单被网络阻塞导致「卡一下才弹出」
    LaunchedEffect(showShareDialog) {
        if (!showShareDialog) return@LaunchedEffect
        try {
            val token = tokenManager.getToken()
            if (token != null) {
                val playlistApi = PlaylistApi(token, context)
                val response = playlistApi.getMyPlaylists()
                if (response.success) {
                    playlists = response.playlists ?: emptyList()
                    playlistFirstMusicCovers = emptyMap()
                    playlists.forEach { playlist ->
                        if (playlist.coverPath.isNullOrEmpty() && playlist.musicCount > 0) {
                            scope.launch {
                                try {
                                    val musicResponse = playlistApi.getPlaylistMusic(playlist.id)
                                    if (musicResponse.success && musicResponse.musicList?.isNotEmpty() == true) {
                                        val firstMusic = musicResponse.musicList[0]
                                        val firstCoverUrl = UrlConfig.getMusicCoverUrl(firstMusic.id)
                                        playlistFirstMusicCovers = playlistFirstMusicCovers + (playlist.id to firstCoverUrl)
                                    }
                                } catch (e: Exception) {
                                    Log.e("PlayerScreen", "加载歌单${playlist.id}封面失败", e)
                                }
                            }
                        }
                    }
                } else {
                    playlists = emptyList()
                }
            } else {
                playlists = emptyList()
            }
        } catch (e: Exception) {
            Log.e("PlayerScreen", "加载歌单失败", e)
            playlists = emptyList()
        }
    }

    LaunchedEffect(currentMusic.id) {
        videoRenderJobId = null
        videoRenderJobStatus = null
    }

    LaunchedEffect(videoRenderJobId, isLoggedIn) {
        val jobId = videoRenderJobId ?: return@LaunchedEffect
        if (!isLoggedIn) return@LaunchedEffect
        while (true) {
            val token = tokenManager.getToken() ?: break
            val res = VideoRenderApi(token).fetchStatus(jobId)
            if (res.success && res.data != null) {
                videoRenderJobStatus = res.data.status
                when (res.data.status) {
                    "done" -> break
                    "failed" -> break
                }
            }
            delay(8_000)
        }
    }

    // 加载音乐文件URL，只在路由音乐变化时准备播放。
    // currentMusicUrl 会在切歌时变化，不能作为 key，否则页面会用旧路由歌曲把刚切到的新歌覆盖回去。
    LaunchedEffect(music.id) {
        isLoading = true
        scope.launch {
            musicFileUrl = when {
                music.id < 0 && currentMusicId == music.id -> currentMusicUrl ?: music.filePath
                music.id < 0 && UrlConfig.isLocalUri(music.filePath) -> music.filePath
                music.id < 0 -> null
                else -> musicApi.getMusicFileUrl(music)
            }
            isLoading = false
            musicFileUrl?.let { url ->
                // 已有播放器状态时，仅允许该路由首次接管；配置变更后恢复的旧路由不再重新起播。
                val shouldStartFromRoute = currentMusicId == null ||
                    (currentMusicId != music.id && !routePlaybackHandled)
                if (shouldStartFromRoute) {
                    routePlaybackHandled = true
                    // 获取完整的封面URL
                    val fullCoverUrl = when {
                        music.id < 0 && music.coverFilePath.isNullOrEmpty() -> null
                        !music.coverFilePath.isNullOrEmpty() -> UrlConfig.buildFullUrl("${music.coverFilePath}")
                        else -> UrlConfig.getMusicCoverUrl(music.id)
                    }
                    Log.d(
                        "PlayerScreen",
                        "封面URL: $fullCoverUrl, coverFilePath: ${music.coverFilePath}"
                    )
                    playerManager.playMusic(
                        url,
                        music.id,
                        music.title,
                        music.artist,
                        music.coverFilePath ?: "",
                        fullCoverUrl
                    )
                }
            }
            Log.d("PlayerScreen", "音乐文件URL: $musicFileUrl, 当前音乐ID: $currentMusicId")
        }
    }

    // 加载歌词
    LaunchedEffect(currentMusic.id) {
        scope.launch {
            val result = musicApi.getMusicLyricsPayload(currentMusic)
            result.fold(
                onSuccess = { payload ->
                    lyrics = parseLyricsPayload(payload)
                    Log.d(
                        "PlayerScreen",
                        "歌词加载成功，共 ${lyrics.size} 行，逐字片段=" +
                            lyrics.sumOf { it.syllables.size }
                    )
                },
                onFailure = { error ->
                    lyrics = emptyList()
                    Log.e("PlayerScreen", "歌词加载失败: ${error.message}")
                }
            )
        }
    }

    // 监听桌面歌词状态变化
    LaunchedEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "desktop_lyric_enabled") {
                isDesktopLyricEnabled = desktopLyricPrefs.getBoolean("desktop_lyric_enabled", false)
            }
        }
        desktopLyricPrefs.registerOnSharedPreferenceChangeListener(listener)
        
        try {
            kotlinx.coroutines.delay(Long.MAX_VALUE)
        } finally {
            desktopLyricPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // 监听悬浮窗权限状态变化
    LaunchedEffect(Unit) {
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    // 记录用户是否尝试开启桌面歌词但被权限阻止
    var pendingDesktopLyricEnable by remember { mutableStateOf(false) }

    // 监听应用生命周期，在恢复时重新检查权限并自动启动桌面歌词
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 重新检查权限状态
                val newPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.provider.Settings.canDrawOverlays(context)
                } else {
                    true
                }
                
                hasOverlayPermission = newPermission
                
                // 如果用户之前尝试开启桌面歌词但被权限阻止，现在有了权限，自动开启
                if (pendingDesktopLyricEnable && newPermission && !isDesktopLyricEnabled) {
                    isDesktopLyricEnabled = true
                    desktopLyricPrefs.edit().putBoolean("desktop_lyric_enabled", true).apply()
                    val serviceIntent = Intent(context, com.neko.music.desktoplyric.DesktopLyricService::class.java)
                    serviceIntent.action = com.neko.music.desktoplyric.DesktopLyricService.ACTION_SHOW
                    context.startService(serviceIntent)
                    pendingDesktopLyricEnable = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        try {
            kotlinx.coroutines.delay(Long.MAX_VALUE)
        } finally {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 桌面歌词切换逻辑
    val toggleDesktopLyric = {
        if (isDesktopLyricEnabled) {
            // 关闭桌面歌词
            isDesktopLyricEnabled = false
            desktopLyricPrefs.edit().putBoolean("desktop_lyric_enabled", false).apply()
            val serviceIntent = Intent(context, com.neko.music.desktoplyric.DesktopLyricService::class.java)
            serviceIntent.action = com.neko.music.desktoplyric.DesktopLyricService.ACTION_HIDE
            context.startService(serviceIntent)
        } else {
            // 开启桌面歌词，先检查权限
            // 普通设备需要检查悬浮窗权限
            if (!hasOverlayPermission) {
                showOverlayPermissionDialog = true
                pendingDesktopLyricEnable = true
            } else {
                isDesktopLyricEnabled = true
                desktopLyricPrefs.edit().putBoolean("desktop_lyric_enabled", true).apply()
                val serviceIntent = Intent(context, com.neko.music.desktoplyric.DesktopLyricService::class.java)
                serviceIntent.action = com.neko.music.desktoplyric.DesktopLyricService.ACTION_SHOW
                context.startService(serviceIntent)
            }
        }
        Unit
    }

    val coverUrl = when {
        currentMusic.coverFilePath.isNullOrEmpty() && currentMusic.id < 0 -> null
        currentMusic.id < 0 -> currentMusic.coverFilePath
        !currentMusic.coverFilePath.isNullOrEmpty() -> UrlConfig.buildFullUrl(currentMusic.coverFilePath)
        else -> UrlConfig.getMusicCoverUrl(currentMusic.id)
    }

    val colorScheme = MaterialTheme.colorScheme
    val pageBackdrop = rememberLiquidPageBackdrop(colorScheme.background)
    val useLiquidStyle = isLiquidGlassEffectActive(pageBackdrop)
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isCommentsPage = pagerState.currentPage == 0
    // 评论页只保留悬浮迷你播放器，给评论列表和输入框让出完整空间。
    val fullBottomReserve = if (isLandscape) 154.dp else 248.dp
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val miniPlayerReserve = 84.dp + navigationBarBottom
    val lyricsControlsHidden = showLyrics && !lyricsControlsVisible
    val bottomGlassReserve by animateDpAsState(
        targetValue = when {
            isCommentsPage -> miniPlayerReserve
            lyricsControlsHidden -> navigationBarBottom
            else -> fullBottomReserve
        },
        animationSpec = tween(durationMillis = 240),
        label = "player_bottom_reserve",
    )
    val pageBaseColor = if (isDarkTheme) Color(0xFF09090B) else Color(0xFFF5F5F7)

    val onFavoriteToggle: () -> Unit = {
        if (isLoggedIn) {
            favoriteCount?.let {
                favoriteCount = (it + if (isFavorite) -1 else 1).coerceAtLeast(0)
            }
            playerManager.toggleFavorite()
        } else {
            Toast.makeText(context, pleaseLoginFirst, Toast.LENGTH_SHORT).show()
        }
    }
    val onCommentClick: () -> Unit = {
        scope.launch { pagerState.animateScrollToPage(0) }
    }
    val openVideoRenderDialog: () -> Unit = {
        if (!isLoggedIn) {
            Toast.makeText(context, pleaseLoginFirst, Toast.LENGTH_SHORT).show()
        } else {
            if (videoRenderJobStatus == "failed") {
                videoRenderJobId = null
                videoRenderJobStatus = null
            }
            showVideoRenderDialog = true
        }
    }
    val downloadRenderedVideo: () -> Unit = download@{
        val jobId = videoRenderJobId ?: return@download
        val name = "${currentMusic.title}.mp4"
        scope.launch {
            VideoRenderApi.enqueueDownload(context, jobId, name).fold(
                onSuccess = {
                    Toast.makeText(
                        context,
                        videoRenderDownloadStarted,
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = { e ->
                    Toast.makeText(
                        context,
                        e.message ?: videoRenderCreateFailed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    val latestDismissDragStart by rememberUpdatedState(onDismissDragStart)
    val latestDismissDrag by rememberUpdatedState(onDismissDrag)
    val latestDismissDragEnd by rememberUpdatedState(onDismissDragEnd)
    val latestDismissDragCancel by rememberUpdatedState(onDismissDragCancel)
    val dismissDragModifier = Modifier.pointerInput(Unit) {
        var started = false
        val velocityTracker = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = {
                started = false
                velocityTracker.resetTracking()
            },
            onVerticalDrag = { change, dragAmount ->
                // 只接管向下拖动；向上手势仍交给封面/分页内容处理。
                if (!started && dragAmount > 0f) {
                    started = true
                    latestDismissDragStart()
                }
                if (started) {
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    latestDismissDrag(dragAmount)
                    change.consume()
                }
            },
            onDragEnd = {
                if (started) {
                    latestDismissDragEnd(velocityTracker.calculateVelocity().y)
                }
                started = false
            },
            onDragCancel = {
                if (started) {
                    latestDismissDragCancel()
                }
                started = false
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (pagerState.currentPage == 1 && !showShareDialog && !showVolumeModal) dismissDragModifier else Modifier)
            .pointerInput(showLyrics) {
                if (!showLyrics) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    lyricsControlsVisible = true
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    lyricsInteractionPulse++
                }
            },
    ) {
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
            if (useLiquidStyle) {
                // 液态模式：模糊封面提供环境色，表面层再做折射。
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.18f)
                        .blur(42.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    alpha = if (isDarkTheme) 0.38f else 0.30f,
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDarkTheme) 0.08f else 0.24f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isDarkTheme) {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.30f),
                                        Color(0xFF09090B).copy(alpha = 0.62f),
                                        Color(0xFF09090B).copy(alpha = 0.94f),
                                    ),
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.54f),
                                        Color.White.copy(alpha = 0.76f),
                                        Color(0xFFF5F5F7).copy(alpha = 0.96f),
                                    ),
                                )
                            },
                        ),
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(pageBaseColor.copy(alpha = 0.36f), Color.Transparent),
                            ),
                        ),
                )
            } else {
                // Material 3：静态 tonal 背景只保留一层纵向色阶，避免径向光斑与玻璃高光残留。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to colorScheme.surfaceContainerLow,
                                0.42f to colorScheme.background,
                                1f to colorScheme.background,
                            ),
                        ),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(bottom = bottomGlassReserve),
            ) {
                PlayerTopReserve(
                    isLandscape = isLandscape,
                    lyricsHeaderVisible = pagerState.currentPage == 2,
                )

                if (togetherActive) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 920.dp)
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        TogetherMemberAvatarStrip(
                            members = togetherState?.members.orEmpty(),
                            connected = togetherConnected,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // 大屏把封面与歌词合并在播放页；小屏继续使用评论、封面、歌词三页横滑。
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    userScrollEnabled = true,
                    beyondViewportPageCount = 1,
                ) { page ->
                    when (page) {
                        0 -> {
                            if (commentsPageReady) {
                                CommentPage(
                                    musicId = currentMusic.id,
                                    isDarkTheme = isDarkTheme,
                                    onRequestLogin = {
                                        Toast.makeText(context, pleaseLoginFirst, Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    bottomReserve = bottomGlassReserve,
                                    onCountChanged = { commentCount = it },
                                )
                            } else {
                                Box(Modifier.fillMaxSize())
                            }
                        }

                        1 -> {
                            if (widePlayerLayout && lyricsPageReady) {
                                Box(Modifier.fillMaxSize()) {
                                    PlayerLyricsPane(
                                        lyrics = lyrics,
                                        currentProgressSeconds = currentProgressSeconds,
                                        isLoading = isLoading,
                                        isPlaying = isPlaying && !isBuffering,
                                        playbackSpeed = playbackSpeed,
                                        active = true,
                                        scrubPositionSeconds = lyricScrubPosition,
                                        listState = lyricsListState,
                                        onLineClick = { lineTime ->
                                            if (togetherActive) {
                                                TogetherSessionManager.seekTo((lineTime * 1000f).toLong())
                                            } else {
                                                playerManager.seekTo((lineTime * 1000f).toLong())
                                            }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .fillMaxHeight()
                                            .fillMaxWidth(0.46f)
                                            .padding(start = 8.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
                                    )
                                }
                            }
                        }

                        2 -> {
                            if (lyricsPageReady) {
                                PlayerLyricsPane(
                                    lyrics = lyrics,
                                    currentProgressSeconds = currentProgressSeconds,
                                    isLoading = isLoading,
                                    isPlaying = isPlaying && !isBuffering,
                                    playbackSpeed = playbackSpeed,
                                    active = showLyrics,
                                    scrubPositionSeconds = lyricScrubPosition,
                                    listState = lyricsListState,
                                    onLineClick = { lineTime ->
                                        if (togetherActive) {
                                            TogetherSessionManager.seekTo((lineTime * 1000f).toLong())
                                        } else {
                                            playerManager.seekTo((lineTime * 1000f).toLong())
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Box(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }

        PlayerPersistentVisualLayer(
            music = currentMusic,
            coverUrl = currentMusicCover ?: coverUrl,
            pagerState = pagerState,
            isLandscape = isLandscape,
            togetherActive = togetherActive,
            bottomReserve = bottomGlassReserve,
            artworkSharedModifier = artworkSharedModifier,
            artworkExpansionProgress = artworkExpansionProgress,
            onMenuClick = { showShareDialog = true },
            onLeaveTogetherClick = {
                TogetherSessionManager.leave()
                Toast.makeText(
                    context,
                    R.string.together_left,
                    Toast.LENGTH_SHORT,
                ).show()
            },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1.5f),
        )

        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(2f)
                    .statusBarsPadding()
                    .padding(horizontal = if (isLandscape) 28.dp else 16.dp)
                    .then(if (pagerState.currentPage != 1) dismissDragModifier else Modifier)
            ) {
                PlayerSceneGrabber(
                    isDarkTheme = isDarkTheme,
                    onDismiss = onBackClick,
                    dragModifier = if (pagerState.currentPage == 1) {
                        dismissDragModifier
                    } else {
                        Modifier
                    },
                )
            }

            AnimatedVisibility(
                visible = !showLyrics || lyricsControlsVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .zIndex(1f),
                enter = fadeIn(tween(220)) +
                    slideInVertically(tween(260)) { it / 4 },
                exit = fadeOut(tween(260)) +
                    slideOutVertically(tween(280)) { it / 4 },
            ) {
                PlayerBottomBar(
                    modifier = Modifier.fillMaxWidth(),
                    isLandscape = isLandscape,
                    compactForComments = isCommentsPage,
                    isDarkTheme = isDarkTheme,
                    coverUrl = coverUrl,
                    musicTitle = currentMusic.title,
                    musicArtist = currentMusic.artist,
                    positionMillis = currentPosition,
                    durationMillis = duration,
                    isLoading = isLoading,
                    isBuffering = isBuffering,
                    isPlaying = isPlaying,
                    musicFileUrl = musicFileUrl,
                    playMode = if (togetherActive) {
                        when (togetherState?.playlist?.repeatMode) {
                            "single_repeat" -> PlayMode.SINGLE_LOOP
                            "shuffle" -> PlayMode.SHUFFLE
                            else -> PlayMode.LIST_LOOP
                        }
                    } else {
                        playMode
                    },
                    isFavorite = isFavorite,
                    audioQuality = audioQuality,
                    maxQuality = maxQuality,
                    onScrubPositionChange = { lyricScrubPosition = it },
                    onProgressChange = { value ->
                        if (duration > 0) {
                            val position = (value * duration).toLong()
                            if (togetherActive) {
                                TogetherSessionManager.seekTo(position)
                            } else {
                                playerManager.seekTo(position)
                            }
                        }
                    },
                    onPlayPauseClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        if (togetherActive) TogetherSessionManager.toggle()
                        else playerManager.togglePlayPause()
                    },
                    onPreviousClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        if (togetherActive) TogetherSessionManager.previous()
                        else playerManager.previous()
                    },
                    onNextClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        if (togetherActive) TogetherSessionManager.next()
                        else playerManager.next()
                    },
                    onFavoriteClick = onFavoriteToggle,
                    onPlaylistClick = onPlaylistClick,
                    onVolumeClick = { showVolumeModal = true },
                    onPlayModeClick = {
                        if (togetherActive) TogetherSessionManager.cyclePlayMode()
                        else playerManager.togglePlayMode()
                    },
                    onQualitySelected = { playerManager.setAudioQuality(it) },
                    onOpenPlayer = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                )
            }
            if (showShareDialog) {
                ShareDialog(
                    music = currentMusic,
                    isDesktopLyricEnabled = isDesktopLyricEnabled,
                    onDesktopLyricClick = toggleDesktopLyric,
                    onDismiss = { showShareDialog = false },
                    onCopyLink = {
                        scope.launch {
                            showShareDialog = false
                            try {
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val shareText = context.getString(R.string.share_music_text, currentMusic.artist, currentMusic.title, currentMusic.id)
                                val clip = android.content.ClipData.newPlainText(context.getString(R.string.music_link), shareText)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, linkCopied, Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, copyFailed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDownload = {
                        scope.launch {
                            showShareDialog = false
                            if (currentMusic.id < 0) {
                                Toast.makeText(context, context.getString(R.string.local_music_cloud_action_unavailable), Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            try {
                                val downloadHelper = com.neko.music.util.DownloadHelper(context)
                                val result = downloadHelper.downloadMusicWithLyrics(currentMusic)
                                result.fold(
                                    onSuccess = { message ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = { error ->
                                        val errorMsg = error.message ?: "Unknown error"
                                        Toast.makeText(context, context.getString(R.string.download_failed_format, errorMsg), Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } catch (e: Exception) {
                                val errorMsg = e.message ?: "Unknown error"
                                Toast.makeText(context, "下载失败: $errorMsg", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onShareToTwitter = {
                        scope.launch {
                            showShareDialog = false
                            if (currentMusic.id < 0) {
                                Toast.makeText(context, context.getString(R.string.local_music_cloud_action_unavailable), Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            try {
                                val shareText = context.getString(R.string.share_music_text, currentMusic.artist, currentMusic.title, currentMusic.id)
                                val encodedText = java.net.URLEncoder.encode(shareText, "UTF-8")
                                val twitterIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    data = android.net.Uri.parse("twitter://post?message=$encodedText")
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(twitterIntent)
                                } catch (e: Exception) {
                                    val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        data = android.net.Uri.parse("https://twitter.com/intent/tweet?text=$encodedText")
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(webIntent)
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, shareFailed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onShareToQQ = {
                        scope.launch {
                            showShareDialog = false
                            if (currentMusic.id < 0) {
                                Toast.makeText(context, context.getString(R.string.local_music_cloud_action_unavailable), Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            try {
                                val shareText = context.getString(R.string.share_music_text, currentMusic.artist, currentMusic.title, currentMusic.id)
                                val qqIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                    setPackage("com.tencent.mobileqq")
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(qqIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.qq_not_installed), Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, shareFailed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onSpeedChange = { speed ->
                        playerManager.setPlaybackSpeed(speed)
                        Toast.makeText(context, context.getString(R.string.playback_speed_format, speed), Toast.LENGTH_SHORT).show()
                    },
                    currentSpeed = playbackSpeed,
                    onSleepTimerChange = { minutes ->
                        if (minutes > 0 && notificationPermissionState != null && !notificationPermissionState.status.isGranted) {
                            showNotificationPermissionDialog = true
                        } else {
                            playerManager.setSleepTimer(minutes)
                            val message = if (minutes == 0) {
                                sleepTimerCancelled
                            } else {
                                val hours = minutes / 60
                                val mins = minutes % 60
                                if (hours > 0 && mins > 0) {
                                    context.getString(R.string.sleep_timer_hours_minutes, hours, mins)
                                } else if (hours > 0) {
                                    context.getString(R.string.sleep_timer_hours, hours)
                                } else {
                                    context.getString(R.string.sleep_timer_minutes, minutes)
                                }
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    currentSleepTimerMinutes = sleepTimerMinutes,
                    playlists = playlists,
                    selectedPlaylistId = selectedPlaylistId,
                    playlistFirstMusicCovers = playlistFirstMusicCovers,
                    onPlaylistSelected = { playlist ->
                        selectedPlaylistId = playlist.id
                        scope.launch {
                            try {
                                if (currentMusic.id < 0) {
                                    Toast.makeText(context, context.getString(R.string.local_music_cloud_action_unavailable), Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                val token = tokenManager.getToken()
                                Log.d("PlayerScreen", "开始添加到歌单: playlistId=${playlist.id}, musicId=${currentMusic.id}, token=$token")
                                if (token != null) {
                                    val playlistApi = PlaylistApi(token, context)
                                    val response = playlistApi.addMusicToPlaylist(playlist.id, currentMusic.id)
                                    Log.d("PlayerScreen", "API响应: success=${response.success}, message=${response.message}")
                                    if (response.success) {
                                        Toast.makeText(context, context.getString(R.string.added_to_playlist_format, playlist.name), Toast.LENGTH_SHORT).show()
                                        showShareDialog = false
                                    } else {
                                        Toast.makeText(context, response.message, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Log.e("PlayerScreen", "Token为空")
                                    Toast.makeText(context, pleaseLoginFirst, Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Log.e("PlayerScreen", "添加到歌单失败", e)
                                val errorMsg = e.message ?: "Unknown error"
                                Toast.makeText(context, context.getString(R.string.add_to_playlist_failed, errorMsg), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onCreatePlaylist = {
                        if (isLoggedIn) {
                            showCreateDialog = true
                        } else {
                            Toast.makeText(context, pleaseLoginFirst, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onGenerateVideo = {
                        showShareDialog = false
                        openVideoRenderDialog()
                    },
                    canGenerateVideo = !isLocalCurrentMusic,
                    videoRenderStatus = when {
                        videoRenderBusy -> videoRenderButtonBusy
                        videoRenderJobStatus == "pending" || videoRenderJobStatus == "processing" -> videoRenderStatusProcessing
                        else -> null
                    },
                    canDownloadVideo = videoRenderJobStatus == "done" && videoRenderJobId != null,
                    onDownloadVideo = {
                        showShareDialog = false
                        downloadRenderedVideo()
                    },
                    onTogetherClick = {
                        showShareDialog = false
                        scope.launch {
                            val token = tokenManager.getToken()
                            if (token.isNullOrBlank()) {
                                Toast.makeText(context, pleaseLoginFirst, Toast.LENGTH_SHORT).show()
                            } else if (TogetherSessionManager.isActive) {
                                val url = TogetherSessionManager.roomUrl().orEmpty()
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                clipboard.setPrimaryClip(
                                    android.content.ClipData.newPlainText(
                                        context.getString(R.string.together_title),
                                        url,
                                    )
                                )
                                Toast.makeText(
                                    context,
                                    R.string.together_link_copied,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                TogetherSessionManager.startWithCurrentMusic(
                                    context,
                                    token,
                                    currentMusic,
                                ).onSuccess { url ->
                                    val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE)
                                            as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText(
                                            context.getString(R.string.together_title),
                                            url,
                                        )
                                    )
                                    Toast.makeText(
                                        context,
                                        R.string.together_created_copied,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: context.getString(R.string.together_create_failed),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    },
                )
            }
            if (showVolumeModal) {
                PlayerVolumeModal(onDismiss = { showVolumeModal = false })
            }
            AnimatedVisibility(
                visible = showVideoRenderDialog,
                enter = LiquidCenterModalTransitions.Enter,
                exit = LiquidCenterModalTransitions.Exit,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(45f),
            ) {
                val trackDurationSec = remember(currentMusic.id, duration, currentMusic.duration) {
                    resolveTrackDurationSeconds(currentMusic.duration, duration)
                }
                VideoRenderDialog(
                    music = currentMusic,
                    trackDurationSec = trackDurationSec,
                    isVip = tokenManager.isVip(),
                    busy = videoRenderBusy,
                    sampleBackdrop = pageBackdrop,
                    onDismiss = {
                        if (!videoRenderBusy) showVideoRenderDialog = false
                    },
                    onConfirm = { startSec, watermarked ->
                        scope.launch {
                            val token = tokenManager.getToken()
                            if (token.isNullOrBlank()) {
                                Toast.makeText(context, pleaseLoginFirst, Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            videoRenderBusy = true
                            val res = VideoRenderApi(token).createJob(
                                currentMusic.id,
                                startSec,
                                watermarked
                            )
                            videoRenderBusy = false
                            if (res.success && res.data != null && res.data.jobId.isNotBlank()) {
                                videoRenderJobId = res.data.jobId
                                videoRenderJobStatus = res.data.status.ifBlank { "pending" }
                                showVideoRenderDialog = false
                                Toast.makeText(context, videoRenderSubmitted, Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    res.message.ifBlank { videoRenderCreateFailed },
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )
            }
            AnimatedVisibility(
                visible = showCreateDialog,
                enter = LiquidCenterModalTransitions.Enter,
                exit = LiquidCenterModalTransitions.Exit,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(45f),
            ) {
                PlaylistDialog(
                    title = stringResource(id = R.string.create_playlist),
                    playlistName = dialogPlaylistName,
                    sampleBackdrop = pageBackdrop,
                    onNameChange = { dialogPlaylistName = it },
                    onConfirm = {
                        scope.launch {
                            try {
                                val token = tokenManager.getToken()
                                if (token != null) {
                                    val playlistApi = PlaylistApi(token, context)
                                    val response = playlistApi.createPlaylist(dialogPlaylistName)

                                    if (response.success) {
                                        Toast.makeText(context, createSuccess, Toast.LENGTH_SHORT).show()
                                        showCreateDialog = false
                                        dialogPlaylistName = ""

                                        val newResponse = playlistApi.getMyPlaylists()
                                        if (newResponse.success) {
                                            val newPlaylists = newResponse.playlists ?: emptyList()
                                            playlists = newPlaylists

                                            val newPlaylistId = response.playlist?.id
                                            if (newPlaylistId != null) {
                                                scope.launch {
                                                    try {
                                                        val musicResponse = playlistApi.getPlaylistMusic(newPlaylistId)
                                                        if (musicResponse.success && musicResponse.musicList?.isNotEmpty() == true) {
                                                            val firstMusic = musicResponse.musicList[0]
                                                            val coverUrl = UrlConfig.getMusicCoverUrl(firstMusic.id)
                                                            val newCovers = playlistFirstMusicCovers.toMutableMap()
                                                            newCovers[newPlaylistId] = coverUrl
                                                            playlistFirstMusicCovers = newCovers
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("PlayerScreen", "加载歌单封面失败", e)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, response.message, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, pleaseLoginFirst, Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Log.e("PlayerScreen", "创建歌单失败", e)
                                val errorMsg = e.message ?: "Unknown error"
                                Toast.makeText(context, context.getString(R.string.create_playlist_failed, errorMsg), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDismiss = {
                        showCreateDialog = false
                        dialogPlaylistName = ""
                    }
                )
            }
        }
    }
    
    // 控制播放模式提示的显示
    LaunchedEffect(playModeChanged) {
        if (playModeChanged > 0) {
            val message = when (playMode) {
                PlayMode.LIST_LOOP -> listLoop
                PlayMode.SINGLE_LOOP -> singleLoop
                PlayMode.SHUFFLE -> shufflePlay
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // 通知权限请求对话框
    if (showNotificationPermissionDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showNotificationPermissionDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { showNotificationPermissionDialog = false }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(
                            colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(28.dp)
                        )
                        .padding(24.dp)
                        .width(300.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(id = R.string.notification_permission_title),
                            fontSize = 18.sp,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(id = R.string.notification_permission_message),
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(
                                        colorScheme.surfaceContainerHighest,
                                        RoundedCornerShape(24.dp)
                                    )
                                    .clickable { showNotificationPermissionDialog = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.cancel),
                                    fontSize = 15.sp,
                                    color = colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(
                                        colorScheme.primary,
                                        RoundedCornerShape(24.dp)
                                    )
                                    .clickable {
                                        notificationPermissionState?.launchPermissionRequest()
                                        showNotificationPermissionDialog = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.authorize),
                                    fontSize = 15.sp,
                                    color = colorScheme.onPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 悬浮窗权限请求对话框
    if (showOverlayPermissionDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { showOverlayPermissionDialog = false }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(
                            colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(28.dp)
                        )
                        .padding(24.dp)
                        .width(300.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(id = R.string.desktop_lyric),
                            fontSize = 18.sp,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(id = R.string.desktop_lyric_permission_message),
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(
                                        colorScheme.surfaceContainerHighest,
                                        RoundedCornerShape(24.dp)
                                    )
                                    .clickable { showOverlayPermissionDialog = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.cancel),
                                    fontSize = 15.sp,
                                    color = colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(
                                        colorScheme.primary,
                                        RoundedCornerShape(24.dp)
                                    )
                                    .clickable {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                        showOverlayPermissionDialog = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.authorize),
                                    fontSize = 15.sp,
                                    color = colorScheme.onPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerLyricsPane(
    lyrics: List<LrcLine>,
    currentProgressSeconds: State<Float>,
    isLoading: Boolean,
    isPlaying: Boolean,
    playbackSpeed: Float,
    active: Boolean,
    scrubPositionSeconds: Float?,
    listState: LazyListState,
    onLineClick: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(listState) {
        com.neko.music.util.LyricScrollManager.setPlayerPageScrollState(listState)
        onDispose {
            com.neko.music.util.LyricScrollManager.clearPlayerPageScrollState(listState)
        }
    }
    AmllLyricsView(
        lyrics = lyrics,
        currentProgressSeconds = currentProgressSeconds,
        isLoading = isLoading,
        isPlaying = isPlaying,
        playbackSpeed = playbackSpeed,
        active = active,
        scrubPositionSeconds = scrubPositionSeconds,
        modifier = modifier,
        listState = listState,
        onLineClick = onLineClick,
    )
}

@Composable
private fun PlayerTopReserve(
    isLandscape: Boolean,
    lyricsHeaderVisible: Boolean,
) {
    val collapsedHeight = if (isLandscape) 52.dp else 64.dp
    val expandedHeight = if (isLandscape) 96.dp else 100.dp
    val reserveHeight by animateDpAsState(
        targetValue = if (lyricsHeaderVisible) expandedHeight else collapsedHeight,
        animationSpec = spring(
            dampingRatio = 0.9f,
            stiffness = 360f,
            visibilityThreshold = 0.5.dp,
        ),
        label = "player_top_reserve",
    )
    Spacer(modifier = Modifier.height(reserveHeight))
}

@Composable
private fun PlayerSceneGrabber(
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
    dragModifier: Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val handleScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "player_scene_grabber_scale",
    )
    val handleColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.56f)
    } else {
        Color.Black.copy(alpha = 0.22f)
    }
    val dismissDescription = stringResource(R.string.player_dismiss)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .then(dragModifier)
            .semantics { contentDescription = dismissDescription }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(width = 68.dp, height = 5.dp)
                .graphicsLayer {
                    scaleX = handleScale
                    scaleY = handleScale
                }
                .clip(CircleShape)
                .background(handleColor),
        )
    }
}

@Composable
private fun CountBadge(
    count: Int?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    if (count == null || count <= 0) return
    Text(
        text = if (count > 99) "99+" else count.toString(),
        color = tint,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

@Composable
private fun PlayerPersistentVisualLayer(
    music: Music,
    coverUrl: String?,
    pagerState: PagerState,
    isLandscape: Boolean,
    togetherActive: Boolean,
    bottomReserve: Dp,
    artworkSharedModifier: Modifier,
    artworkExpansionProgress: () -> Float,
    onMenuClick: () -> Unit,
    onLeaveTogetherClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageProgress = when {
        pagerState.currentPage == 2 ->
            (1f + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
        pagerState.currentPage == 1 ->
            pagerState.currentPageOffsetFraction.coerceIn(0f, 1f)
        else -> 0f
    }
    val commentsProgress = when {
        pagerState.currentPage == 0 ->
            (1f - pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
        pagerState.currentPage == 1 && pagerState.currentPageOffsetFraction < 0f ->
            (-pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
        else -> 0f
    }

    val dark = isAppDarkTheme()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        val statusTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
        val navigationBottom = with(density) {
            WindowInsets.navigationBars.getBottom(density).toDp()
        }
        val contentTop = statusTop +
            (if (isLandscape) 52.dp else 64.dp) +
            (if (togetherActive) 52.dp else 0.dp)
        val availableHeight = (
            maxHeight - bottomReserve - contentTop
        ).coerceAtLeast(1.dp)

        val combinedLyricsLayout = isLandscape && maxWidth >= 840.dp
        val largeCoverSize = if (isLandscape && combinedLyricsLayout) {
            minOf(
                maxWidth * 0.23f,
                availableHeight * 0.64f,
                240.dp,
            )
        } else if (isLandscape) {
            minOf(
                maxWidth * (if (togetherActive) 0.24f else 0.30f),
                availableHeight * 0.72f,
                if (togetherActive) 190.dp else 240.dp,
            )
        } else {
            minOf(
                maxWidth * (if (togetherActive) 0.72f else 0.84f),
                availableHeight * 0.60f,
                if (togetherActive) 280.dp else 380.dp,
            )
        }

        val trailingWidth = 44.dp + if (togetherActive) 50.dp else 0.dp
        val compactCoverSize = 48.dp
        val compactTop = statusTop + 40.dp
        val compactCoverX = 16.dp
        val compactTextX = compactCoverX + compactCoverSize + 12.dp
        val compactMoreX = maxWidth - 16.dp - trailingWidth
        val compactLeaveX = compactMoreX + 50.dp
        val compactTextWidth = (compactMoreX - 12.dp - compactTextX).coerceAtLeast(1.dp)
        val commentsCoverSize = 46.dp
        val commentsCoverX = 24.dp
        val commentsCoverY = (maxHeight - navigationBottom - 67.dp).coerceAtLeast(0.dp)
        val commentsTextX = commentsCoverX + commentsCoverSize + 11.dp
        val commentsTextY = commentsCoverY + 7.dp
        val commentsTextWidth = (maxWidth - commentsTextX - 100.dp).coerceAtLeast(1.dp)

        val largeCoverX: Dp
        val largeCoverY: Dp
        val largeTextX: Dp
        val largeTextY: Dp
        val largeBlockWidth: Dp
        if (isLandscape) {
            if (combinedLyricsLayout) {
                largeCoverX = 40.dp
                val identityHeight = 54.dp
                val identityGap = 16.dp
                val totalHeight = largeCoverSize + identityGap + identityHeight
                largeCoverY = contentTop + ((availableHeight - totalHeight) / 2f)
                    .coerceAtLeast(0.dp)
                largeTextX = largeCoverX
                largeTextY = largeCoverY + largeCoverSize + identityGap
                largeBlockWidth = (largeCoverSize + 72.dp)
                    .coerceAtLeast(1.dp)
            } else {
                val blockWidth = minOf(
                    (maxWidth - 64.dp).coerceAtLeast(1.dp),
                    760.dp,
                )
                largeCoverX = ((maxWidth - blockWidth) / 2f).coerceAtLeast(0.dp)
                largeCoverY = contentTop + ((availableHeight - largeCoverSize) / 2f)
                    .coerceAtLeast(0.dp)
                largeTextX = largeCoverX + largeCoverSize + 30.dp
                largeTextY = largeCoverY + ((largeCoverSize - 54.dp) / 2f).coerceAtLeast(0.dp)
                largeBlockWidth = (blockWidth - largeCoverSize - 30.dp)
                    .coerceAtLeast(1.dp)
            }
        } else {
            val identityHeight = 54.dp
            val totalHeight = largeCoverSize + 30.dp + identityHeight
            largeCoverX = (maxWidth - largeCoverSize) / 2f
            largeCoverY = contentTop + ((availableHeight - totalHeight) / 2f)
                .coerceAtLeast(0.dp)
            largeTextX = largeCoverX
            largeTextY = largeCoverY + largeCoverSize + 30.dp
            largeBlockWidth = largeCoverSize
        }
        val largeTextWidth = (largeBlockWidth - trailingWidth - 12.dp)
            .coerceAtLeast(1.dp)

        val movingToComments = commentsProgress > 0.001f
        val coverSize = if (movingToComments) {
            lerp(largeCoverSize, commentsCoverSize, commentsProgress)
        } else {
            lerp(largeCoverSize, compactCoverSize, pageProgress)
        }
        val coverX = if (movingToComments) {
            lerp(largeCoverX, commentsCoverX, commentsProgress)
        } else {
            lerp(largeCoverX, compactCoverX, pageProgress)
        }
        val coverY = if (movingToComments) {
            lerp(largeCoverY, commentsCoverY, commentsProgress)
        } else {
            lerp(largeCoverY, compactTop, pageProgress)
        }
        val textX = if (movingToComments) {
            lerp(largeTextX, commentsTextX, commentsProgress)
        } else {
            lerp(largeTextX, compactTextX, pageProgress)
        }
        val textY = if (movingToComments) {
            lerp(largeTextY, commentsTextY, commentsProgress)
        } else {
            lerp(largeTextY, compactTop + 3.dp, pageProgress)
        }
        val textWidth = if (movingToComments) {
            lerp(largeTextWidth, commentsTextWidth, commentsProgress)
        } else {
            lerp(largeTextWidth, compactTextWidth, pageProgress)
        }
        val largeMoreX = largeTextX + largeBlockWidth - trailingWidth
        val largeLeaveX = largeMoreX + 50.dp
        val largeMoreY = largeTextY + 5.dp
        val moreX = lerp(largeMoreX, compactMoreX, pageProgress)
        val leaveX = lerp(largeLeaveX, compactLeaveX, pageProgress)
        val moreY = lerp(largeMoreY, compactTop + 2.dp, pageProgress)
        val actionsAlpha = (1f - commentsProgress).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .offset(x = coverX, y = coverY)
                .size(coverSize)
                .then(artworkSharedModifier),
        ) {
            CoverImage(
                music = music,
                modifier = Modifier.fillMaxSize(),
                coverUrlOverride = coverUrl,
                artworkExpansionProgress = artworkExpansionProgress,
                artworkCornerRadius = if (movingToComments) {
                    lerp(18.dp, 20.dp, commentsProgress)
                } else {
                    lerp(18.dp, 10.dp, pageProgress)
                },
                artworkElevation = if (movingToComments) {
                    lerp(24.dp, 0.dp, commentsProgress)
                } else {
                    lerp(24.dp, 4.dp, pageProgress)
                },
            )
        }

        PlayerTrackText(
            music = music,
            modifier = Modifier
                .offset(x = textX, y = textY)
                .width(textWidth),
            alignStart = true,
            titleSize = if (movingToComments) {
                lerp(22.sp, 14.sp, commentsProgress)
            } else {
                lerp(22.sp, 16.sp, pageProgress)
            },
            titleLineHeight = if (movingToComments) {
                lerp(27.sp, 18.sp, commentsProgress)
            } else {
                lerp(27.sp, 20.sp, pageProgress)
            },
            artistSize = if (movingToComments) {
                lerp(15.sp, 11.5.sp, commentsProgress)
            } else {
                lerp(15.sp, 12.sp, pageProgress)
            },
            artistLineHeight = if (movingToComments) {
                lerp(19.sp, 15.sp, commentsProgress)
            } else {
                lerp(19.sp, 16.sp, pageProgress)
            },
        )

        AnimatedVisibility(
            visible = togetherActive && actionsAlpha > 0.01f,
            enter = fadeIn(tween(180)) +
                scaleIn(initialScale = 0.82f, animationSpec = tween(220)),
            exit = fadeOut(tween(140)) +
                scaleOut(targetScale = 0.82f, animationSpec = tween(180)),
            modifier = Modifier
                .offset(x = leaveX, y = moreY)
                .size(44.dp)
                .graphicsLayer { alpha = actionsAlpha },
        ) {
            PlayerTrackCircleActionButton(
                isDarkTheme = dark,
                onClick = onLeaveTogetherClick,
                iconRes = R.drawable.logout,
                contentDescription = stringResource(R.string.together_leave),
            )
        }

        if (actionsAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .offset(x = moreX, y = moreY)
                    .size(44.dp)
                    .graphicsLayer { alpha = actionsAlpha },
            ) {
                PlayerTrackMoreButton(
                    isDarkTheme = dark,
                    onClick = onMenuClick,
                )
            }
        }
    }
}

@Composable
private fun PlayerTrackText(
    music: Music,
    modifier: Modifier,
    alignStart: Boolean,
    titleSize: TextUnit,
    titleLineHeight: TextUnit,
    artistSize: TextUnit,
    artistLineHeight: TextUnit,
) {
    val scheme = MaterialTheme.colorScheme
    val useLiquidStyle = isLiquidGlassEffectActive(LocalLiquidLayerBackdrop.current)
    val dark = isAppDarkTheme()
    val title = if (useLiquidStyle) {
        if (dark) Color.White else Color(0xFF151518)
    } else {
        scheme.onSurface
    }
    val secondary = if (useLiquidStyle) {
        if (dark) Color.White.copy(alpha = 0.58f) else Color(0xFF6D6D72)
    } else {
        scheme.onSurfaceVariant
    }
    val contentAlignment = if (alignStart) Alignment.Start else Alignment.CenterHorizontally
    val textAlignment = if (alignStart) TextAlign.Start else TextAlign.Center

    Column(
        modifier = modifier,
        horizontalAlignment = contentAlignment,
    ) {
        Text(
            text = music.title,
            color = title,
            fontSize = titleSize,
            lineHeight = titleLineHeight,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.45).sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = textAlignment,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = music.artist,
            color = secondary,
            fontSize = artistSize,
            lineHeight = artistLineHeight,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = textAlignment,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(
                    initialDelayMillis = 1_000,
                ),
        )
    }
}

@Composable
private fun PlayerTrackMoreButton(
    isDarkTheme: Boolean,
    onClick: () -> Unit,
) {
    PlayerTrackCircleActionButton(
        isDarkTheme = isDarkTheme,
        onClick = onClick,
        iconRes = R.drawable.ic_more_horizontal,
        contentDescription = stringResource(id = R.string.more),
    )
}

@Composable
private fun PlayerTrackCircleActionButton(
    isDarkTheme: Boolean,
    onClick: () -> Unit,
    iconRes: Int,
    contentDescription: String,
) {
    val tint = if (isDarkTheme) Color.White else Color(0xFF17171A)
    val background = if (isDarkTheme) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            NekoIcon(
                drawableRes = iconRes,
                contentDescription = contentDescription,
                tint = tint.copy(alpha = 0.88f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun CoverImage(
    music: Music,
    modifier: Modifier = Modifier,
    coverUrlOverride: String? = null,
    artworkExpansionProgress: (() -> Float)? = null,
    artworkCornerRadius: Dp? = null,
    artworkElevation: Dp? = null,
) {
    val context = LocalContext.current
    val musicApi = remember { MusicApi(context) }
    var fetchedCoverUrl by remember(music.id) { mutableStateOf<String?>(null) }
    val displayCoverUrl = coverUrlOverride ?: fetchedCoverUrl
    val coverModel = remember(displayCoverUrl, context) {
        displayCoverUrl?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .precision(Precision.INEXACT)
                .memoryCacheKey(playerCoverMemoryKey(url))
                .build()
        }
    }
    val scheme = MaterialTheme.colorScheme
    val useLiquidStyle = isLiquidGlassEffectActive(LocalLiquidLayerBackdrop.current)
    val isDarkTheme = isAppDarkTheme()
    val artworkCornerDp = artworkCornerRadius?.value ?: if (useLiquidStyle) 18f else 24f
    val artworkShape = RoundedCornerShape(artworkCornerDp.dp)
    val artworkClipModifier = if (artworkExpansionProgress == null) {
        Modifier.clip(artworkShape)
    } else {
        Modifier.graphicsLayer {
            val cornerDp = 6f + (artworkCornerDp - 6f) *
                artworkExpansionProgress().coerceIn(0f, 1f)
            shape = RoundedCornerShape(cornerDp.dp)
            clip = true
        }
    }
    val artworkModifier = if (useLiquidStyle) {
        Modifier
            .shadow(
                elevation = artworkElevation ?: 24.dp,
                shape = artworkShape,
                spotColor = Color.Black.copy(alpha = if (isDarkTheme) 0.50f else 0.24f),
                ambientColor = Color.Black.copy(alpha = 0.16f),
            )
            .then(artworkClipModifier)
            .background(
                if (!displayCoverUrl.isNullOrEmpty()) {
                    Color.Transparent
                } else if (isDarkTheme) {
                    Color(0xFF1B1B1E)
                } else {
                    Color(0xFFE9E9ED)
                },
            )
            .border(
                width = 0.6.dp,
                color = if (isDarkTheme) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.72f),
                shape = artworkShape,
            )
    } else {
        Modifier
            .shadow(
                elevation = artworkElevation ?: 10.dp,
                shape = artworkShape,
                spotColor = Color.Black.copy(alpha = if (isDarkTheme) 0.32f else 0.14f),
                ambientColor = Color.Black.copy(alpha = 0.06f),
            )
            .then(artworkClipModifier)
            .background(
                if (!displayCoverUrl.isNullOrEmpty()) {
                    Color.Transparent
                } else {
                    scheme.surfaceContainerHighest
                },
            )
    }

    LaunchedEffect(music.id, coverUrlOverride) {
        if (coverUrlOverride != null) {
            fetchedCoverUrl = coverUrlOverride
        } else {
            fetchedCoverUrl = musicApi.getMusicCoverUrl(music)
        }
    }

    Box(
        modifier = modifier
            .then(artworkModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (!displayCoverUrl.isNullOrEmpty()) {
            AsyncImage(
                model = coverModel,
                contentDescription = stringResource(id = R.string.cover),
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            Image(
                painter = painterResource(R.drawable.music),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
        }

        if (useLiquidStyle) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
                            endY = 180f,
                        ),
                    ),
            )
        }
    }
}

        @Composable
        fun MusicInfoWithFavorite(
                    music: Music,
                    isFavorite: Boolean,
                    onFavoriteClick: () -> Unit,
                    isLoggedIn: Boolean = true
        ) {
            val isDarkTheme = isSystemInDarkTheme()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                // 歌名和歌手 - 居中
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        val shouldScroll by remember { derivedStateOf { music.title.length > 15 } }
                        
                        LaunchedEffect(shouldScroll, music.title) {
                            if (shouldScroll) {
                                while (true) {
                                    delay(3000)
                                    scrollState.animateScrollTo(scrollState.maxValue, animationSpec = tween(5000))
                                    delay(3000)
                                    scrollState.animateScrollTo(0, animationSpec = tween(5000))
                                }
                            }
                        }
                        
                        Text(
                            text = music.title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollState, enabled = false)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = music.artist,
                        fontSize = 16.sp,
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // 收藏按钮 - 右侧固定位置
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp)
                ) {
                    NekoIcon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) stringResource(id = R.string.favorite) else stringResource(id = R.string.unfavorite),
                        tint = if (isFavorite) {
                            RoseRed
                        } else {
                            if (isDarkTheme) Color.White.copy(alpha = 0.4f) else Color.Gray
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        @Composable
fun MusicInfo(
            music: Music,
            isFavorite: Boolean,
            onFavoriteClick: () -> Unit
        ) {
            val isDarkTheme = isSystemInDarkTheme()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = music.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = music.artist,
                    fontSize = 16.sp,
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    NekoIcon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) stringResource(id = R.string.favorite) else stringResource(id = R.string.unfavorite),
                        tint = if (isFavorite) {
                            RoseRed
                        } else {
                            if (isDarkTheme) Color.White.copy(alpha = 0.4f) else Color.Gray
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

@Composable
fun PlayerMetadataRow(
    music: Music,
    coverUrl: String?,
    isFavorite: Boolean,
    favoriteCount: Int?,
    commentCount: Int,
    audioQuality: AudioQuality,
    maxQuality: String,
    isDesktopLyricEnabled: Boolean,
    showDesktopLyric: Boolean,
    onFavoriteClick: () -> Unit,
    onCommentClick: () -> Unit,
    onQualitySelected: (AudioQuality) -> Unit,
    onDesktopLyricClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val titleColor = if (isDarkTheme) Color.White else Color(0xFF17171A)
    val secondaryColor = if (isDarkTheme) Color.White.copy(alpha = 0.56f) else Color(0xFF6E6E73)
    val actionBackground = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val thumbShape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(thumbShape)
                .background(if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                .border(
                    width = 0.5.dp,
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                    shape = thumbShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.music),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = music.title,
                color = titleColor,
                fontSize = 17.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = music.artist,
                    color = secondaryColor,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(8.dp))
                AudioQualitySelector(
                    selected = audioQuality,
                    maxQuality = maxQuality,
                    onSelected = onQualitySelected,
                    compact = true,
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                PlayerMetaActionButton(
                    onClick = onFavoriteClick,
                    background = actionBackground,
                ) {
                    NekoIcon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(if (isFavorite) R.string.favorite else R.string.unfavorite),
                        tint = if (isFavorite) RoseRed else secondaryColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
                CountBadge(
                    count = favoriteCount,
                    tint = RoseRed,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 1.dp, y = (-1).dp),
                )
            }

            Box {
                PlayerMetaActionButton(
                    onClick = onCommentClick,
                    background = actionBackground,
                ) {
                    NekoSymbolIcon(
                        symbol = NekoSymbol.Comment,
                        contentDescription = stringResource(id = R.string.comment_title),
                        color = secondaryColor,
                        modifier = Modifier.size(19.dp),
                    )
                }
                CountBadge(
                    count = commentCount,
                    tint = RoseRed,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 1.dp, y = (-1).dp),
                )
            }

            if (showDesktopLyric) {
                PlayerMetaActionButton(
                    onClick = onDesktopLyricClick,
                    background = if (isDesktopLyricEnabled) {
                        RoseRed.copy(alpha = if (isDarkTheme) 0.20f else 0.12f)
                    } else {
                        actionBackground
                    },
                ) {
                    NekoIcon(
                        drawableRes = R.drawable.ic_lyric_toggle,
                        contentDescription = stringResource(id = R.string.desktop_lyric),
                        tint = if (isDesktopLyricEnabled) RoseRed else secondaryColor,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerMetaActionButton(
    onClick: () -> Unit,
    background: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

        @Composable
        fun SongActionButtons(
            isFavorite: Boolean,
            onFavoriteClick: () -> Unit,
            isDesktopLyricEnabled: Boolean = false,
            onDesktopLyricClick: () -> Unit = {}
        ) {
            val isDarkTheme = isSystemInDarkTheme()
            val favColor = if (isFavorite) {
                RoseRed
            } else {
                if (isDarkTheme) Color.White.copy(alpha = 0.4f) else Color.Gray
            }
            val desktopLyricBg = if (isDesktopLyricEnabled) {
                if (isDarkTheme) SakuraPink.copy(alpha = 0.15f) else RoseRed.copy(alpha = 0.15f)
            } else Color.Transparent
            val desktopLyricText = if (isDesktopLyricEnabled) {
                if (isDarkTheme) SakuraPink else RoseRed
            } else {
                if (isDarkTheme) Color.White.copy(alpha = 0.4f) else Color.Gray
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 收藏按钮
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    NekoIcon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) stringResource(id = R.string.favorite) else stringResource(id = R.string.unfavorite),
                        tint = favColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 桌面歌词按钮
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(desktopLyricBg)
                        .clickable(onClick = onDesktopLyricClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "词",
                        fontSize = 14.sp,
                        fontWeight = if (isDesktopLyricEnabled) FontWeight.Bold else FontWeight.Normal,
                        color = desktopLyricText
                    )
                }
            }
        }

        @Composable
fun LyricsSongInfo(
            music: Music,
            modifier: Modifier = Modifier
        ) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                // 歌名和歌手 - 左侧
                Column(
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    // 歌名 - 可横向滚动
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    ) {
                        Text(
                            text = music.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 歌手
                    Text(
                        text = music.artist,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }

                // 收藏按钮 - 右侧固定位置
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp)
                ) {
                    NekoIcon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(id = R.string.unfavorite),
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

@Composable
private fun PlayerMiniPlayerBar(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean,
    positionMillis: State<Long>,
    durationMillis: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    musicFileUrl: String?,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val useLiquidStyle = isLiquidGlassEffectActive(LocalLiquidLayerBackdrop.current)
    val baseColor = if (isDarkTheme) Color(0xFF09090B) else Color(0xFFF5F5F7)
    val dockShape = RoundedCornerShape(28.dp)
    val dockColor = if (useLiquidStyle) {
        if (isDarkTheme) Color(0xFF121216).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.84f)
    } else {
        scheme.surfaceContainerHigh
    }
    val borderColor = if (useLiquidStyle) {
        if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.92f)
    } else {
        scheme.outlineVariant
    }
    val primary = if (useLiquidStyle) {
        if (isDarkTheme) Color.White else Color(0xFF17171A)
    } else {
        scheme.onSurface
    }
    val progressTarget = if (durationMillis > 0) {
        (positionMillis.value.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = 160),
        label = "mini_player_progress",
    )

    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        baseColor.copy(alpha = 0.48f),
                        baseColor.copy(alpha = 0.86f),
                    ),
                ),
            )
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp)
                .clip(dockShape)
                .background(dockColor)
                .border(0.6.dp, borderColor, dockShape),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPlayer)
                    .padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 封面与歌曲文字来自持久播放视觉层，这里仅预留对应布局空间。
                Spacer(modifier = Modifier.width(57.dp))
                Spacer(modifier = Modifier.weight(1f))
                PlayerTransportButton(
                    onClick = onPlayPauseClick,
                    size = 40.dp,
                    background = if (useLiquidStyle) {
                        if (isDarkTheme) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.045f)
                    } else {
                        scheme.surfaceContainer
                    },
                ) {
                    when {
                        isLoading -> MusicLoadingIndicator(
                            color = primary,
                            modifier = Modifier.size(width = 22.dp, height = 18.dp),
                        )
                        musicFileUrl == null -> NekoIcon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(id = R.string.loading),
                            tint = primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp),
                        )
                        else -> NekoIcon(
                            drawableRes = if (isPlaying) R.drawable.pause else R.drawable.play,
                            contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                            tint = primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                PlayerTransportButton(
                    onClick = onNextClick,
                    size = 38.dp,
                ) {
                    NekoIcon(
                        drawableRes = R.drawable.next_song,
                        contentDescription = stringResource(id = R.string.next),
                        tint = primary.copy(alpha = 0.86f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.12f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(2.dp)
                        .background(primary.copy(alpha = 0.66f)),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun PlayerBottomBar(
    modifier: Modifier = Modifier,
    isLandscape: Boolean,
    compactForComments: Boolean,
    isDarkTheme: Boolean,
    coverUrl: String?,
    musicTitle: String,
    musicArtist: String,
    positionMillis: State<Long>,
    durationMillis: Long,
    isLoading: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    musicFileUrl: String?,
    playMode: PlayMode,
    isFavorite: Boolean,
    audioQuality: AudioQuality,
    maxQuality: String,
    onProgressChange: (Float) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onPlaylistClick: () -> Unit,
    onVolumeClick: () -> Unit,
    onPlayModeClick: () -> Unit,
    onQualitySelected: (AudioQuality) -> Unit,
    onOpenPlayer: () -> Unit,
    onScrubPositionChange: (Float?) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val useLiquidStyle = isLiquidGlassEffectActive(LocalLiquidLayerBackdrop.current)
    val baseColor = if (isDarkTheme) Color(0xFF09090B) else Color(0xFFF5F5F7)

    if (compactForComments) {
        PlayerMiniPlayerBar(
            modifier = modifier,
            isDarkTheme = isDarkTheme,
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            isPlaying = isPlaying,
            isLoading = isLoading || isBuffering,
            musicFileUrl = musicFileUrl,
            onPlayPauseClick = onPlayPauseClick,
            onNextClick = onNextClick,
            onOpenPlayer = onOpenPlayer,
        )
        return
    }

    val backgroundBrush = if (useLiquidStyle) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                baseColor.copy(alpha = 0.34f),
                baseColor.copy(alpha = 0.96f),
            ),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                scheme.surfaceContainer.copy(alpha = 0.88f),
                scheme.surfaceContainer,
            ),
        )
    }
    Box(
        modifier = modifier
            .background(backgroundBrush)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        if (isLandscape) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ProgressSlider(
                            positionMillis = positionMillis,
                            durationMillis = durationMillis,
                            isLoading = isLoading,
                            compact = true,
                            audioQuality = audioQuality,
                            maxQuality = maxQuality,
                            onQualitySelected = onQualitySelected,
                            onProgressChange = onProgressChange,
                            onScrubPositionChange = onScrubPositionChange,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        PlayerUtilityRow(
                            isDarkTheme = isDarkTheme,
                            compact = true,
                            onPlaylistClick = onPlaylistClick,
                            onVolumeClick = onVolumeClick,
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        PlaybackControls(
                            isPlaying = isPlaying,
                            isLoading = isLoading || isBuffering,
                            musicFileUrl = musicFileUrl,
                            playMode = playMode,
                            isFavorite = isFavorite,
                            compact = true,
                            onPlayPauseClick = onPlayPauseClick,
                            onPreviousClick = onPreviousClick,
                            onNextClick = onNextClick,
                            onFavoriteClick = onFavoriteClick,
                            onPlayModeClick = onPlayModeClick,
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp),
            ) {
                ProgressSlider(
                    positionMillis = positionMillis,
                    durationMillis = durationMillis,
                    isLoading = isLoading,
                    audioQuality = audioQuality,
                    maxQuality = maxQuality,
                    onQualitySelected = onQualitySelected,
                    onProgressChange = onProgressChange,
                    onScrubPositionChange = onScrubPositionChange,
                )
                Spacer(modifier = Modifier.height(16.dp))
                PlaybackControls(
                    isPlaying = isPlaying,
                    isLoading = isLoading || isBuffering,
                    musicFileUrl = musicFileUrl,
                    playMode = playMode,
                    isFavorite = isFavorite,
                    onPlayPauseClick = onPlayPauseClick,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    onFavoriteClick = onFavoriteClick,
                    onPlayModeClick = onPlayModeClick,
                )
                Spacer(modifier = Modifier.height(18.dp))
                PlayerUtilityRow(
                    isDarkTheme = isDarkTheme,
                    onPlaylistClick = onPlaylistClick,
                    onVolumeClick = onVolumeClick,
                )
            }
        }
    }
}

@Composable
private fun PlayerUtilityRow(
    isDarkTheme: Boolean,
    compact: Boolean = false,
    onPlaylistClick: () -> Unit,
    onVolumeClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val useLiquidStyle = isLiquidGlassEffectActive(LocalLiquidLayerBackdrop.current)
    val muted = if (useLiquidStyle) {
        if (isDarkTheme) Color.White.copy(alpha = 0.46f) else Color(0xFF17171A).copy(alpha = 0.46f)
    } else {
        scheme.onSurfaceVariant
    }
    val actionSize = 48.dp
    val actionIconSize = if (useLiquidStyle) {
        if (compact) 17.dp else 18.dp
    } else {
        21.dp
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = actionSize),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(actionSize).clickable(onClick = onVolumeClick),
            contentAlignment = Alignment.Center,
        ) {
            NekoSymbolIcon(
                symbol = NekoSymbol.Volume,
                contentDescription = stringResource(R.string.player_volume_settings),
                color = muted,
                modifier = Modifier.size(actionIconSize),
            )
        }
        Box(
            modifier = Modifier
                .size(actionSize)
                .clickable(onClick = onPlaylistClick),
            contentAlignment = Alignment.Center,
        ) {
            NekoIcon(
                drawableRes = R.drawable.playlist,
                contentDescription = stringResource(id = R.string.playlist),
                tint = muted,
                modifier = Modifier.size(actionIconSize),
            )
        }

    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProgressSlider(
    positionMillis: State<Long>,
    durationMillis: Long,
    isLoading: Boolean,
    compact: Boolean = false,
    audioQuality: AudioQuality? = null,
    maxQuality: String = "",
    onQualitySelected: (AudioQuality) -> Unit = {},
    onProgressChange: (Float) -> Unit,
    onScrubPositionChange: (Float?) -> Unit = {},
) {
    val position = positionMillis.value
    var scrubProgress by remember(durationMillis) { mutableStateOf<Float?>(null) }
    val reportedProgress = if (durationMillis > 0) {
        (position.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progress = scrubProgress ?: reportedProgress
    val currentTime = formatTime(scrubProgress?.let { (it * durationMillis).toLong() } ?: position)
    val totalTime = formatTime(durationMillis)
    val scheme = MaterialTheme.colorScheme
    val liquidBackdrop = LocalLiquidLayerBackdrop.current
    val useLiquidStyle = isLiquidGlassEffectActive(liquidBackdrop)
    val isDarkTheme = isAppDarkTheme()
    val ink = if (useLiquidStyle) {
        if (isDarkTheme) Color.White else Color(0xFF17171A)
    } else {
        scheme.primary
    }
    val timeColor = if (useLiquidStyle) {
        if (isDarkTheme) Color.White.copy(alpha = 0.52f) else Color(0xFF6E6E73)
    } else {
        scheme.onSurfaceVariant
    }
    val inactiveTrackColor = if (useLiquidStyle) {
        ink.copy(alpha = 0.16f)
    } else {
        scheme.surfaceVariant
    }
    val latestScrubCallback by rememberUpdatedState(onScrubPositionChange)
    val haptics = LocalHapticFeedback.current
    DisposableEffect(durationMillis) {
        onDispose { latestScrubCallback(null) }
    }
    fun previewProgress(value: Float) {
        scrubProgress = value
        latestScrubCallback(value * durationMillis / 1000f)
    }
    fun finishScrubbing() {
        scrubProgress?.let {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onProgressChange(it)
        }
        scrubProgress = null
        latestScrubCallback(null)
    }
    val sliderState = remember { SliderState(value = progress.coerceIn(0f, 1f)) }
    androidx.compose.runtime.SideEffect {
        sliderState.value = progress.coerceIn(0f, 1f)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (useLiquidStyle && liquidBackdrop != null) {
            LiquidGlassSlider(
                value = progress,
                onValueChange = ::previewProgress,
                onValueChangeFinished = ::finishScrubbing,
                backdrop = liquidBackdrop,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 36.dp else 40.dp),
            )
        } else {
            val sliderHeight = if (compact) 28.dp else 32.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sliderHeight),
                contentAlignment = Alignment.Center,
            ) {
                LinearWavyProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .height(if (compact) 18.dp else 20.dp),
                    color = ink.copy(alpha = if (isLoading) 0.36f else 0.92f),
                    trackColor = if (isLoading) {
                        scheme.surfaceVariant.copy(alpha = 0.55f)
                    } else {
                        inactiveTrackColor
                    },
                )

                Slider(
                    state = sliderState,
                    onValueChange = { value ->
                        sliderState.value = value
                        previewProgress(value)
                    },
                    onValueChangeFinished = ::finishScrubbing,
                    enabled = !isLoading,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                        thumbColor = ink.copy(alpha = if (isLoading) 0.42f else 1f),
                        disabledActiveTrackColor = Color.Transparent,
                        disabledInactiveTrackColor = Color.Transparent,
                        disabledThumbColor = ink.copy(alpha = 0.34f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sliderHeight),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .padding(horizontal = 2.dp),
        ) {
            Text(
                text = currentTime,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
                color = timeColor,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            if (audioQuality != null) {
                AudioQualitySelector(
                    selected = audioQuality,
                    maxQuality = maxQuality,
                    onSelected = onQualitySelected,
                    compact = true,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Text(
                text = totalTime,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
                color = timeColor,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun TogetherMemberAvatarStrip(
    members: List<com.neko.music.data.api.TogetherMember>,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val useLiquidStyle = isLiquidGlassEffectActive(LocalLiquidLayerBackdrop.current)
    val dark = isAppDarkTheme()
    val onlineMembers = remember(members) { members.filter { it.online } }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(onlineMembers, key = { it.userId }) { member ->
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (member.owner) 2.dp else 1.dp,
                            color = when {
                                member.owner -> RoseRed
                                useLiquidStyle && dark -> Color.White.copy(alpha = 0.34f)
                                useLiquidStyle -> Color.Black.copy(alpha = 0.16f)
                                else -> scheme.outlineVariant
                            },
                            shape = CircleShape,
                        )
                        .alpha(if (member.online) 1f else 0.48f),
                ) {
                    AsyncImage(
                        model = UrlConfig.getUserAvatarUrl(member.userId),
                        contentDescription = member.nickname,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${onlineMembers.size}/10" + if (connected) "" else " · …",
            color = if (useLiquidStyle && dark) Color.White.copy(alpha = 0.56f)
            else scheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
    }
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    musicFileUrl: String?,
    playMode: PlayMode,
    isFavorite: Boolean,
    compact: Boolean = false,
    showPlayMode: Boolean = true,
    showFavorite: Boolean = true,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onPlayModeClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val useLiquidStyle = isLiquidGlassEffectActive(LocalLiquidLayerBackdrop.current)
    val isDarkTheme = isAppDarkTheme()
    val iconColor = if (useLiquidStyle) {
        if (isDarkTheme) Color.White.copy(alpha = 0.90f) else Color(0xFF17171A)
    } else {
        scheme.onSurface
    }
    val secondaryIconColor = if (useLiquidStyle) {
        iconColor.copy(alpha = 0.78f)
    } else {
        scheme.onSurfaceVariant
    }
    val transportSize = if (compact) 38.dp else 42.dp
    val playSize = if (compact) 56.dp else 64.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showPlayMode) {
            PlayerTransportButton(
                onClick = onPlayModeClick,
                size = transportSize,
            ) {
                val symbol = when (playMode) {
                    PlayMode.LIST_LOOP -> NekoSymbol.Repeat
                    PlayMode.SINGLE_LOOP -> NekoSymbol.RepeatOne
                    PlayMode.SHUFFLE -> NekoSymbol.Shuffle
                }
                NekoSymbolIcon(
                    symbol = symbol,
                    contentDescription = stringResource(id = R.string.player_play_mode),
                    color = secondaryIconColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(modifier = Modifier.size(transportSize))
        }

        PlayerTransportButton(
            onClick = onPreviousClick,
            size = transportSize,
        ) {
            NekoIcon(
                drawableRes = R.drawable.previous_song,
                contentDescription = stringResource(id = R.string.previous),
                tint = iconColor,
                modifier = Modifier.size(if (compact) 22.dp else 24.dp),
            )
        }

        val materialPlayButtonColor = if (musicFileUrl == null) {
            scheme.primary.copy(alpha = 0.38f)
        } else {
            scheme.primary
        }
        val playIconColor = if (useLiquidStyle) {
            if (isDarkTheme) Color.White else Color(0xFF17171A)
        } else {
            scheme.onPrimary
        }
        if (useLiquidStyle) {
            val glass = LiquidGlassDefaults.playerBottomChrome
            val interactionSource = remember {
                androidx.compose.foundation.interaction.MutableInteractionSource()
            }
            GlassSurface(
                modifier = Modifier.size(playSize),
                shape = CircleShape,
                backgroundAlpha = glass.tint.background(isDarkTheme),
                borderAlpha = glass.tint.border(isDarkTheme),
                highlightAlpha = glass.tint.highlight(isDarkTheme),
                borderColor = if (isDarkTheme) Color.White else Color(0xFF1C1C1E),
                liquidBlur = glass.liquid.blur,
                liquidLensHeight = glass.liquid.lensHeight,
                liquidLensAmount = glass.liquid.lensAmount,
                surfaceTint = if (musicFileUrl == null) {
                    scheme.primary.copy(alpha = 0.12f)
                } else {
                    null
                },
                material3Shape = CircleShape,
                material3Container = scheme.surfaceContainerHigh,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            enabled = !isLoading && musicFileUrl != null,
                            onClick = onPlayPauseClick,
                            indication = null,
                            interactionSource = interactionSource,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    PlaybackButtonContent(
                        isLoading = isLoading,
                        musicFileUrl = musicFileUrl,
                        isPlaying = isPlaying,
                        compact = compact,
                        iconColor = playIconColor,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(playSize)
                    .clickable(
                        enabled = !isLoading && musicFileUrl != null,
                        onClick = onPlayPauseClick,
                        indication = null,
                        interactionSource = remember {
                            androidx.compose.foundation.interaction.MutableInteractionSource()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_gear_play_button_bg),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(materialPlayButtonColor),
                )
                PlaybackButtonContent(
                    isLoading = isLoading,
                    musicFileUrl = musicFileUrl,
                    isPlaying = isPlaying,
                    compact = compact,
                    iconColor = playIconColor,
                )
            }
        }

        PlayerTransportButton(
            onClick = onNextClick,
            size = transportSize,
        ) {
            NekoIcon(
                drawableRes = R.drawable.next_song,
                contentDescription = stringResource(id = R.string.next),
                tint = iconColor,
                modifier = Modifier.size(if (compact) 22.dp else 24.dp),
            )
        }

        if (showFavorite) {
            PlayerTransportButton(
                onClick = onFavoriteClick,
                size = transportSize,
            ) {
                NekoIcon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(if (isFavorite) R.string.favorite else R.string.unfavorite),
                    tint = when {
                        isFavorite -> RoseRed
                        useLiquidStyle -> iconColor.copy(alpha = 0.56f)
                        else -> secondaryIconColor
                    },
                    modifier = Modifier.size(if (compact) 21.dp else 23.dp),
                )
            }
        } else {
            Spacer(modifier = Modifier.size(transportSize))
        }
    }
}

@Composable
private fun PlaybackButtonContent(
    isLoading: Boolean,
    musicFileUrl: String?,
    isPlaying: Boolean,
    compact: Boolean,
    iconColor: Color,
) {
    when {
        isLoading -> {
            MusicLoadingIndicator(
                color = iconColor,
                modifier = Modifier.size(width = 26.dp, height = 22.dp),
            )
        }

        musicFileUrl == null -> {
            NekoIcon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(id = R.string.loading),
                tint = iconColor.copy(alpha = 0.55f),
                modifier = Modifier.size(28.dp),
            )
        }

        else -> {
            NekoIcon(
                drawableRes = if (isPlaying) R.drawable.pause else R.drawable.play,
                contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                tint = iconColor,
                modifier = Modifier.size(if (compact) 26.dp else 30.dp),
            )
        }
    }
}

@Composable
private fun PlayerTransportButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    background: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun MusicLoadingIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "music_loading")
    val bar1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 640, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "music_loading_bar1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 640, delayMillis = 140, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "music_loading_bar2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 640, delayMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "music_loading_bar3"
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(bar1, bar2, bar3).forEach { bar ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(22.dp * (0.42f + bar * 0.58f))
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun AudioQualitySelector(
    selected: AudioQuality,
    maxQuality: String,
    onSelected: (AudioQuality) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "audio_quality_press",
    )
    val scheme = MaterialTheme.colorScheme
    val useLiquidStyle = isLiquidGlassEffectActive(LocalLiquidLayerBackdrop.current)
    val isDarkTheme = isAppDarkTheme()
    val foreground = if (!useLiquidStyle) {
        scheme.onSurface.copy(alpha = 0.72f)
    } else if (isDarkTheme) {
        Color.White.copy(alpha = 0.82f)
    } else {
        Color(0xFF3A3A3C).copy(alpha = 0.82f)
    }
    val pillBackground = if (!useLiquidStyle) {
        scheme.surfaceContainerHigh
    } else {
        if (isDarkTheme) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.05f)
    }
    val pillShape = RoundedCornerShape(if (compact) 7.dp else 8.dp)
    val maxRank = when (maxQuality.lowercase()) {
        "standard" -> 0
        "sq" -> 2
        "hires" -> 3
        else -> 1
    }
    val selectedLabel = when (selected) {
        AudioQuality.STANDARD -> "标准"
        AudioQuality.HQ -> "HQ"
        AudioQuality.SQ -> "SQ"
        AudioQuality.HIRES -> "Hi-Res"
    }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .height(if (compact) 28.dp else 32.dp)
                .clip(pillShape)
                .background(pillBackground)
                .border(
                    width = 0.5.dp,
                    color = foreground.copy(alpha = 0.10f),
                    shape = pillShape,
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) { expanded = true }
                .padding(
                    horizontal = if (compact) 9.dp else 10.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedLabel,
                fontSize = if (compact) 10.5.sp else 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
                color = foreground,
            )
            NekoIcon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = foreground.copy(alpha = 0.64f),
                modifier = Modifier.size(if (compact) 14.dp else 16.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AudioQuality.entries.forEachIndexed { index, quality ->
                DropdownMenuItem(
                    text = {
                        Text(when (quality) {
                            AudioQuality.STANDARD -> "标准"
                            AudioQuality.HQ -> "HQ"
                            AudioQuality.SQ -> "SQ"
                            AudioQuality.HIRES -> "Hi-Res"
                        })
                    },
                    enabled = index <= maxRank,
                    onClick = {
                        expanded = false
                        onSelected(quality)
                    }
                )
            }
        }
    }
}

@Composable
fun ShareDialog(
    music: Music,
    onDismiss: () -> Unit,
    onCopyLink: () -> Unit,
    onDownload: () -> Unit,
    onShareToTwitter: () -> Unit,
    onShareToQQ: () -> Unit,
    onSpeedChange: (Float) -> Unit = {},
    currentSpeed: Float = 1.0f,
    onSleepTimerChange: (Int) -> Unit = {},
    currentSleepTimerMinutes: Int = 0,
    playlists: List<PlaylistInfo> = emptyList(),
    selectedPlaylistId: Int? = null,
    onPlaylistSelected: (PlaylistInfo) -> Unit = {},
    playlistFirstMusicCovers: Map<Int, String> = emptyMap(),
    onCreatePlaylist: () -> Unit = {},
    onGenerateVideo: () -> Unit = {},
    canGenerateVideo: Boolean = true,
    videoRenderStatus: String? = null,
    canDownloadVideo: Boolean = false,
    onDownloadVideo: () -> Unit = {},
    onTogetherClick: () -> Unit = {},
    isDesktopLyricEnabled: Boolean = false,
    onDesktopLyricClick: () -> Unit = {},
) {
    var showCustomSleepTimerDialog by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val useLiquidStyle = isLiquidGlassEffectActive(LocalLiquidLayerBackdrop.current)
    val isDarkTheme = isAppDarkTheme()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val dragState = remember(scope, density.density) {
        MoreSheetDragState(scope, density.density) { latestOnDismiss() }
    }
    val sheetScroll = rememberScrollState()
    val nestedScroll = remember(dragState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || dragState.offsetPx <= 0f) return Offset.Zero
                return Offset(0f, dragState.dragBy(available.y))
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                return Offset(0f, dragState.dragBy(available.y))
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragState.offsetPx <= 0f) return Velocity.Zero
                dragState.release(available.y)
                return Velocity(0f, available.y)
            }
        }
    }

    // 预加载额外的字符串资源
    val customLabel = stringResource(id = R.string.custom)
    val closeLabel = stringResource(id = R.string.close)
    val dragHandleLabel = stringResource(R.string.player_more_drag_handle)

    val sectionTitleColor = if (useLiquidStyle) {
        if (isDarkTheme) Color.White.copy(alpha = 0.6f) else scheme.onSurfaceVariant
    } else {
        scheme.onSurfaceVariant
    }
    val cancelTextColor = if (useLiquidStyle) {
        if (isDarkTheme) Color.White.copy(alpha = 0.8f) else scheme.onSurface
    } else {
        scheme.onSurface
    }
    val dividerColor = if (useLiquidStyle) {
        if (isDarkTheme) Color.White.copy(alpha = 0.1f) else scheme.outlineVariant
    } else {
        scheme.outlineVariant
    }
    val createPlaylistColor = if (useLiquidStyle) {
        if (isDarkTheme) Color.White else RoseRed
    } else {
        scheme.primary
    }
    // 暗色下勿用纯白作圆形底，否则与白色线稿图标糊成一片
    val copyLinkColor = if (useLiquidStyle) {
        if (isDarkTheme) RoseRed.copy(alpha = 0.92f) else RoseRed
    } else {
        scheme.primary
    }

    // visible 恒为 true 时 enter 过渡不会跑：首帧 false，下一帧再 true 才能触发滑入+淡入
    var sheetShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        sheetShown = true
    }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (sheetShown) 1f else 0f,
        animationSpec = tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "share_scrim"
    )

    BackHandler(onBack = dragState::dismiss)
    // 不用 Dialog 独立窗口，避免首帧建窗卡顿；与播放页同层合成，液态采样更稳定
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(40f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = scrimAlpha * (1f - dragState.offsetPx / dragState.heightPx).coerceIn(0f, 1f)
                }
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = dragState::dismiss
                )
        )
        AnimatedVisibility(
            visible = sheetShown,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(180)),
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .graphicsLayer { translationY = dragState.offsetPx }
                .nestedScroll(nestedScroll)
                .semantics { dismiss { dragState.dismiss(); true } }
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            val panelCorner = if (useLiquidStyle) 22.dp else 28.dp
            val panelShape = RoundedCornerShape(topStart = panelCorner, topEnd = panelCorner)
            val sharePanel = LiquidGlassDefaults.playerShareBottomSheet
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (maxHeight - WindowInsets.statusBars.asPaddingValues().calculateTopPadding() - 12.dp)
                        .coerceAtLeast(1.dp))
                    .onSizeChanged { dragState.updateHeight(it.height.toFloat()) }
                    .windowInsetsPadding(WindowInsets.navigationBars),
                shape = panelShape,
                backgroundAlpha = sharePanel.tint.background(isDarkTheme),
                borderAlpha = sharePanel.tint.border(isDarkTheme),
                highlightAlpha = sharePanel.tint.highlight(isDarkTheme),
                borderColor = if (isDarkTheme) SakuraPink else scheme.outline,
                liquidBlur = sharePanel.liquid.blur,
                liquidLensHeight = sharePanel.liquid.lensHeight,
                liquidLensAmount = sharePanel.liquid.lensAmount,
                material3Shape = panelShape,
                material3Container = scheme.surfaceContainerHigh,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .semantics { contentDescription = dragHandleLabel }
                            .draggable(
                                state = rememberDraggableState { dragState.dragBy(it) },
                                orientation = Orientation.Vertical,
                                onDragStopped = { dragState.release(it) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(36.dp, 5.dp).clip(CircleShape)
                            .background(scheme.onSurfaceVariant.copy(alpha = .35f)))
                    }
                    Box(Modifier.weight(1f, fill = false).verticalScroll(sheetScroll)) {
                        ShareDialogContent(
                            isDarkTheme = isDarkTheme,
                            sectionTitleColor = sectionTitleColor,
                            cancelTextColor = cancelTextColor,
                            dividerColor = dividerColor,
                            createPlaylistColor = createPlaylistColor,
                            copyLinkColor = copyLinkColor,
                            playlists = playlists,
                            selectedPlaylistId = selectedPlaylistId,
                            playlistFirstMusicCovers = playlistFirstMusicCovers,
                            onPlaylistSelected = onPlaylistSelected,
                            onCreatePlaylist = onCreatePlaylist,
                            currentSpeed = currentSpeed,
                            onSpeedChange = onSpeedChange,
                            currentSleepTimerMinutes = currentSleepTimerMinutes,
                            onSleepTimerChange = onSleepTimerChange,
                            customLabel = customLabel,
                            closeLabel = closeLabel,
                            onDismiss = dragState::dismiss,
                            onShareToTwitter = onShareToTwitter,
                            onShareToQQ = onShareToQQ,
                            onCopyLink = onCopyLink,
                            onDownload = onDownload,
                            onGenerateVideo = onGenerateVideo,
                            canGenerateVideo = canGenerateVideo,
                            videoRenderStatus = videoRenderStatus,
                            canDownloadVideo = canDownloadVideo,
                            onDownloadVideo = onDownloadVideo,
                            onShowCustomSleepTimer = { showCustomSleepTimerDialog = true },
                            onTogetherClick = onTogetherClick,
                            isDesktopLyricEnabled = isDesktopLyricEnabled,
                            onDesktopLyricClick = onDesktopLyricClick,
                        )
                    }
                }
            }
        }

        if (showCustomSleepTimerDialog) {
            CustomSleepTimerDialog(
                initialMinutes = currentSleepTimerMinutes,
                onDismiss = { showCustomSleepTimerDialog = false },
                onConfirm = { hours, minutes ->
                    val totalMinutes = hours * 60 + minutes
                    onSleepTimerChange(totalMinutes)
                    showCustomSleepTimerDialog = false
                }
            )
        }
    }
}

@Composable
fun ShareDialogContent(
    isDarkTheme: Boolean,
    sectionTitleColor: Color,
    cancelTextColor: Color,
    dividerColor: Color,
    createPlaylistColor: Color,
    copyLinkColor: Color,
    playlists: List<PlaylistInfo>,
    selectedPlaylistId: Int?,
    playlistFirstMusicCovers: Map<Int, String>,
    onPlaylistSelected: (PlaylistInfo) -> Unit,
    onCreatePlaylist: () -> Unit,
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    currentSleepTimerMinutes: Int,
    onSleepTimerChange: (Int) -> Unit,
    customLabel: String,
    closeLabel: String,
    onDismiss: () -> Unit,
    onShareToTwitter: () -> Unit,
    onShareToQQ: () -> Unit,
    onCopyLink: () -> Unit,
    onDownload: () -> Unit,
    onGenerateVideo: () -> Unit,
    canGenerateVideo: Boolean,
    videoRenderStatus: String?,
    canDownloadVideo: Boolean,
    onDownloadVideo: () -> Unit,
    onShowCustomSleepTimer: () -> Unit,
    onTogetherClick: () -> Unit,
    isDesktopLyricEnabled: Boolean,
    onDesktopLyricClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val useLiquidStyle = isLiquidGlassEffectActive(LocalLiquidLayerBackdrop.current)
    val shareLabelTint = if (useLiquidStyle) {
        if (isDarkTheme) Color.White.copy(alpha = 0.82f) else scheme.onSurfaceVariant
    } else {
        scheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        ShareSheetLiquidSection(modifier = Modifier.padding(horizontal = 4.dp)) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    ShareGridItem(
                        symbol = NekoSymbol.RadioWaves,
                        label = stringResource(R.string.together_menu),
                        color = RoseRed,
                        labelColor = shareLabelTint,
                        onClick = onTogetherClick
                    )
                }
                item {
                    ShareGridItem(
                        iconRes = R.drawable.ic_lyric_toggle,
                        label = stringResource(id = R.string.desktop_lyric),
                        color = if (isDesktopLyricEnabled) RoseRed else Color(0xFF6B7280),
                        labelColor = shareLabelTint,
                        onClick = onDesktopLyricClick,
                    )
                }
                item {
                    ShareGridItem(
                        iconRes = R.drawable.twitter,
                        label = stringResource(id = R.string.share_to_twitter),
                        color = Color(0xFF1DA1F2),
                        labelColor = shareLabelTint,
                        onClick = onShareToTwitter
                    )
                }
                item {
                    ShareGridItem(
                        iconRes = R.drawable.qq,
                        label = stringResource(id = R.string.share_to_qq),
                        color = Color(0xFF12B7F5),
                        labelColor = shareLabelTint,
                        onClick = onShareToQQ
                    )
                }
                item {
                    ShareGridItem(
                        iconRes = R.drawable.copy_link,
                        label = stringResource(id = R.string.copy_link),
                        color = copyLinkColor,
                        labelColor = shareLabelTint,
                        onClick = onCopyLink
                    )
                }
                item {
                    ShareGridItem(
                        iconRes = R.drawable.download,
                        label = stringResource(id = R.string.download),
                        color = Color(0xFF6B5B95),
                        labelColor = shareLabelTint,
                        onClick = onDownload
                    )
                }
                if (canGenerateVideo) {
                    item {
                        val videoLabel = when {
                            canDownloadVideo -> stringResource(id = R.string.video_render_download)
                            videoRenderStatus != null -> videoRenderStatus
                            else -> stringResource(id = R.string.video_render_button)
                        }
                        ShareGridItem(
                            iconRes = if (canDownloadVideo) R.drawable.download else R.drawable.ic_video_share,
                            label = videoLabel,
                            color = RoseRed,
                            labelColor = shareLabelTint,
                            enabled = videoRenderStatus == null || canDownloadVideo,
                            onClick = when {
                                canDownloadVideo -> onDownloadVideo
                                videoRenderStatus != null -> ({})
                                else -> onGenerateVideo
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (playlists.isNotEmpty()) {
            Text(
                text = stringResource(id = R.string.add_to_playlist),
                fontSize = 14.sp,
                color = sectionTitleColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp)
            )
            ShareSheetLiquidSection(modifier = Modifier.padding(horizontal = 4.dp)) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        count = playlists.size,
                        key = { index -> playlists[index].id }
                    ) { index ->
                        val playlist = playlists[index]
                        PlaylistChip(
                            playlist = playlist,
                            isSelected = selectedPlaylistId == playlist.id,
                            firstMusicCover = playlistFirstMusicCovers[playlist.id],
                            onClick = { onPlaylistSelected(playlist) }
                        )
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(56.dp)
                                .border(
                                    width = 2.dp,
                                    color = createPlaylistColor,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(
                                    color = createPlaylistColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable(onClick = onCreatePlaylist),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                NekoIcon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(id = R.string.create_new),
                                    tint = createPlaylistColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = stringResource(id = R.string.create_new),
                                    fontSize = 10.sp,
                                    color = createPlaylistColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = stringResource(id = R.string.playback_speed),
            fontSize = 14.sp,
            color = sectionTitleColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp)
        )
        ShareSheetLiquidSection(modifier = Modifier.padding(horizontal = 4.dp)) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    count = 6,
                    key = { index -> index }
                ) { index ->
                    val speed = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)[index]
                    SpeedChip(
                        speed = speed,
                        isSelected = speed == currentSpeed,
                        onClick = { onSpeedChange(speed) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(id = R.string.sleep_timer),
            fontSize = 14.sp,
            color = sectionTitleColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp)
        )
        ShareSheetLiquidSection(modifier = Modifier.padding(horizontal = 4.dp)) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    count = 7,
                    key = { index -> index }
                ) { index ->
                    val presetMinutes = listOf(0, 10, 20, 30, 45, 60)
                    if (index == 6) {
                        val isCustomSelected =
                            currentSleepTimerMinutes > 0 && currentSleepTimerMinutes !in presetMinutes
                        SleepTimerChip(
                            minutes = -1,
                            isSelected = isCustomSelected,
                            customMinutes = if (isCustomSelected) currentSleepTimerMinutes else null,
                            onClick = onShowCustomSleepTimer,
                            customLabel = customLabel,
                            closeLabel = closeLabel
                        )
                    } else {
                        val minutes = presetMinutes[index]
                        SleepTimerChip(
                            minutes = minutes,
                            isSelected = minutes == currentSleepTimerMinutes,
                            onClick = { onSleepTimerChange(minutes) },
                            customLabel = customLabel,
                            closeLabel = closeLabel
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ShareSheetLiquidSection(modifier = Modifier.padding(horizontal = 4.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.cancel),
                    fontSize = 17.sp,
                    color = cancelTextColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
fun ShareGridItem(
    icon: String? = null,
    iconRes: Int? = null,
    label: String,
    color: Color,
    labelColor: Color,
    enabled: Boolean = true,
    symbol: NekoSymbol? = null,
    onClick: () -> Unit
) {
    val useLiquidStyle = isLiquidGlassEffectActive(LocalLiquidLayerBackdrop.current)
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (useLiquidStyle && isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(140)
            isPressed = false
        }
    }
    val iconTint = if (color.luminance() > 0.72f) Color(0xFF2A2A2A) else Color.White

    Column(
        modifier = Modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled) {
                isPressed = true
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (useLiquidStyle) {
                        Modifier.shadow(
                            elevation = 4.dp,
                            spotColor = color.copy(alpha = 0.3f),
                            ambientColor = color.copy(alpha = 0.15f)
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (symbol != null) {
                NekoSymbolIcon(
                    symbol, label, Modifier.size(28.dp), iconTint,
                )
            } else if (iconRes != null) {
                NekoIcon(
                    drawableRes = iconRes,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            } else if (icon != null) {
                Text(
                    text = icon,
                    fontSize = 28.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = labelColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SpeedChip(
    speed: Float,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val useLiquidStyle = isLiquidGlassEffectActive(LocalLiquidLayerBackdrop.current)
    val isDarkTheme = isAppDarkTheme()
    val backgroundColor = if (useLiquidStyle) {
        if (isSelected) {
            if (isDarkTheme) SakuraPink.copy(alpha = 0.42f) else RoseRed.copy(alpha = 0.9f)
        } else {
            if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
        }
    } else if (isSelected) {
        scheme.primary
    } else {
        Color.Transparent
    }
    val textColor = if (useLiquidStyle) {
        if (isSelected) Color.White
        else if (isDarkTheme) Color.White.copy(alpha = 0.75f) else scheme.onSurfaceVariant
    } else if (isSelected) {
        scheme.onPrimary
    } else {
        scheme.onSurfaceVariant
    }
    val borderColor = if (useLiquidStyle) {
        if (isSelected) {
            if (isDarkTheme) SakuraPink.copy(alpha = 0.6f) else RoseRed.copy(alpha = 0.5f)
        } else {
            if (isDarkTheme) Color.White.copy(alpha = 0.12f) else scheme.outline.copy(alpha = 0.35f)
        }
    } else if (isSelected) {
        Color.Transparent
    } else {
        scheme.outlineVariant
    }

    Box(
        modifier = Modifier
            .height(36.dp)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${speed}x",
            fontSize = 14.sp,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun SleepTimerChip(
    minutes: Int,
    isSelected: Boolean,
    customMinutes: Int? = null,
    onClick: () -> Unit,
    customLabel: String = "",
    closeLabel: String = ""
) {
    val scheme = MaterialTheme.colorScheme
    val useLiquidStyle = isLiquidGlassEffectActive(LocalLiquidLayerBackdrop.current)
    val isDarkTheme = isAppDarkTheme()
    val backgroundColor = if (useLiquidStyle) {
        if (isSelected) {
            if (isDarkTheme) SakuraPink.copy(alpha = 0.42f) else RoseRed.copy(alpha = 0.9f)
        } else {
            if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
        }
    } else if (isSelected) {
        scheme.primary
    } else {
        Color.Transparent
    }
    val textColor = if (useLiquidStyle) {
        if (isSelected) Color.White
        else if (isDarkTheme) Color.White.copy(alpha = 0.75f) else scheme.onSurfaceVariant
    } else if (isSelected) {
        scheme.onPrimary
    } else {
        scheme.onSurfaceVariant
    }
    val borderColor = if (useLiquidStyle) {
        if (isSelected) {
            if (isDarkTheme) SakuraPink.copy(alpha = 0.6f) else RoseRed.copy(alpha = 0.5f)
        } else {
            if (isDarkTheme) Color.White.copy(alpha = 0.12f) else scheme.outline.copy(alpha = 0.35f)
        }
    } else if (isSelected) {
        Color.Transparent
    } else {
        scheme.outlineVariant
    }
    val label = when {
        minutes == -1 && customMinutes != null -> {
            val hours = customMinutes / 60
            val mins = customMinutes % 60
            if (hours > 0 && mins > 0) {
                "${hours}h${mins}m"
            } else if (hours > 0) {
                "${hours}h"
            } else {
                "${mins}m"
            }
        }
        minutes == -1 -> customLabel
        minutes == 0 -> closeLabel
        else -> "${minutes}分钟"
    }

    Box(
        modifier = Modifier
            .height(36.dp)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun CustomSleepTimerDialog(
    initialMinutes: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var hours by remember { mutableStateOf(initialMinutes / 60) }
    var minutes by remember { mutableStateOf(initialMinutes % 60) }
    val scheme = MaterialTheme.colorScheme

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = scheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.custom_sleep_timer),
                            fontSize = 18.sp,
                            color = scheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 小时选择
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(id = R.string.hours),
                                    fontSize = 14.sp,
                                    color = scheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { if (hours > 0) hours-- },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        NekoIcon(
                                            drawableRes = R.drawable.previous_song,
                                            contentDescription = stringResource(id = R.string.decrease),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Text(
                                        text = "$hours",
                                        fontSize = 32.sp,
                                        color = scheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )

                                    IconButton(
                                        onClick = { if (hours < 23) hours++ },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        NekoIcon(
                                            drawableRes = R.drawable.next_song,
                                            contentDescription = stringResource(id = R.string.increase),
                                            tint = scheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = ":",
                                fontSize = 32.sp,
                                color = scheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )

                            // 分钟选择
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(id = R.string.minutes),
                                    fontSize = 14.sp,
                                    color = scheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { if (minutes > 0) minutes-- },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        NekoIcon(
                                            drawableRes = R.drawable.previous_song,
                                            contentDescription = stringResource(id = R.string.decrease),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Text(
                                        text = String.format("%02d", minutes),
                                        fontSize = 32.sp,
                                        color = scheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )

                                    IconButton(
                                        onClick = { if (minutes < 59) minutes++ },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        NekoIcon(
                                            drawableRes = R.drawable.next_song,
                                            contentDescription = stringResource(id = R.string.increase),
                                            tint = scheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(
                                        scheme.surfaceContainerHighest,
                                        RoundedCornerShape(24.dp)
                                    )
                                    .clickable { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.cancel),
                                    fontSize = 16.sp,
                                    color = scheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(
                                        scheme.primary,
                                        RoundedCornerShape(24.dp)
                                    )
                                    .clickable { onConfirm(hours, minutes) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.confirm),
                                    fontSize = 16.sp,
                                    color = scheme.onPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistChip(
    playlist: PlaylistInfo,
    isSelected: Boolean,
    firstMusicCover: String? = null,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val useLiquidStyle = isLiquidGlassEffectActive(LocalLiquidLayerBackdrop.current)
    val isDarkTheme = isAppDarkTheme()
    val selectedColor = if (useLiquidStyle) {
        if (isDarkTheme) Color.White else RoseRed
    } else {
        scheme.primary
    }
    val containerColor = if (useLiquidStyle) {
        if (isSelected) selectedColor.copy(alpha = 0.1f) else scheme.surfaceVariant
    } else if (isSelected) {
        scheme.primaryContainer
    } else {
        scheme.surfaceContainerHigh
    }
    val contentColor = if (useLiquidStyle) {
        if (isDarkTheme) Color.White.copy(alpha = 0.8f) else scheme.onSurface
    } else if (isSelected) {
        scheme.onPrimaryContainer
    } else {
        scheme.onSurface
    }
    Box(
        modifier = Modifier
            .width(70.dp)
            .height(56.dp)
            .border(
                width = if (!useLiquidStyle) 1.dp else if (isSelected) 2.dp else 0.dp,
                color = when {
                    useLiquidStyle && isSelected -> selectedColor
                    useLiquidStyle -> Color.Transparent
                    isSelected -> Color.Transparent
                    else -> scheme.outlineVariant
                },
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 封面
            val coverUrl = if (!playlist.coverPath.isNullOrEmpty()) {
                UrlConfig.buildFullUrl("${playlist.coverPath}")
            } else {
                firstMusicCover
            }
            AsyncImage(
                model = coverUrl,
                contentDescription = stringResource(id = R.string.cover),
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )

            // 歌单名称
            Text(
                text = playlist.name,
                fontSize = 10.sp,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            
            // 歌曲数量
            Text(
                text = "${playlist.musicCount}首",
                fontSize = 8.sp,
                color = if (useLiquidStyle) scheme.onSurfaceVariant else contentColor.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun DecorativeOrbs() {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "orbs")
    val orb1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(4000, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "orb1",
    )
    val orb2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -15f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(5000, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "orb2",
    )
    val orb3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 25f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(6000, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "orb3",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .offset(x = (-40f).dp, y = ((60f + orb1Offset)).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(SakuraPink.copy(alpha = 0.08f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(150.dp)
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = ((120f + orb2Offset)).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(SkyBlue.copy(alpha = 0.07f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-50f).dp, y = ((-100f + orb3Offset)).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFDDA0DD).copy(alpha = 0.06f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
    }
}

@Composable
private fun DecorativeOrbsLight() {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "orbsLight")
    val orb1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 15f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(5000, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "orb1L",
    )
    val orb2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -12f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(6000, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "orb2L",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = (-30f).dp, y = ((80f + orb1Offset)).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(SakuraPink.copy(alpha = 0.07f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.TopEnd)
                .offset(x = 20.dp, y = ((150f + orb2Offset)).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(SkyBlue.copy(alpha = 0.06f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
    }
}
