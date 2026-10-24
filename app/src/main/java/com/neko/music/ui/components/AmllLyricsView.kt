package com.neko.music.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.music.R
import com.neko.music.config.AppConfig
import com.neko.music.ui.screens.LrcLine
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.ui.theme.lyricHighlightColorFromPrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Apple Music / AMLL 风格的歌词视图。
 *
 * 非当前行由屏幕中心距离连续驱动透明度和模糊，当前行随播放进度逐字扫光；
 * 行切换使用弹簧滚动，用户主动拖动时暂停跟随，松手后自动回到当前歌词。
 */
@Composable
fun AmllLyricsView(
    lyrics: List<LrcLine>,
    currentProgressSeconds: State<Float>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onLineClick: (Float) -> Unit = {},
) {
    val isDarkTheme = isAppDarkTheme()
    val context = LocalContext.current
    val appPrefs = remember(context) {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }
    var lyricHighlightRev by remember { mutableIntStateOf(0) }
    DisposableEffect(appPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppConfig.PrefConfig.KEY_LYRIC_HIGHLIGHT_COLOR) {
                lyricHighlightRev++
            }
        }
        appPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { appPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val highlightColor = remember(isDarkTheme, lyricHighlightRev) {
        lyricHighlightColorFromPrefs(appPrefs, isDarkTheme)
    }
    val displayLyrics = remember(lyrics) {
        if (lyrics.firstOrNull()?.time?.let { it > 0f } == true) {
            listOf(LrcLine(0f, "")) + lyrics
        } else {
            lyrics
        }
    }
    val currentIndex by remember(displayLyrics, currentProgressSeconds) {
        androidx.compose.runtime.derivedStateOf {
            displayLyrics.indexOfLast { it.time <= currentProgressSeconds.value }
        }
    }

    var isUserBrowsing by remember { mutableStateOf(false) }
    var resumeFollowJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentIndex, displayLyrics.size, isUserBrowsing) {
        if (currentIndex < 0 || displayLyrics.isEmpty() || isUserBrowsing) {
            return@LaunchedEffect
        }
        delay(32)
        animateLyricLineToCenter(listState, currentIndex)
    }

    val baseTextColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.94f)
    } else {
        Color(0xFF111114).copy(alpha = 0.92f)
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
                        color = baseTextColor.copy(alpha = 0.45f),
                    )
                }
            }

            else -> {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val halfViewport = maxHeight / 2
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
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
                            top = halfViewport,
                            end = 32.dp,
                            bottom = halfViewport,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        itemsIndexed(
                            items = displayLyrics,
                            key = { index, line -> "${line.time}:$index" },
                        ) { index, line ->
                            val layoutInfo = listState.layoutInfo
                            val viewportStart = layoutInfo.viewportStartOffset.toFloat()
                            val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
                            val viewportCenter = (viewportStart + viewportEnd) / 2f
                            val viewportHalf = ((viewportEnd - viewportStart) / 2f).coerceAtLeast(1f)
                            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                            val distanceRatio = itemInfo?.let {
                                val itemCenter = it.offset + it.size / 2f
                                (abs(itemCenter - viewportCenter) / viewportHalf).coerceIn(0f, 1f)
                            } ?: 1f
                            val isCurrentLine = index == currentIndex
                            val lineAlpha by animateFloatAsState(
                                targetValue = 1f - 0.8f * distanceRatio.pow(1.15f),
                                animationSpec = tween(durationMillis = 180, easing = LinearEasing),
                                label = "amll_line_alpha",
                            )
                            val lineScale by animateFloatAsState(
                                targetValue = 1.07f - 0.16f * distanceRatio,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                                label = "amll_line_scale",
                            )
                            val lineBlur = (distanceRatio.pow(1.55f) * 7.5f).coerceIn(0f, 7.5f)

                            val lineEndTime = displayLyrics.getOrNull(index + 1)?.time
                                ?: (line.time + 4f)
                            val rawLineProgress = if (isCurrentLine) {
                                ((currentProgressSeconds.value - line.time) /
                                    (lineEndTime - line.time).coerceAtLeast(0.2f))
                                    .coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            val lineProgress by animateFloatAsState(
                                targetValue = rawLineProgress,
                                animationSpec = tween(durationMillis = 110, easing = LinearEasing),
                                label = "amll_line_progress_$index",
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = lineAlpha
                                        scaleX = lineScale
                                        scaleY = lineScale
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                    }
                                    .blur(lineBlur.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onLineClick(line.time) },
                                    )
                                    .padding(vertical = if (isCurrentLine) 5.dp else 1.dp),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                if (line.text.isBlank()) {
                                    InstrumentalDots(
                                        isCurrentLine = isCurrentLine,
                                        elapsedSeconds = (currentProgressSeconds.value - line.time).coerceAtLeast(0f),
                                        durationSeconds = (lineEndTime - line.time).coerceAtLeast(0.2f),
                                        baseColor = baseTextColor,
                                        highlightColor = highlightColor,
                                    )
                                } else {
                                    Text(
                                        text = buildKaraokeText(
                                            line = line,
                                            lineProgress = lineProgress,
                                            lineEndTime = lineEndTime,
                                            isCurrentLine = isCurrentLine,
                                            baseColor = baseTextColor,
                                            highlightColor = highlightColor,
                                        ),
                                        fontSize = 24.sp,
                                        lineHeight = 32.sp,
                                        fontWeight = if (isCurrentLine) FontWeight.SemiBold else FontWeight.Medium,
                                        letterSpacing = (-0.35).sp,
                                        maxLines = 5,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = if (isCurrentLine) {
                                            TextStyle(
                                                shadow = Shadow(
                                                    color = highlightColor.copy(
                                                        alpha = if (isDarkTheme) 0.16f else 0.10f,
                                                    ),
                                                    blurRadius = 12f,
                                                ),
                                            )
                                        } else {
                                            TextStyle.Default
                                        },
                                    )
                                }

                                if (line.translation.isNotEmpty()) {
                                    Text(
                                        text = line.translation,
                                        fontSize = 15.sp,
                                        lineHeight = 21.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = baseTextColor.copy(
                                            alpha = if (isCurrentLine) 0.70f else 0.52f,
                                        ),
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
    }
}

@Composable
private fun InstrumentalDots(
    isCurrentLine: Boolean,
    elapsedSeconds: Float,
    durationSeconds: Float,
    baseColor: Color,
    highlightColor: Color,
) {
    val pulse = rememberInfiniteTransition(label = "amll_instrumental_dots")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "amll_instrumental_dots_alpha",
    )
    Row(
        modifier = Modifier.padding(vertical = if (isCurrentLine) 7.dp else 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { pointIndex ->
            val phase = (
                elapsedSeconds / durationSeconds.coerceAtLeast(0.2f) * 3f - pointIndex
                ).coerceIn(0f, 1f)
            val pointAlpha = if (isCurrentLine) {
                (0.28f + (1f - phase) * 0.62f) * pulseAlpha
            } else {
                0.24f
            }
            Box(
                modifier = Modifier
                    .size(if (isCurrentLine) 7.dp else 6.dp)
                    .clip(CircleShape)
                    .background((if (isCurrentLine) highlightColor else baseColor).copy(alpha = pointAlpha)),
            )
        }
    }
}

private fun buildKaraokeText(
    line: LrcLine,
    lineProgress: Float,
    lineEndTime: Float,
    isCurrentLine: Boolean,
    baseColor: Color,
    highlightColor: Color,
): AnnotatedString {
    if (line.text.isEmpty()) return AnnotatedString("")

    return buildAnnotatedString {
        append(line.text)

        fun applySweep(start: Int, end: Int, fraction: Float) {
            if (start >= end) return
            val boundary = start + (end - start) * fraction.coerceIn(0f, 1f)
            for (charIndex in start until end) {
                val fill = (boundary - charIndex).coerceIn(0f, 1f)
                addStyle(
                    style = SpanStyle(color = lerp(baseColor, highlightColor, fill)),
                    start = charIndex,
                    end = charIndex + 1,
                )
            }
        }

        if (line.syllables.isEmpty()) {
            // 行级 LRC 不使用模拟逐字扫光；逐字时间戳就绪后再走下方分支。
            addStyle(
                style = SpanStyle(color = if (isCurrentLine) highlightColor else baseColor),
                start = 0,
                end = line.text.length,
            )
        } else {
            var offset = 0
            line.syllables.forEachIndexed { syllableIndex, syllable ->
                val start = offset.coerceAtMost(line.text.length)
                val end = (offset + syllable.text.length).coerceAtMost(line.text.length)
                val syllableEnd = line.syllables.getOrNull(syllableIndex + 1)?.time ?: lineEndTime
                val lineDuration = (lineEndTime - line.time).coerceAtLeast(0.2f)
                val elapsed = line.time + lineDuration * lineProgress
                val syllableProgress = (
                    (elapsed - syllable.time) /
                        (syllableEnd - syllable.time).coerceAtLeast(0.12f)
                    ).coerceIn(0f, 1f)
                applySweep(start, end, syllableProgress)
                offset += syllable.text.length
            }
            if (offset < line.text.length) {
                applySweep(offset, line.text.length, lineProgress)
            }
        }
    }
}

private suspend fun animateLyricLineToCenter(
    listState: LazyListState,
    index: Int,
) {
    var layoutInfo = listState.layoutInfo
    val viewportCenter = (
        layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset
        ) / 2f
    val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

    if (visibleItem == null) {
        val viewportHeight = (
            layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            ).coerceAtLeast(1)
        val averageItemHeight = layoutInfo.visibleItemsInfo
            .map { it.size }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()
            ?.takeIf { it.isFinite() && it > 0f }
            ?: (viewportHeight / 5f)
        val centeredOffset = -(viewportCenter - averageItemHeight / 2f).roundToInt()
        listState.scrollToItem(index, centeredOffset)
        delay(16)
        layoutInfo = listState.layoutInfo
    }

    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        ?: return
    val itemCenter = item.offset + item.size / 2f
    val delta = itemCenter - viewportCenter
    if (abs(delta) > 0.5f) {
        listState.animateScrollBy(
            value = delta,
            animationSpec = spring(
                dampingRatio = 0.86f,
                stiffness = 330f,
                visibilityThreshold = 0.5f,
            ),
        )
    }
}
