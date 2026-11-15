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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
    val lyricLineHeight = (lyricStyle.fontSizeSp * 1.28f).sp
    val secondaryFontSize = (lyricStyle.fontSizeSp * 0.61f).coerceIn(13f, 20f).sp
    val secondaryLineHeight = (secondaryFontSize.value * 1.4f).sp
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
                            animate = lyricStyle.smoothScrollEnabled,
                        )
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .lyricEdgeFade(topFade = 52.dp, bottomFade = 84.dp)
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
                            val lineAlpha by animateFloatAsState(
                                targetValue = if (isCurrentLine) {
                                    lyricStyle.activeAlpha
                                } else {
                                    lyricStyle.inactiveAlpha
                                },
                                animationSpec = tween(
                                    durationMillis = if (isCurrentLine) 250 else 350,
                                    delayMillis = 250,
                                    easing = AppleLyricsEasing,
                                ),
                                label = "apple_line_alpha",
                            )
                            val lineScale by animateFloatAsState(
                                targetValue = if (isCurrentLine || !lyricStyle.scaleCurrentLine) {
                                    1.0f
                                } else {
                                    0.98f
                                },
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
                                horizontalAlignment = if (lyricStyle.centerText) {
                                    Alignment.CenterHorizontally
                                } else {
                                    Alignment.Start
                                },
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
                                    KaraokeText(
                                        line = line,
                                        playheadSeconds = smoothPlayhead,
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
                                        fontWeight = FontWeight.Bold,
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
                                        fontWeight = FontWeight.Bold,
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
    }
}

/**
 * 用透明度遮罩让歌词在视口顶/底边缘柔和消失，而不是被列表边界硬裁切。
 * DstIn 只修改歌词层自身的 alpha，不会在动态模糊封面上盖一层固定底色。
 */
private fun Modifier.lyricEdgeFade(topFade: Dp, bottomFade: Dp): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val halfHeight = size.height / 2f
            val topFadePx = topFade.toPx().coerceIn(0f, halfHeight)
            val bottomFadePx = bottomFade.toPx().coerceIn(0f, halfHeight)
            val topStop = (topFadePx / size.height).coerceIn(0f, 0.5f)
            val bottomStop = (1f - bottomFadePx / size.height).coerceIn(0.5f, 1f)

            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    topStop to Color.White,
                    bottomStop to Color.White,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
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

@Composable
private fun KaraokeText(
    line: LrcLine,
    playheadSeconds: Float,
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
        isCurrentLine,
        glowEnabled,
        centerText,
        shadowColor,
    ) {
        TextStyle(
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.35).sp,
            textAlign = if (centerText) TextAlign.Center else TextAlign.Start,
            shadow = if (isCurrentLine && glowEnabled) {
                Shadow(color = shadowColor, blurRadius = 12f)
            } else {
                null
            },
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
        val charProgress = remember(
            line,
            playheadSeconds,
            lineEndTime,
            isCurrentLine,
            karaokeEnabled,
        ) {
            calculateKaraokeProgress(
                line = line,
                playheadSeconds = playheadSeconds,
                lineEndTime = lineEndTime,
                isCurrentLine = isCurrentLine,
                karaokeEnabled = karaokeEnabled,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { textLayout.size.height.toDp() })
                .semantics { text = AnnotatedString(line.text) },
        ) {
            drawText(
                textLayoutResult = textLayout,
                color = baseColor,
                topLeft = Offset.Zero,
            )

            if (!isCurrentLine || charProgress.isEmpty()) return@Canvas

            // 文本排版只计算一次；高亮进度仅裁剪已经排好的同一份文本，
            // 因此逐帧更新不会改变英文字母的 kerning、字距或换行位置。
            val highlightPath = Path()
            charProgress.forEachIndexed { charIndex, progress ->
                if (progress <= 0f) return@forEachIndexed
                if (charIndex >= line.text.length) return@forEachIndexed

                val box = textLayout.getBoundingBox(charIndex)
                val textLine = textLayout.getLineForOffset(charIndex)
                val lineTop = textLayout.getLineTop(textLine)
                val lineBottom = textLayout.getLineBottom(textLine)

                val right = box.left + box.width * progress.coerceIn(0f, 1f)
                if (right > box.left) {
                    highlightPath.addRect(
                        Rect(
                            left = box.left,
                            top = lineTop,
                            right = right,
                            bottom = lineBottom.coerceAtLeast(lineTop + 1f),
                        ),
                    )
                }
            }

            if (!highlightPath.isEmpty) {
                clipPath(highlightPath) {
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

private fun calculateKaraokeProgress(
    line: LrcLine,
    playheadSeconds: Float,
    lineEndTime: Float,
    isCurrentLine: Boolean,
    karaokeEnabled: Boolean,
): FloatArray {
    if (line.text.isEmpty() || !isCurrentLine) return FloatArray(0)

    val progress = FloatArray(line.text.length)
    if (!karaokeEnabled || line.syllables.isEmpty()) {
        // 行级 LRC 不模拟逐字扫光，整行直接进入高亮色。
        progress.fill(1f)
        return progress
    }

    fun markSweep(start: Int, end: Int, fraction: Float) {
        if (start >= end) return
        val boundary = start + (end - start) * fraction.coerceIn(0f, 1f)
        for (charIndex in start until end) {
            progress[charIndex] = (boundary - charIndex).coerceIn(0f, 1f)
        }
    }

    var offset = 0
    line.syllables.forEachIndexed { syllableIndex, syllable ->
        val start = offset.coerceAtMost(line.text.length)
        val end = (offset + syllable.text.length).coerceAtMost(line.text.length)
        val syllableEnd = line.syllables.getOrNull(syllableIndex + 1)?.time ?: lineEndTime
        val syllableProgress = (
            (playheadSeconds - syllable.time) /
                (syllableEnd - syllable.time).coerceAtLeast(0.12f)
            ).coerceIn(0f, 1f)
        markSweep(start, end, syllableProgress)
        offset += syllable.text.length
    }
    if (offset < line.text.length) {
        val fallbackProgress = ((playheadSeconds - line.time) /
            (lineEndTime - line.time).coerceAtLeast(0.2f)).coerceIn(0f, 1f)
        markSweep(offset, line.text.length, fallbackProgress)
    }
    return progress
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
                animationSpec = tween(
                    durationMillis = 320,
                    easing = AppleLyricsEasing,
                ),
            )
        } else {
            listState.scrollBy(delta)
        }
    }
}

private val AppleLyricsEasing = CubicBezierEasing(0.39f, 0.575f, 0.565f, 1f)
