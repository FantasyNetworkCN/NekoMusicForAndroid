package com.neko.music.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.neko.music.R
import com.neko.music.ui.screens.LrcLine
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.ui.theme.lyricHighlightColorFromPrefs
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Apple 主题歌词：独立行追赶、字符揭示与长音呼吸。 */
@Composable
fun AmllLyricsView(
    lyrics: List<LrcLine>,
    currentProgressSeconds: State<Float>,
    isLoading: Boolean,
    isPlaying: Boolean = false,
    playbackSpeed: Float = 1f,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    active: Boolean = true,
    scrubPositionSeconds: Float? = null,
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
            if (key == null || key in LyricStylePreferences.keys) lyricStyleRev++
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
    val lyricTextColor = if (isDarkTheme) Color.White else Color(0xFF111114)
    val displayLyrics = remember(lyrics) { appleDisplayLyrics(lyrics) }
    val playhead = remember(lyrics) { mutableFloatStateOf(currentProgressSeconds.value) }
    var anchorPosition by remember(lyrics) { mutableFloatStateOf(currentProgressSeconds.value) }
    var anchorRealtime by remember(lyrics) { mutableStateOf(SystemClock.elapsedRealtime()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // 位置回调只校准时钟，逐帧进度只在当前行的绘制阶段读取。
    LaunchedEffect(currentProgressSeconds, playhead) {
        snapshotFlow { currentProgressSeconds.value }.collect { position ->
            anchorPosition = position
            anchorRealtime = SystemClock.elapsedRealtime()
            playhead.floatValue = position
        }
    }
    LaunchedEffect(isPlaying, playbackSpeed, active, foreground, playhead) {
        anchorPosition = currentProgressSeconds.value
        anchorRealtime = SystemClock.elapsedRealtime()
        playhead.floatValue = anchorPosition
        if (!isPlaying || !active || !foreground) return@LaunchedEffect
        while (true) {
            withFrameNanos {
                playhead.floatValue = anchorPosition +
                    (SystemClock.elapsedRealtime() - anchorRealtime) / 1000f * playbackSpeed.coerceAtLeast(0f)
            }
        }
    }
    val position = remember(playhead, scrubPositionSeconds) {
        derivedStateOf { scrubPositionSeconds ?: playhead.floatValue }
    }
    val activeIndexes by remember(displayLyrics, position) {
        derivedStateOf { appleActiveLineIndexes(displayLyrics, position.value) }
    }
    val currentIndex = activeIndexes.minOrNull() ?: -1
    var isUserBrowsing by remember(displayLyrics) { mutableStateOf(false) }
    var touchHeld by remember(displayLyrics) { mutableStateOf(false) }
    var browseRevision by remember(displayLyrics) { mutableIntStateOf(0) }
    var followRevision by remember(displayLyrics) { mutableIntStateOf(0) }
    var selectedLineIndices by remember(displayLyrics) { mutableStateOf(emptySet<Int>()) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val motion = remember(displayLyrics, lyricStyle, density) { AppleLyricScrollState(displayLyrics) }
    val browsingConnection = remember(motion) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && abs(available.y) > 0.01f) {
                    isUserBrowsing = true
                    browseRevision++
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(browseRevision, touchHeld, selectedLineIndices, listState.isScrollInProgress) {
        if (!isUserBrowsing || touchHeld || listState.isScrollInProgress || selectedLineIndices.isNotEmpty()) {
            return@LaunchedEffect
        }
        delay(3000)
        isUserBrowsing = false
        followRevision++
    }
    LaunchedEffect(isUserBrowsing, selectedLineIndices) {
        if (isUserBrowsing || selectedLineIndices.isNotEmpty()) motion.clear(currentIndex)
    }
    BackHandler(enabled = selectedLineIndices.isNotEmpty()) { selectedLineIndices = emptySet() }

    Box(modifier.fillMaxSize()) {
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = highlightColor)
            }
            displayLyrics.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_lyrics), fontSize = 16.sp, color = lyricTextColor.copy(alpha = 0.45f))
            }
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val viewportHeightPx = with(density) { maxHeight.toPx() }
                val lineSpacingPx = with(density) { lyricStyle.lineSpacingDp.dp.toPx() }
                val primaryHeightPx = with(density) { (lyricStyle.fontSizeSp * 1.2f).sp.toPx() }
                val annotationHeightPx = primaryHeightPx * 0.65f + with(density) { 4.dp.toPx() }
                val stridePx = primaryHeightPx + annotationHeightPx * 2f + lineSpacingPx
                fun estimatedHeight(index: Int): Float = motion.rowHeights[index]?.toFloat() ?: run {
                    val line = displayLyrics[index]
                    if (line.text.isBlank()) with(density) { 48.dp.toPx() } else {
                        primaryHeightPx +
                            (if (lyricStyle.showRomanization && line.romanization.isNotBlank()) annotationHeightPx else 0f) +
                            (if (lyricStyle.showTranslation && line.translation.isNotBlank()) annotationHeightPx else 0f)
                    }
                }
                val reveal = remember(motion) { Animatable(0f) }
                LaunchedEffect(motion.positioned, motion) {
                    if (motion.positioned) reveal.animateTo(1f, spring(dampingRatio = 1f, stiffness = 320f))
                }
                LaunchedEffect(
                    currentIndex, isUserBrowsing, selectedLineIndices, viewportHeightPx,
                    followRevision, motion, scrubPositionSeconds,
                ) {
                    if (isUserBrowsing || selectedLineIndices.isNotEmpty()) return@LaunchedEffect
                    // 等待首轮测量后定位，再开始淡入，避免从列表顶部飞到当前句。
                    withFrameNanos { }
                    motion.follow(
                        listState, currentIndex, viewportHeightPx, ::estimatedHeight,
                        lyricStyle.smoothScrollEnabled, lyricStyle.scaleCurrentLine,
                        playbackSpeed, position.value,
                        forcePosition = scrubPositionSeconds != null,
                    )
                }
                val flat by animateFloatAsState(
                    if (scrubPositionSeconds != null) 1f else 0f,
                    tween(180), label = "apple_scrub_flat",
                )
                val visibleItems by remember(listState) {
                    derivedStateOf { listState.layoutInfo.visibleItemsInfo.associateBy { it.index } }
                }
                val browsingItems = if (isUserBrowsing) visibleItems else emptyMap()
                // 扩展实际布局的预绘制区，让下方尚未露出的行先完成各自的延迟追赶。
                Layout(
                    modifier = Modifier.fillMaxSize().clipToBounds()
                        .graphicsLayer { alpha = reveal.value }
                        .softEdgeFade(topFade = 52.dp, bottomFade = 84.dp),
                    content = {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().nestedScroll(browsingConnection)
                                .pointerInput(motion) {
                                    awaitEachGesture {
                                        awaitFirstDown(false, PointerEventPass.Initial)
                                        touchHeld = true
                                        try {
                                            do {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                            } while (event.changes.any { it.pressed })
                                        } finally { touchHeld = false }
                                    }
                                },
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            item(key = "apple-lyrics-top") {
                                Spacer(Modifier.height(maxHeight * AppleLyricMotion.FocusPosition))
                            }
                            itemsIndexed(displayLyrics, key = { index, line -> "${line.time}:$index" }) { index, line ->
                                val selected = index in selectedLineIndices
                                val isCurrentLine = index in activeIndexes
                                val focus by animateFloatAsState(
                                    if (selected || isCurrentLine) 1f else 0f,
                                    tween(AppleLyricMotion.FocusColorDurationMs, easing = AppleLyricsSmoothStepEasing),
                                    label = "apple_line_focus",
                                )
                                val scale by animateFloatAsState(
                                    if (lyricStyle.scaleCurrentLine && (selected || index == currentIndex)) {
                                        AppleLyricMotion.CurrentLineScale
                                    } else 1f,
                                    tween(AppleLyricMotion.ScaleDurationMs, easing = AppleLyricsSpringEasing(AppleLyricMotion.ScaleBounce)),
                                    label = "apple_line_scale",
                                )
                                val distance = if (isUserBrowsing) {
                                    val item = browsingItems[index + 1]
                                    if (item != null) abs(item.offset + item.size * 0.5f - viewportHeightPx * AppleLyricMotion.FocusPosition) / stridePx
                                    else abs(index - currentIndex).toFloat()
                                } else abs(index - currentIndex).toFloat()
                                val referenceAlpha = appleDistanceOpacity(distance, focus)
                                // 默认使用参考景深，用户设置相对于默认值调整，避免设置滑块失效。
                                val customDim = lyricStyle.inactiveAlpha / LyricStylePreferences.DEFAULT_INACTIVE_ALPHA
                                val rowAlpha = if (selected) 1f else (
                                    referenceAlpha * (customDim + (1f - customDim) * focus) *
                                        lyricStyle.activeAlpha / LyricStylePreferences.DEFAULT_ACTIVE_ALPHA
                                    ).coerceIn(0f, 1f)
                                val adjacentBlur = when (index) {
                                    motion.focusIndex - 1 -> 2.4f
                                    motion.focusIndex + 1 -> 0.7f
                                    else -> 0f
                                } * AppleLyricMotion.BlurIntensity * (1f - focus)
                                val blur by animateFloatAsState(
                                    if (selected || rowAlpha < AppleLyricMotion.BlurSkipAlpha) 0f
                                    else max(appleDistanceBlurRadius(distance, focus), adjacentBlur),
                                    tween(AppleLyricMotion.BlurSmoothDurationMs), label = "apple_line_blur",
                                )
                                val quantizedBlur = ((blur * (1f - flat)) * 2f).roundToInt() / 2f
                                val unplayedAlpha = (appleDistanceOpacity(1f, 0f) / rowAlpha.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                        .onSizeChanged { motion.rowHeights[index] = it.height }
                                        .graphicsLayer {
                                            translationY = motion.offset(index) * (1f - flat)
                                            scaleX = scale + (1f - scale) * flat
                                            scaleY = scaleX
                                            alpha = rowAlpha + ((if (isCurrentLine) 1f else 0.62f) - rowAlpha) * flat
                                            transformOrigin = TransformOrigin(if (lyricStyle.centerText) 0.5f else 0f, 0f)
                                        }
                                        .drawBehind {
                                            if (selected) drawRoundRect(highlightColor.copy(alpha = 0.12f), cornerRadius = CornerRadius(14.dp.toPx()))
                                        }
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                                            onClick = {
                                                if (selectedLineIndices.isNotEmpty()) {
                                                    if (line.text.isNotBlank()) selectedLineIndices =
                                                        if (selected) selectedLineIndices - index else selectedLineIndices + index
                                                } else if (line.text.isNotBlank()) {
                                                    isUserBrowsing = false
                                                    followRevision++
                                                    onLineClick(line.time)
                                                }
                                            },
                                            onLongClick = {
                                                if (line.text.isNotBlank()) {
                                                    selectedLineIndices = if (selected) selectedLineIndices - index else selectedLineIndices + index
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            },
                                        )
                                        .padding(horizontal = 32.dp),
                                    horizontalAlignment = if (lyricStyle.centerText) Alignment.CenterHorizontally else Alignment.Start,
                                ) {
                                    val endTime = appleLineEndTime(displayLyrics, index)
                                    if (line.text.isBlank()) {
                                        InstrumentalDots(line, endTime, position, isCurrentLine, highlightColor)
                                    } else {
                                        AppleKaraokeText(
                                            line = line, playheadSeconds = { position.value }, lineEndTime = endTime,
                                            timed = lyricStyle.karaokeEnabled && (isCurrentLine || focus > 0.001f),
                                            baseColor = lyricTextColor, highlightColor = highlightColor,
                                            fontSize = lyricStyle.fontSizeSp.sp, lineHeight = (lyricStyle.fontSizeSp * 1.2f).sp,
                                            glowEnabled = lyricStyle.glowEnabled, centerText = lyricStyle.centerText,
                                            timingStrength = if (isCurrentLine) 1f else focus,
                                            unplayedAlpha = unplayedAlpha + (appleDistanceOpacity(1f, 0f) - unplayedAlpha) * flat,
                                            softBlurDp = quantizedBlur,
                                        )
                                    }
                                    val annotationSize = (lyricStyle.fontSizeSp * 0.65f).sp
                                    if (lyricStyle.showRomanization && line.romanization.isNotBlank()) {
                                        Text(line.romanization, fontSize = annotationSize, lineHeight = annotationSize * 1.2f,
                                            fontFamily = FontFamily.Default, fontWeight = FontWeight.Black,
                                            color = lyricTextColor.copy(alpha = 0.9f),
                                            textAlign = if (lyricStyle.centerText) TextAlign.Center else TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth().blur(quantizedBlur.dp).padding(top = 4.dp))
                                    }
                                    if (lyricStyle.showTranslation && line.translation.isNotBlank()) {
                                        Text(line.translation, fontSize = annotationSize, lineHeight = annotationSize * 1.2f,
                                            fontFamily = FontFamily.Default, fontWeight = FontWeight.Black,
                                            color = lyricTextColor.copy(alpha = 0.9f),
                                            textAlign = if (lyricStyle.centerText) TextAlign.Center else TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth().blur(quantizedBlur.dp).padding(top = 4.dp))
                                    }
                                }
                                if (index < displayLyrics.lastIndex) {
                                    Spacer(Modifier.height(if (line.text.isBlank()) 8.dp else lyricStyle.lineSpacingDp.dp))
                                }
                            }
                            item(key = "apple-lyrics-bottom") {
                                Spacer(Modifier.height(maxHeight * (1f - AppleLyricMotion.FocusPosition) + with(density) { (stridePx * 4f).toDp() }))
                            }
                        }
                    },
                ) { measurables, constraints ->
                    val expanded = (constraints.maxHeight.toLong() + (stridePx * 4f).roundToInt()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    val list = measurables.single().measure(constraints.copy(minHeight = expanded, maxHeight = expanded))
                    layout(constraints.maxWidth, constraints.maxHeight) { list.placeRelative(0, 0) }
                }
            }
        }
        if (selectedLineIndices.isNotEmpty()) {
            val selectedText = selectedLineIndices.sorted().mapNotNull(displayLyrics::getOrNull).flatMap { line ->
                buildList {
                    if (line.text.isNotBlank()) add(line.text)
                    if (lyricStyle.showRomanization && line.romanization.isNotBlank()) add(line.romanization)
                    if (lyricStyle.showTranslation && line.translation.isNotBlank()) add(line.translation)
                }
            }.joinToString("\n")
            LyricSelectionToolbar(
                selectedCount = selectedLineIndices.size, isDarkTheme = isDarkTheme, accentColor = highlightColor,
                onCopy = {
                    if (selectedText.isNotBlank()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.player_tab_lyrics), selectedText))
                        Toast.makeText(context, R.string.lyric_selection_copied, Toast.LENGTH_SHORT).show()
                    }
                    selectedLineIndices = emptySet()
                },
                onShare = {
                    if (selectedText.isNotBlank()) context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, selectedText) },
                        context.getString(R.string.lyric_selection_share),
                    ))
                    selectedLineIndices = emptySet()
                },
                onCancel = { selectedLineIndices = emptySet() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 18.dp),
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
            .heightIn(min = 48.dp)
            .padding(horizontal = 10.dp, vertical = 14.dp),
    )
}

@Composable
private fun InstrumentalDots(
    line: LrcLine,
    endTime: Float,
    playhead: State<Float>,
    active: Boolean,
    color: Color,
) {
    // 空间在排版时预留，播放进入间奏时只更新绘制，不插入或移除列表项。
    Canvas(Modifier.fillMaxWidth(0.28f).height(48.dp)) {
        if (!active) return@Canvas
        val now = playhead.value
        val remaining = endTime - now
        if (remaining <= 0f || now < line.time) return@Canvas
        val duration = (endTime - line.time).coerceAtLeast(0.001f)
        val elapsed = now - line.time
        val radius = 5.dp.toPx()
        val breathe = 1f + sin(elapsed / 1.5f * 2f * Math.PI.toFloat()) * 0.05f
        repeat(3) { index ->
            val progress = ((elapsed - duration * index / 3f) / (duration / 3f)).coerceIn(0.25f, 1f)
            drawCircle(
                color.copy(alpha = progress * (remaining / 0.375f).coerceIn(0f, 1f)),
                radius * breathe * if (index == 2) 1f + progress * 0.18f else 1f,
                Offset(radius + index * 18.dp.toPx(), size.height / 2f),
            )
        }
    }
}
