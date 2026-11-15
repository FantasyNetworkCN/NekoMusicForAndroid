package com.neko.music.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onLineClick: (Float) -> Unit = {},
) {
    val isDarkTheme = isAppDarkTheme()
    val isWideLayout = LocalConfiguration.current.screenWidthDp >= 600
    val lyricFontSize = if (isWideLayout) 24.sp else 28.sp
    val lyricLineHeight = if (isWideLayout) 30.sp else 36.sp
    val secondaryFontSize = 17.sp
    val secondaryLineHeight = 24.sp
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
    val lyricTextColor = if (isDarkTheme) Color.White else Color(0xFF111114)
    val displayLyrics = remember(lyrics) {
        if (lyrics.firstOrNull()?.time?.let { it > 0f } == true) {
            listOf(LrcLine(0f, "")) + lyrics
        } else {
            lyrics
        }
    }
    // 播放器位置约 10Hz 更新；用短线性动画补帧，避免真实逐字时间轴按 100ms 跳动。
    val smoothPlayhead by animateFloatAsState(
        targetValue = currentProgressSeconds.value,
        animationSpec = tween(durationMillis = 130, easing = LinearEasing),
        label = "amll_playhead",
    )
    val currentIndex by remember(displayLyrics, currentProgressSeconds) {
        androidx.compose.runtime.derivedStateOf {
            displayLyrics.indexOfLast { it.time <= smoothPlayhead }
        }
    }

    var isUserBrowsing by remember { mutableStateOf(false) }
    var resumeFollowJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

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
                    // Apple 将当前行放在歌词列表顶部约 8% 的位置，而不是垂直居中。
                    val anchorOffset = maxOf(maxHeight * 0.08f, 56.dp)
                    val anchorOffsetPx = with(density) { anchorOffset.toPx() }
                    val bottomPadding = maxHeight * 0.2775f + 60.dp

                    LaunchedEffect(currentIndex, displayLyrics.size, isUserBrowsing) {
                        if (currentIndex < 0 || displayLyrics.isEmpty() || isUserBrowsing) {
                            return@LaunchedEffect
                        }
                        delay(32)
                        anchorLyricLine(
                            listState = listState,
                            index = currentIndex,
                            anchorOffsetPx = anchorOffsetPx,
                        )
                    }

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
                            top = anchorOffset,
                            end = 32.dp,
                            bottom = bottomPadding,
                        ),
                        // Apple: 32dp 行距由 2dp/10dp 行内边距与 20dp 行间空隙组成。
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        itemsIndexed(
                            items = displayLyrics,
                            key = { index, line -> "${line.time}:$index" },
                        ) { index, line ->
                            val isCurrentLine = index == currentIndex
                            val lineAlpha by animateFloatAsState(
                                targetValue = if (isCurrentLine) 0.94f else 0.18f,
                                animationSpec = tween(
                                    durationMillis = if (isCurrentLine) 250 else 350,
                                    delayMillis = 250,
                                    easing = AppleLyricsEasing,
                                ),
                                label = "apple_line_alpha",
                            )
                            val lineScale by animateFloatAsState(
                                targetValue = if (isCurrentLine) 1.0f else 0.98f,
                                animationSpec = tween(
                                    durationMillis = if (isCurrentLine) 250 else 350,
                                    delayMillis = 250,
                                    easing = AppleLyricsEasing,
                                ),
                                label = "apple_line_scale",
                            )

                            val lineEndTime = line.endTime
                                ?: displayLyrics.getOrNull(index + 1)?.time
                                ?: (line.time + 4f)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = lineAlpha
                                        scaleX = lineScale
                                        scaleY = lineScale
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                    }
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onLineClick(line.time) },
                                    )
                                    .padding(top = 2.dp, bottom = 10.dp),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                if (line.text.isBlank()) {
                                    InstrumentalDots(
                                        isCurrentLine = isCurrentLine,
                                        elapsedSeconds = (smoothPlayhead - line.time).coerceAtLeast(0f),
                                        durationSeconds = (lineEndTime - line.time).coerceAtLeast(0.2f),
                                        baseColor = lyricTextColor,
                                        highlightColor = highlightColor,
                                    )
                                } else {
                                    Text(
                                        text = buildKaraokeText(
                                            line = line,
                                            playheadSeconds = smoothPlayhead,
                                            lineEndTime = lineEndTime,
                                            isCurrentLine = isCurrentLine,
                                            baseColor = lyricTextColor,
                                            highlightColor = highlightColor,
                                        ),
                                        fontSize = lyricFontSize,
                                        lineHeight = lyricLineHeight,
                                        fontWeight = FontWeight.Bold,
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

                                if (line.romanization.isNotEmpty()) {
                                    Text(
                                        text = line.romanization,
                                        fontSize = secondaryFontSize,
                                        lineHeight = secondaryLineHeight,
                                        fontWeight = FontWeight.Bold,
                                        color = lyricTextColor.copy(alpha = 0.76f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                    )
                                }

                                if (line.translation.isNotEmpty()) {
                                    Text(
                                        text = line.translation,
                                        fontSize = secondaryFontSize,
                                        lineHeight = secondaryLineHeight,
                                        fontWeight = FontWeight.Bold,
                                        color = lyricTextColor.copy(alpha = 0.76f),
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
    playheadSeconds: Float,
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

        if (!isCurrentLine) {
            addStyle(
                style = SpanStyle(color = baseColor),
                start = 0,
                end = line.text.length,
            )
            return@buildAnnotatedString
        }

        if (line.syllables.isEmpty()) {
            // 行级 LRC 不使用模拟逐字扫光；逐字时间戳就绪后再走下方分支。
            addStyle(
                style = SpanStyle(color = highlightColor),
                start = 0,
                end = line.text.length,
            )
        } else {
            var offset = 0
            line.syllables.forEachIndexed { syllableIndex, syllable ->
                val start = offset.coerceAtMost(line.text.length)
                val end = (offset + syllable.text.length).coerceAtMost(line.text.length)
                val syllableEnd = line.syllables.getOrNull(syllableIndex + 1)?.time ?: lineEndTime
                val syllableProgress = (
                    (playheadSeconds - syllable.time) /
                        (syllableEnd - syllable.time).coerceAtLeast(0.12f)
                    ).coerceIn(0f, 1f)
                applySweep(start, end, syllableProgress)
                offset += syllable.text.length
            }
            if (offset < line.text.length) {
                val fallbackProgress = ((playheadSeconds - line.time) /
                    (lineEndTime - line.time).coerceAtLeast(0.2f)).coerceIn(0f, 1f)
                applySweep(offset, line.text.length, fallbackProgress)
            }
        }
    }
}

private suspend fun anchorLyricLine(
    listState: LazyListState,
    index: Int,
    anchorOffsetPx: Float,
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
        listState.animateScrollBy(
            value = delta,
            animationSpec = tween(
                durationMillis = 320,
                easing = AppleLyricsEasing,
            ),
        )
    }
}

private val AppleLyricsEasing = CubicBezierEasing(0.39f, 0.575f, 0.565f, 1f)
