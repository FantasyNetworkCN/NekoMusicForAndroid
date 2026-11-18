package com.neko.music.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.music.R
import com.neko.music.ui.screens.LrcLine
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.ui.theme.lyricHighlightColorFromPrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private object AppleLyricMotion {
    const val FocusPosition = 0.25f
    const val CurrentLineScale = 1.05f
    const val BlurIntensity = 0.32f
    const val BlurSkipAlpha = 0.05f
    const val FocusColorDurationMs = 120
    const val BlurSmoothDurationMs = 160
    const val CascadeDelayMs = 14f
    const val CascadeDelayIncreaseMs = 2.5f
    const val CascadeFollowingDelayMs = 22f
    const val CascadeBounceGradient = 0.60f
    const val CascadeBounce = 0.14f
    const val LongToneThresholdMs = 950f
    const val LongToneMaxScale = 1.0625f
    const val LongToneGlowPeak = 0.7f
    const val OrdinaryGlowPeak = 0.55f
    const val GlowTailMs = 550f
}
private val AppleLyricFontFamily = FontFamily(
    Font(R.font.sf_pro_vf, weight = FontWeight.Black),
    Font(R.font.sf_pro_vf, weight = FontWeight.Bold),
)

/**
 * Apple Music 播放页歌词视图。
 *
 * 复刻 Apple Music 的两个核心视觉规则：
 * 1. 当前行固定在歌词视口上方的锚点，而不是屏幕中心；
 * 2. 非当前行统一为 18% 不透明度、0.98 缩放，当前行过渡到 94%、1.0。
 *
 * 用户拖动时暂停自动跟随；停止拖动后恢复锚定。逐字高亮仍沿用项目现有能力。
 */
@Composable
fun AmllLyricsView(
    lyrics: List<LrcLine>,
    currentProgressSeconds: State<Float>,
    isLoading: Boolean,
    isPlaying: Boolean = false,
    playbackSpeed: Float = 1f,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onLineClick: (Float) -> Unit = {},
) {
    val isDarkTheme = isAppDarkTheme()
    val isWideLayout = LocalConfiguration.current.screenWidthDp >= 600
    val context = LocalContext.current
    val appPrefs = remember(context) {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }
    var lyricStyleRev by remember { mutableIntStateOf(0) }
    DisposableEffect(appPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in LyricStylePreferences.keys) {
                lyricStyleRev++
            }
        }
        appPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { appPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val lyricStyle = remember(isWideLayout, lyricStyleRev) {
        LyricStylePreferences.load(appPrefs, isWideLayout)
    }
    val highlightColor = remember(isDarkTheme, lyricStyleRev) {
        lyricHighlightColorFromPrefs(appPrefs, isDarkTheme)
    }
    val lyricFontSize = lyricStyle.fontSizeSp.sp
    val lyricLineHeight = (lyricStyle.fontSizeSp * 1.2f).sp
    val secondaryFontSize = (lyricStyle.fontSizeSp * 0.65f).sp
    val secondaryLineHeight = (secondaryFontSize.value * 1.2f).sp
    val lyricTextColor = if (isDarkTheme) Color.White else Color(0xFF111114)
    val displayLyrics = remember(lyrics) {
        if (lyrics.firstOrNull()?.time?.let { it > 0f } == true) {
            listOf(LrcLine(0f, "")) + lyrics
        } else {
            lyrics
        }
    }
    // 播放器位置约 10Hz 更新。动画补帧会始终追赶旧目标，导致逐字高亮晚约一到两帧；
    // 改为从最新位置按帧时钟向前推算，并在每次位置回调时重新对齐。
    val reportedPlayhead = currentProgressSeconds.value
    var smoothPlayhead by remember { mutableFloatStateOf(reportedPlayhead) }
    LaunchedEffect(reportedPlayhead, isPlaying, playbackSpeed) {
        if (!isPlaying) {
            smoothPlayhead = reportedPlayhead
            return@LaunchedEffect
        }

        val anchorFrameNanos = withFrameNanos { it }
        smoothPlayhead = reportedPlayhead
        while (true) {
            withFrameNanos { frameTimeNanos ->
                val elapsedSeconds = (frameTimeNanos - anchorFrameNanos) / 1_000_000_000f
                smoothPlayhead = reportedPlayhead + elapsedSeconds * playbackSpeed.coerceAtLeast(0f)
            }
        }
    }
    val currentIndex by remember(displayLyrics) {
        derivedStateOf {
            displayLyrics.indexOfLast { it.time <= smoothPlayhead }
        }
    }

    var isUserBrowsing by remember { mutableStateOf(false) }
    var resumeFollowJob by remember { mutableStateOf<Job?>(null) }
    var cascadeRevision by remember { mutableIntStateOf(0) }
    var previousCascadeIndex by remember { mutableIntStateOf(-1) }
    var selectedLineIndices by remember(displayLyrics) { mutableStateOf(emptySet<Int>()) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val selectedLineCount = selectedLineIndices.size

    LaunchedEffect(currentIndex) {
        val previous = previousCascadeIndex
        previousCascadeIndex = currentIndex
        if (
            previous < 0 ||
            currentIndex < 0 ||
            isUserBrowsing ||
            !lyricStyle.smoothScrollEnabled ||
            abs(currentIndex - previous) !in 1..4
        ) {
            return@LaunchedEffect
        }
        cascadeRevision++
    }

    fun clearSelection() {
        selectedLineIndices = emptySet()
    }

    fun selectedLyricsText(): String {
        return selectedLineIndices.sorted()
            .mapNotNull(displayLyrics::getOrNull)
            .flatMap { line ->
                buildList {
                    if (line.text.isNotBlank()) add(line.text)
                    if (lyricStyle.showRomanization && line.romanization.isNotBlank()) {
                        add(line.romanization)
                    }
                    if (lyricStyle.showTranslation && line.translation.isNotBlank()) {
                        add(line.translation)
                    }
                }
            }
            .joinToString("\n")
    }

    BackHandler(enabled = selectedLineIndices.isNotEmpty()) {
        clearSelection()
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.8f) else highlightColor,
                    )
                }
            }

            displayLyrics.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(id = R.string.no_lyrics),
                        fontSize = 16.sp,
                        color = lyricTextColor.copy(alpha = 0.45f),
                    )
                }
            }

            else -> {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    // 与参考歌词面板一致：焦点位于视口顶部 25%。
                    val anchorOffset = maxHeight * AppleLyricMotion.FocusPosition
                    val anchorOffsetPx = with(density) { anchorOffset.toPx() }
                    val bottomPadding =
                        maxHeight * (1f - AppleLyricMotion.FocusPosition)
                    val lyricsReveal = remember(displayLyrics) { Animatable(0f) }
                    LaunchedEffect(displayLyrics) {
                        lyricsReveal.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 1f,
                                stiffness = 320f,
                                visibilityThreshold = 0.001f,
                            ),
                        )
                    }

                    LaunchedEffect(
                        currentIndex,
                        displayLyrics.size,
                        isUserBrowsing,
                        selectedLineIndices,
                    ) {
                        if (
                            currentIndex < 0 ||
                            displayLyrics.isEmpty() ||
                            isUserBrowsing ||
                            selectedLineIndices.isNotEmpty()
                        ) {
                            return@LaunchedEffect
                        }
                        delay(16)
                        anchorLyricLine(
                            listState = listState,
                            index = currentIndex,
                            anchorOffsetPx = anchorOffsetPx,
                            animate = lyricStyle.smoothScrollEnabled,
                        )
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = lyricsReveal.value }
                            .softEdgeFade(topFade = 52.dp, bottomFade = 84.dp)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    resumeFollowJob?.cancel()
                                    isUserBrowsing = true
                                    do {
                                        val event = awaitPointerEvent()
                                    } while (event.changes.any { it.pressed })
                                    resumeFollowJob = scope.launch {
                                        delay(2_200)
                                        isUserBrowsing = false
                                    }
                                }
                            },
                        contentPadding = PaddingValues(
                            start = 32.dp,
                            top = anchorOffset,
                            end = 32.dp,
                            bottom = bottomPadding,
                        ),
                        // Apple: 32dp 行距由 2dp/10dp 行内边距与 20dp 行间空隙组成。
                        verticalArrangement = Arrangement.spacedBy(lyricStyle.lineSpacingDp.dp),
                        horizontalAlignment = if (lyricStyle.centerText) {
                            Alignment.CenterHorizontally
                        } else {
                            Alignment.Start
                        },
                    ) {
                        itemsIndexed(
                            items = displayLyrics,
                            key = { index, line -> "${line.time}:$index" },
                        ) { index, line ->
                            val isCurrentLine = index == currentIndex
                            val isSelected = index in selectedLineIndices
                            val lineDistance = if (currentIndex >= 0) {
                                abs(index - currentIndex)
                            } else {
                                0
                            }
                            val focusProgress by animateFloatAsState(
                                targetValue = if (isSelected) {
                                    1f
                                } else if (isCurrentLine) {
                                    1f
                                } else {
                                    0f
                                },
                                animationSpec = tween(
                                    durationMillis = AppleLyricMotion.FocusColorDurationMs,
                                    easing = AppleLyricsSmoothStepEasing,
                                ),
                                label = "apple_line_focus",
                            )
                            val lineScale by animateFloatAsState(
                                targetValue = if (
                                    lyricStyle.scaleCurrentLine &&
                                    (isSelected || isCurrentLine)
                                ) {
                                    AppleLyricMotion.CurrentLineScale
                                } else {
                                    1f
                                },
                                animationSpec = spring(
                                    dampingRatio = 0.56f,
                                    stiffness = 360f,
                                    visibilityThreshold = 0.001f,
                                ),
                                label = "apple_line_scale",
                            )
                            val appleDistanceOpacity = appleDistanceOpacity(
                                distancePx = lineDistance.toFloat(),
                                lyricStridePx = 1f,
                                dimAmount = 1f,
                                focusProgress = focusProgress,
                            )
                            val appleEmphasis = appleEmphasis(
                                focusProgress = focusProgress,
                                dimAmount = 1f,
                            )
                            val lineAlpha = if (isSelected) {
                                1f
                            } else {
                                (appleDistanceOpacity * appleEmphasis).coerceIn(0f, 1f)
                            }
                            val distanceBlur = appleDistanceBlurRadius(
                                distancePx = lineDistance.toFloat(),
                                lyricStridePx = 1f,
                                intensity = AppleLyricMotion.BlurIntensity,
                                focusProgress = focusProgress,
                            )
                            val focusBlur = appleFocusBlurRadius(
                                intensity = AppleLyricMotion.BlurIntensity,
                                preceding = currentIndex >= 0 && index == currentIndex - 1,
                                following = currentIndex >= 0 && index == currentIndex + 1,
                            ) * (1f - focusProgress.coerceIn(0f, 1f))
                            val targetBlur = if (lineAlpha < AppleLyricMotion.BlurSkipAlpha) {
                                0f
                            } else {
                                max(distanceBlur, focusBlur)
                            }
                            val lineBlur by animateFloatAsState(
                                targetValue = targetBlur,
                                animationSpec = tween(
                                    AppleLyricMotion.BlurSmoothDurationMs,
                                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                                ),
                                label = "apple_line_blur",
                            )
                            val cascadeWave = remember { Animatable(1f) }
                            val maximumCascadeOrder = 6
                            val cascadePosition =
                                lineDistance.coerceIn(0, maximumCascadeOrder) + 1
                            val cascadeNormalized =
                                cascadePosition.toFloat() / (maximumCascadeOrder + 1f)
                            val cascadeBounceValue = AppleLyricMotion.CascadeBounce *
                                (
                                    1f -
                                        (1f - cascadeNormalized) *
                                        AppleLyricMotion.CascadeBounceGradient
                                    )
                            val cascadeAmplitudePx = with(density) {
                                val lineHeightDp = lyricStyle.fontSizeSp * 1.2f
                                (lineHeightDp * (0.10f + cascadeBounceValue * 0.6f)).dp.toPx()
                            }
                            LaunchedEffect(cascadeRevision) {
                                if (cascadeRevision <= 0 || lineDistance > 6) {
                                    return@LaunchedEffect
                                }
                                val order = lineDistance
                                val accumulatedIncrease =
                                    order.toFloat() * (order - 1).coerceAtLeast(0).toFloat() / 2f
                                val delayMs = if (order == 0) {
                                    0f
                                } else {
                                    AppleLyricMotion.CascadeFollowingDelayMs +
                                        order * AppleLyricMotion.CascadeDelayMs +
                                        accumulatedIncrease *
                                        AppleLyricMotion.CascadeDelayIncreaseMs
                                }
                                delay(delayMs.toLong())
                                cascadeWave.snapTo(0f)
                                cascadeWave.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = 0.56f,
                                        stiffness = 320f,
                                        visibilityThreshold = 0.001f,
                                    ),
                                )
                            }

                            val lineEndTime = line.endTime
                                ?: displayLyrics.getOrNull(index + 1)?.time
                                ?: (line.time + 4f)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (lineBlur > 0.04f) {
                                            Modifier.blur(lineBlur.dp)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .graphicsLayer {
                                        alpha = lineAlpha
                                        scaleX = lineScale
                                        scaleY = lineScale
                                        translationY =
                                            -sin(cascadeWave.value * Math.PI.toFloat()) * cascadeAmplitudePx
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                    }
                                    .drawBehind {
                                        if (isSelected) {
                                            drawRoundRect(
                                                color = highlightColor.copy(alpha = 0.12f),
                                                cornerRadius = CornerRadius(14.dp.toPx()),
                                            )
                                        }
                                    }
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            if (selectedLineIndices.isNotEmpty()) {
                                                if (line.text.isNotBlank()) {
                                                    selectedLineIndices =
                                                        if (isSelected) {
                                                            selectedLineIndices - index
                                                        } else {
                                                            selectedLineIndices + index
                                                        }
                                                }
                                            } else {
                                                onLineClick(line.time)
                                            }
                                        },
                                        onLongClick = {
                                            if (line.text.isNotBlank()) {
                                                selectedLineIndices =
                                                    if (isSelected) {
                                                        selectedLineIndices - index
                                                    } else {
                                                        selectedLineIndices + index
                                                    }
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                            }
                                        },
                                    )
                                    .padding(top = 2.dp, bottom = 10.dp),
                                horizontalAlignment = if (lyricStyle.centerText) {
                                    Alignment.CenterHorizontally
                                } else {
                                    Alignment.Start
                                },
                            ) {
                                if (line.text.isBlank()) {
                                    InstrumentalDots(
                                        isCurrentLine = isCurrentLine,
                                        playheadSeconds = { smoothPlayhead },
                                        lineStartSeconds = line.time,
                                        durationSeconds = (lineEndTime - line.time).coerceAtLeast(0.2f),
                                        baseColor = lyricTextColor,
                                        highlightColor = highlightColor,
                                    )
                                } else {
                                    KaraokeText(
                                        line = line,
                                        playheadSeconds = { smoothPlayhead },
                                        lineEndTime = lineEndTime,
                                        isCurrentLine = isCurrentLine,
                                        baseColor = lyricTextColor,
                                        highlightColor = highlightColor,
                                        fontSize = lyricFontSize,
                                        lineHeight = lyricLineHeight,
                                        glowEnabled = lyricStyle.glowEnabled,
                                        karaokeEnabled = lyricStyle.karaokeEnabled,
                                        centerText = lyricStyle.centerText,
                                        shadowColor = highlightColor.copy(
                                            alpha = if (isDarkTheme) 0.16f else 0.10f,
                                        ),
                                    )
                                }

                                if (lyricStyle.showRomanization && line.romanization.isNotEmpty()) {
                                    Text(
                                        text = line.romanization,
                                        fontSize = secondaryFontSize,
                                        lineHeight = secondaryLineHeight,
                                        fontFamily = AppleLyricFontFamily,
                                        fontWeight = FontWeight.Black,
                                        color = lyricTextColor.copy(alpha = 0.76f),
                                        textAlign = if (lyricStyle.centerText) TextAlign.Center else TextAlign.Start,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                    )
                                }

                                if (lyricStyle.showTranslation && line.translation.isNotEmpty()) {
                                    Text(
                                        text = line.translation,
                                        fontSize = secondaryFontSize,
                                        lineHeight = secondaryLineHeight,
                                        fontFamily = AppleLyricFontFamily,
                                        fontWeight = FontWeight.Black,
                                        color = lyricTextColor.copy(alpha = 0.76f),
                                        textAlign = if (lyricStyle.centerText) TextAlign.Center else TextAlign.Start,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp),
                                    )
                                }
                            }
                        }
                    }

                }
            }
        }

        if (selectedLineIndices.isNotEmpty()) {
            val selectedText = selectedLyricsText()
            LyricSelectionToolbar(
                selectedCount = selectedLineCount,
                isDarkTheme = isDarkTheme,
                accentColor = highlightColor,
                onCopy = {
                    if (selectedText.isNotBlank()) {
                        val clipboard = context.getSystemService(
                            Context.CLIPBOARD_SERVICE
                        ) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                context.getString(R.string.player_tab_lyrics),
                                selectedText,
                            )
                        )
                        Toast.makeText(
                            context,
                            R.string.lyric_selection_copied,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    clearSelection()
                },
                onShare = {
                    if (selectedText.isNotBlank()) {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, selectedText)
                        }
                        context.startActivity(
                            Intent.createChooser(
                                sendIntent,
                                context.getString(R.string.lyric_selection_share),
                            )
                        )
                    }
                    clearSelection()
                },
                onCancel = ::clearSelection,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            )
        }
    }
}

@Composable
private fun LyricSelectionToolbar(
    selectedCount: Int,
    isDarkTheme: Boolean,
    accentColor: Color,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val surfaceColor = if (isDarkTheme) Color(0xF21C1C20) else Color(0xF7FFFFFF)
    val borderColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }
    val primaryColor = if (isDarkTheme) Color.White else Color(0xFF17171A)
    val secondaryColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.62f)
    } else {
        Color(0xFF6E6E73)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, clip = false)
            .clip(shape)
            .background(surfaceColor)
            .border(0.5.dp, borderColor, shape)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.lyric_selection_count, selectedCount),
            color = primaryColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        LyricSelectionAction(
            label = stringResource(R.string.lyric_selection_copy),
            color = accentColor,
            onClick = onCopy,
        )
        LyricSelectionAction(
            label = stringResource(R.string.lyric_selection_share),
            color = accentColor,
            onClick = onShare,
        )
        LyricSelectionAction(
            label = stringResource(R.string.cancel),
            color = secondaryColor,
            onClick = onCancel,
        )
    }
}

@Composable
private fun LyricSelectionAction(
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
private fun InstrumentalDots(
    isCurrentLine: Boolean,
    playheadSeconds: () -> Float,
    lineStartSeconds: Float,
    durationSeconds: Float,
    baseColor: Color,
    highlightColor: Color,
) {
    val pulse = rememberInfiniteTransition(label = "amll_instrumental_dots")
    val pulseAlpha = pulse.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "amll_instrumental_dots_alpha",
    )
    Canvas(
        Modifier
            .width(49.dp)
            .height(20.dp)
            .padding(vertical = if (isCurrentLine) 2.dp else 0.dp),
    ) {
        val elapsedSeconds = (playheadSeconds() - lineStartSeconds).coerceAtLeast(0f)
        val animatedPulse = if (isCurrentLine) pulseAlpha.value else 1f
        val activeRadius = 3.5.dp.toPx()
        val idleRadius = 3.dp.toPx()
        val spacing = 7.dp.toPx()
        val centerY = size.height / 2f
        repeat(3) { pointIndex ->
            val phase = (
                elapsedSeconds / durationSeconds.coerceAtLeast(0.2f) * 3f - pointIndex
            ).coerceIn(0f, 1f)
            val pointAlpha = if (isCurrentLine) {
                (0.24f + (1f - phase) * 0.68f) * animatedPulse
            } else {
                0.24f
            }
            drawCircle(
                color = (if (isCurrentLine) highlightColor else baseColor).copy(alpha = pointAlpha),
                radius = if (isCurrentLine) activeRadius else idleRadius,
                center = Offset(
                    x = activeRadius + spacing * pointIndex,
                    y = centerY,
                ),
            )
        }
    }
}

@Composable
private fun KaraokeText(
    line: LrcLine,
    playheadSeconds: () -> Float,
    lineEndTime: Float,
    isCurrentLine: Boolean,
    baseColor: Color,
    highlightColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    glowEnabled: Boolean,
    karaokeEnabled: Boolean,
    centerText: Boolean,
    shadowColor: Color,
) {
    if (line.text.isEmpty()) return

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val textStyle = remember(
        fontSize,
        lineHeight,
        centerText,
    ) {
        TextStyle(
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontFamily = AppleLyricFontFamily,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
            textAlign = if (centerText) TextAlign.Center else TextAlign.Start,
        )
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val textLayout = remember(line.text, widthPx, textStyle) {
            measurer.measure(
                text = AnnotatedString(line.text),
                style = textStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 5,
                constraints = Constraints(maxWidth = widthPx),
            )
        }
        val syllableTimings = remember(line, lineEndTime) {
            buildKaraokeSyllableTimings(line, lineEndTime)
        }
        val syllableGeometries = remember(textLayout, syllableTimings) {
            syllableTimings.mapNotNull { buildKaraokeSyllableGeometry(textLayout, it) }
        }
        val uncoveredGlyphBounds = remember(textLayout, syllableGeometries, line.text) {
            val covered = syllableGeometries
                .flatMap { geometry -> geometry.glyphBounds.map(KaraokeGlyphBound::charIndex) }
                .toSet()
            buildList {
                for (charIndex in line.text.indices) {
                    if (charIndex in covered) continue
                    buildKaraokeGlyphBound(textLayout, charIndex)?.let { add(it.bounds) }
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { textLayout.size.height.toDp() })
                .semantics { text = AnnotatedString(line.text) },
        ) {
            if (!isCurrentLine) {
                drawText(
                    textLayoutResult = textLayout,
                    color = baseColor,
                    topLeft = Offset.Zero,
                )
                return@Canvas
            }
            val now = playheadSeconds()
            if (!karaokeEnabled || syllableGeometries.isEmpty()) {
                drawText(
                    textLayoutResult = textLayout,
                    color = baseColor,
                    topLeft = Offset.Zero,
                )
                drawText(
                    textLayoutResult = textLayout,
                    color = highlightColor,
                    topLeft = Offset.Zero,
                    shadow = if (glowEnabled) {
                        Shadow(
                            color = shadowColor.copy(alpha = 0.32f),
                            blurRadius = min(fontSize.toPx() * 0.16f, 8.dp.toPx()),
                        )
                    } else {
                        null
                    },
                )
                return@Canvas
            }

            val unplayedPath = Path()
            val completedPath = Path()
            val completedLiftPx = (
                textLayout.layoutInput.style.fontSize.toPx() * 0.08f
                ).coerceIn(1.5f, 5.dp.toPx())
            syllableGeometries.forEach { geometry ->
                val stableEnd = geometry.timing.endSeconds + 0.18f
                when {
                    now < geometry.timing.startSeconds -> {
                        geometry.glyphBounds.forEach { unplayedPath.addRect(it.bounds) }
                    }
                    now >= stableEnd -> {
                        geometry.glyphBounds.forEach { completedPath.addRect(it.bounds) }
                    }
                    else -> {
                        drawAnimatedKaraokeSyllable(
                            textLayout = textLayout,
                            geometry = geometry,
                            nowSeconds = now,
                            baseColor = baseColor,
                            highlightColor = highlightColor,
                            shadowColor = shadowColor,
                            glowEnabled = glowEnabled,
                        )
                    }
                }
            }
            uncoveredGlyphBounds.forEach(unplayedPath::addRect)
            if (!unplayedPath.isEmpty) {
                clipPath(unplayedPath) {
                    drawText(
                        textLayoutResult = textLayout,
                        color = baseColor,
                        topLeft = Offset.Zero,
                    )
                }
            }
            if (!completedPath.isEmpty) {
                withTransform({
                    translate(left = 0f, top = -completedLiftPx)
                }) {
                    clipPath(completedPath) {
                        drawText(
                            textLayoutResult = textLayout,
                            color = baseColor,
                            topLeft = Offset.Zero,
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            color = highlightColor,
                            topLeft = Offset.Zero,
                        )
                    }
                }
            }
        }
    }
}

internal data class KaraokeSyllableTiming(
    val startChar: Int,
    val endChar: Int,
    val startSeconds: Float,
    val endSeconds: Float,
)

private data class KaraokeSyllableGeometry(
    val timing: KaraokeSyllableTiming,
    val glyphBounds: List<KaraokeGlyphBound>,
    val bounds: Rect,
)

private data class KaraokeGlyphBound(
    val charIndex: Int,
    val bounds: Rect,
)

/**
 * 把字级时间映射到已经排版后的字符区间。
 *
 * 高亮绘制不再每帧构造一个 `FloatArray`，而是在歌词或行结束时间变化时
 * 只计算一次区间；播放进度只参与 Canvas 绘制。
 */
internal fun buildKaraokeSyllableTimings(
    line: LrcLine,
    lineEndTime: Float,
): List<KaraokeSyllableTiming> {
    if (line.text.isEmpty() || line.syllables.isEmpty()) return emptyList()

    val timing = mutableListOf<KaraokeSyllableTiming>()
    var searchFrom = 0
    line.syllables.forEachIndexed { index, syllable ->
        if (syllable.text.isEmpty()) return@forEachIndexed
        val startChar = line.text.indexOf(syllable.text, startIndex = searchFrom)
        if (startChar < 0) return@forEachIndexed
        val endChar = startChar + syllable.text.length
        val nextStart = line.syllables.getOrNull(index + 1)?.time
        val startSeconds = syllable.time.coerceAtLeast(line.time)
        val endSeconds = max(
            startSeconds + 0.08f,
            syllable.endTime ?: nextStart ?: lineEndTime,
        )
        timing += KaraokeSyllableTiming(
            startChar = startChar,
            endChar = endChar,
            startSeconds = startSeconds,
            endSeconds = endSeconds,
        )
        searchFrom = endChar
    }
    if (timing.isEmpty()) {
        return listOf(
            KaraokeSyllableTiming(
                startChar = 0,
                endChar = line.text.length,
                startSeconds = line.time,
                endSeconds = max(line.time + 0.2f, lineEndTime),
            )
        )
    }
    return timing
}

internal fun karaokeRevealProgress(
    timing: KaraokeSyllableTiming,
    playheadSeconds: Float,
): Float {
    if (playheadSeconds <= timing.startSeconds) return 0f
    if (playheadSeconds >= timing.endSeconds) return 1f
    val progress = (
        (playheadSeconds - timing.startSeconds) /
            (timing.endSeconds - timing.startSeconds).coerceAtLeast(0.001f)
        ).coerceIn(0f, 1f)
    return lyricSmootherStep(progress)
}

private fun buildKaraokeSyllableGeometry(
    textLayout: TextLayoutResult,
    timing: KaraokeSyllableTiming,
): KaraokeSyllableGeometry? {
    val glyphBounds = buildList {
        for (charIndex in timing.startChar until timing.endChar) {
            buildKaraokeGlyphBound(textLayout, charIndex)?.let(::add)
        }
    }
    if (glyphBounds.isEmpty()) return null
    val bounds = Rect(
        left = glyphBounds.minOf { it.bounds.left },
        top = glyphBounds.minOf { it.bounds.top },
        right = glyphBounds.maxOf { it.bounds.right },
        bottom = glyphBounds.maxOf { it.bounds.bottom },
    )
    return KaraokeSyllableGeometry(timing, glyphBounds, bounds)
}

private fun buildKaraokeGlyphBound(
    textLayout: TextLayoutResult,
    charIndex: Int,
): KaraokeGlyphBound? {
    val box = runCatching { textLayout.getBoundingBox(charIndex) }.getOrNull()
        ?.takeIf { it.width.isFinite() && it.height.isFinite() && it.width > 0f }
        ?: return null
    val textLine = textLayout.getLineForOffset(charIndex)
    val top = textLayout.getLineTop(textLine)
    val bottom = textLayout.getLineBottom(textLine).coerceAtLeast(top + 1f)
    return KaraokeGlyphBound(
        charIndex = charIndex,
        bounds = Rect(box.left, top, box.right, bottom),
    )
}

private fun DrawScope.drawAnimatedKaraokeSyllable(
    textLayout: TextLayoutResult,
    geometry: KaraokeSyllableGeometry,
    nowSeconds: Float,
    baseColor: Color,
    highlightColor: Color,
    shadowColor: Color,
    glowEnabled: Boolean,
) {
    val timing = geometry.timing
    val duration = (timing.endSeconds - timing.startSeconds).coerceAtLeast(0.001f)
    val rawProgress = (
        (nowSeconds - timing.startSeconds) / duration
        ).coerceIn(0f, 1f)
    val isLongTone = duration * 1_000f >= AppleLyricMotion.LongToneThresholdMs
    val reveal = appleHighlightRevealProgress(
        playbackTimeMs = nowSeconds * 1_000f,
        startMs = timing.startSeconds * 1_000f,
        endMs = timing.endSeconds * 1_000f,
        rawProgress = rawProgress,
        isLongTone = isLongTone,
    )
    if (reveal <= 0f) return

    val elapsed = (nowSeconds - timing.startSeconds).coerceAtLeast(0f)
    val fontSizePx = textLayout.layoutInput.style.fontSize.toPx()
    val lift = lyricSmootherStep((elapsed / 0.32f).coerceIn(0f, 1f))
    val maxLiftPx = (fontSizePx * 0.10f).coerceIn(1.5f, 6.dp.toPx())
    val emphasisProgress = lyricSmootherStep(
        (
            (duration * 1_000f - AppleLyricMotion.LongToneThresholdMs) /
                (2_800f - AppleLyricMotion.LongToneThresholdMs)
            ).coerceIn(0f, 1f),
    )
    val expansionAmount = if (isLongTone) 0.7f + 0.3f * emphasisProgress else 0f
    val glowAmount = if (isLongTone) 0.32f + 0.38f * emphasisProgress else 0f
    val longToneEnvelope = if (isLongTone) {
        appleLongToneEnvelope(
            playbackTimeMs = nowSeconds * 1_000f,
            groupStartMs = timing.startSeconds * 1_000f,
            durationMs = duration * 1_000f,
            characterIndex = timing.startChar,
            characterCount = (timing.endChar - timing.startChar).coerceAtLeast(1),
        )
    } else {
        0f
    }
    val longToneScale = 1f +
        (AppleLyricMotion.LongToneMaxScale - 1f) * longToneEnvelope * expansionAmount
    val bounceScale = if (isLongTone) {
        1f
    } else if (rawProgress > 0f && rawProgress < 0.5f) {
        1f + 0.2f * (rawProgress / 0.5f)
    } else if (rawProgress >= 0.5f && rawProgress < 1f) {
        1.2f - 0.2f * ((rawProgress - 0.5f) / 0.5f)
    } else {
        1f
    }
    val scale = longToneScale * bounceScale
    val ageAfterEnd = (nowSeconds - timing.endSeconds).coerceAtLeast(0f)
    val glowTail = (1f - ageAfterEnd / 0.55f).coerceIn(0f, 1f)
    val glowStrength = if (glowEnabled) {
        if (isLongTone) {
            longToneEnvelope * glowAmount / AppleLyricMotion.LongToneGlowPeak * glowTail
        } else {
            appleOrdinaryGlowStrength(
                playbackTimeMs = nowSeconds * 1_000f,
                endMs = timing.endSeconds * 1_000f,
                rawProgress = rawProgress,
            ) / AppleLyricMotion.OrdinaryGlowPeak * glowTail
        }
    } else {
        0f
    }
    val shakeAmplitude = if (isLongTone) {
        min(0.58.dp.toPx(), fontSizePx * 0.013f) *
            longToneEnvelope * expansionAmount
    } else {
        0f
    }
    val shakePhaseMs = nowSeconds * 1_000f - timing.startSeconds * 1_000f
    val shakeX = sin(shakePhaseMs * 0.016f + timing.startChar * 1.73f) * shakeAmplitude
    val shakeY = sin(shakePhaseMs * 0.021f + timing.startChar * 0.91f) * shakeAmplitude * 0.42f
    val glowRadius = min(fontSizePx * 0.12f, 8.dp.toPx())
    val shadow = glowStrength.takeIf { it > 0.01f }?.let {
        Shadow(
            color = shadowColor.copy(alpha = it.coerceIn(0f, 1f)),
            blurRadius = glowRadius,
        )
    }
    val revealPath = Path()
    val glowPath = Path()
    val revealBoundary = timing.startChar +
        (timing.endChar - timing.startChar) * reveal
    geometry.glyphBounds.forEach { glyph ->
        val box = glyph.bounds
        val charReveal = (revealBoundary - glyph.charIndex).coerceIn(0f, 1f)
        if (charReveal > 0f) {
            revealPath.addRect(
                Rect(
                    left = box.left,
                    top = box.top,
                    right = box.left + box.width * charReveal,
                    bottom = box.bottom,
                )
            )
        }
        if (shadow != null && charReveal > 0f) {
            glowPath.addRect(
                Rect(
                    left = box.left - glowRadius,
                    top = box.top - glowRadius,
                    right = box.right + glowRadius,
                    bottom = box.bottom + glowRadius,
                )
            )
        }
    }
    if (revealPath.isEmpty) return

    withTransform({
        translate(left = shakeX, top = -maxLiftPx * lift + shakeY)
        scale(
            scaleX = scale,
            scaleY = scale,
            pivot = geometry.bounds.center,
        )
    }) {
        if (shadow != null && !glowPath.isEmpty) {
            clipPath(glowPath) {
                drawText(
                    textLayoutResult = textLayout,
                    color = highlightColor,
                    topLeft = Offset.Zero,
                    shadow = shadow,
                )
            }
        }
        clipPath(geometry.bounds.asPath()) {
            drawText(
                textLayoutResult = textLayout,
                color = baseColor,
                topLeft = Offset.Zero,
            )
        }
        clipPath(revealPath) {
            drawText(
                textLayoutResult = textLayout,
                color = highlightColor,
                topLeft = Offset.Zero,
            )
        }
    }
}

private fun Rect.asPath(): Path = Path().apply { addRect(this@asPath) }

private fun appleDistanceOpacity(
    distancePx: Float,
    lyricStridePx: Float,
    dimAmount: Float,
    focusProgress: Float,
): Float {
    val lineDistance = distancePx / lyricStridePx.coerceAtLeast(0.001f)
    val base = when {
        lineDistance <= 1f -> 1f - lineDistance * 0.30f
        lineDistance <= 2f -> 0.70f - (lineDistance - 1f) * 0.16f
        else -> max(0.34f, 0.54f - (lineDistance - 2f) * 0.05f)
    }
    val distanceOpacity = 1f - (1f - base) * dimAmount
    return distanceOpacity + (1f - distanceOpacity) * focusProgress.coerceIn(0f, 1f)
}

private fun appleEmphasis(focusProgress: Float, dimAmount: Float): Float {
    val unfocused = 1f - (1f - 0.72f) * dimAmount
    return unfocused + (1f - unfocused) * focusProgress.coerceIn(0f, 1f)
}

private fun appleDistanceBlurRadius(
    distancePx: Float,
    lyricStridePx: Float,
    intensity: Float,
    focusProgress: Float,
): Float {
    val lineDistance = distancePx / lyricStridePx.coerceAtLeast(0.001f)
    val blurProgress = max(lineDistance - 1.35f, 0f)
    val baseRadius = min(blurProgress * 1.65f, 5f)
    return baseRadius * intensity * (1f - focusProgress.coerceIn(0f, 1f))
}

private fun appleFocusBlurRadius(
    intensity: Float,
    preceding: Boolean,
    following: Boolean,
): Float = (
    (if (preceding) 1.1f else 0f) +
        (if (following) 0.4f else 0f)
    ) * intensity

private fun appleHighlightRevealProgress(
    playbackTimeMs: Float,
    startMs: Float,
    endMs: Float,
    rawProgress: Float,
    isLongTone: Boolean,
): Float {
    val regular = lyricSmootherStep(rawProgress)
    val duration = endMs - startMs
    if (!isLongTone || duration <= 460f) return regular
    val elapsed = max(playbackTimeMs - startMs, 0f)
    val attack = lyricSmootherStep(elapsed / 300f)
    val releaseStart = endMs - 160f
    val release = lyricSmootherStep((playbackTimeMs - releaseStart) / 160f)
    return (0.82f * attack + 0.08f * rawProgress + 0.10f * release).coerceIn(0f, 1f)
}

private fun appleLongToneEnvelope(
    playbackTimeMs: Float,
    groupStartMs: Float,
    durationMs: Float,
    characterIndex: Int,
    characterCount: Int,
): Float {
    val stagger = if (characterCount > 1) {
        durationMs * 0.55f * characterIndex.toFloat() /
            (characterCount - 1).toFloat()
    } else {
        0f
    }
    val animationDuration = max(durationMs, 1_000f)
    val progress = (
        (playbackTimeMs - groupStartMs - stagger) / animationDuration
        ).coerceIn(0f, 1f)
    return if (progress <= 0.5f) {
        lyricSmootherStep(progress / 0.5f)
    } else {
        1f - lyricSmootherStep((progress - 0.5f) / 0.5f)
    }
}

private fun appleOrdinaryGlowStrength(
    playbackTimeMs: Float,
    endMs: Float,
    rawProgress: Float,
): Float {
    if (playbackTimeMs <= endMs) {
        val attack = lyricSmootherStep(rawProgress / 0.24f)
        val breath = 0.82f + 0.18f * sin(Math.PI.toFloat() * rawProgress)
        return attack * breath * AppleLyricMotion.OrdinaryGlowPeak
    }
    val tail = ((playbackTimeMs - endMs) / AppleLyricMotion.GlowTailMs).coerceIn(0f, 1f)
    return (1f - lyricSmootherStep(tail)) * 0.82f * AppleLyricMotion.OrdinaryGlowPeak
}

private object AppleLyricsSmoothStepEasing : Easing {
    override fun transform(fraction: Float): Float = lyricSmootherStep(fraction)
}

private fun lyricSmootherStep(value: Float): Float {
    val progress = value.coerceIn(0f, 1f)
    return progress * progress * progress * (progress * (progress * 6f - 15f) + 10f)
}

private suspend fun anchorLyricLine(
    listState: LazyListState,
    index: Int,
    anchorOffsetPx: Float,
    animate: Boolean,
) {
    var layoutInfo = listState.layoutInfo
    var item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

    if (item == null) {
        // 大跨度 seek 先定位到目标行，只对最后的锚点修正做动画，避免跨数百行飞滚。
        listState.scrollToItem(index)
        withFrameNanos { }
        layoutInfo = listState.layoutInfo
        item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    }

    val targetTop = layoutInfo.viewportStartOffset + anchorOffsetPx
    val delta = item.offset - targetTop
    if (abs(delta) > 0.5f) {
        if (animate) {
            listState.animateScrollBy(
                value = delta,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 280f,
                    visibilityThreshold = 0.5f,
                ),
            )
        } else {
            listState.scrollBy(delta)
        }
    }
}
