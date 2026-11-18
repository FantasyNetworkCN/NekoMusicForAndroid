package com.neko.music.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.neko.music.data.model.Music
import com.neko.music.ui.screens.PlayerScreen
import kotlinx.coroutines.flow.first
import kotlin.math.min

/**
 * 播放页的交互式呈现层。
 *
 * [progress] 为 0 时页面收在屏幕底部，为 1 时完全展开。拖动期间直接读取动画值，
 * 因此不会因为每一帧位移而重组整棵播放页。
 */
@Composable
fun PlayerPresentationHost(
    music: Music,
    modifier: Modifier = Modifier,
    startTopPx: Float,
    startBounds: Rect? = null,
    progress: () -> Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    onDragCancel: () -> Unit,
    onBackClick: () -> Unit,
    onPlaylistClick: () -> Unit,
) {
    // 该返回处理位于 NavHost 之后，确保系统返回优先收起播放呈现层，而不是先弹路由。
    BackHandler(onBack = onBackClick)

    var commentsPageReady by remember(music.id) { mutableStateOf(false) }
    var lyricsPageReady by remember(music.id) { mutableStateOf(false) }
    LaunchedEffect(music.id) {
        // 首屏只组合封面页，评论和歌词两棵重层在展开落定后隔帧接入，
        // 避免它们的首次布局与外壳展开动画争抢同一批帧。
        snapshotFlow { progress() >= 0.985f }.first { it }
        withFrameNanos { }
        commentsPageReady = true
        withFrameNanos { }
        lyricsPageReady = true
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val heightPx = constraints.maxHeight.toFloat()
        val anchoredTopPx = startTopPx.coerceIn(0f, heightPx)
        val cornerPx = with(density) { 34.dp.toPx() }
        val shadowPx = with(density) { 24.dp.toPx() }
        val shellColor = MaterialTheme.colorScheme.background

        // 背景退让层只读取动画值，不参与组合；播放页升起时同步压暗后景。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val fraction = progress().coerceIn(0f, 1f)
                    alpha = 0.32f * smoothStep(fraction, 0f, 0.72f)
                }
                .background(Color.Black),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .playerShellMorph(
                    progress = progress,
                    startBounds = startBounds,
                    fallbackTopPx = anchoredTopPx,
                    cornerPx = cornerPx,
                    shadowPx = shadowPx,
                    shellColor = shellColor,
                ),
        ) {
            // 纯色底只遮住页面已经覆盖的区域，背后仍能连续读到底部退让的层次。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val fraction = progress().coerceIn(0f, 1f)
                        val reveal = smoothStep(fraction, 0.02f, 0.34f)
                        alpha = reveal
                        translationY = 6.dp.toPx() * (1f - reveal)
                    },
            ) {
                PlayerScreen(
                    music = music,
                    onBackClick = onBackClick,
                    onPlaylistClick = onPlaylistClick,
                    onDismissDragStart = onDragStart,
                    onDismissDrag = onDrag,
                    onDismissDragEnd = onDragEnd,
                    onDismissDragCancel = onDragCancel,
                    commentsPageReady = commentsPageReady,
                    lyricsPageReady = lyricsPageReady,
                )
            }
        }
    }
}

/**
 * 迷你播放栏与全屏播放器之间共享的外壳几何。
 *
 * 四条边共用同一个主进度：起点取迷你栏真实 bounds，终点为全屏。外壳先只呈现
 * 背景和圆角，播放器内容随壳体成形淡入；内容始终按全屏宽度布局，仅为贴合壳体
 * 做横向缩放与纵向裁切，避免把整页压扁。
 */
private fun Modifier.playerShellMorph(
    progress: () -> Float,
    startBounds: Rect?,
    fallbackTopPx: Float,
    cornerPx: Float,
    shadowPx: Float,
    shellColor: Color,
): Modifier = drawWithContent {
    val contentDrawScope = this
    val fraction = progress().coerceIn(0f, 1f)
    if (fraction <= 0f) return@drawWithContent

    // 几何直接跟随同一条主弹簧；不要再叠第二层 easing，否则外壳与内容会错拍。
    val shellReveal = fraction
    val layerAlpha = smoothStep(fraction, 0.015f, 0.18f)
    if (layerAlpha <= 0f) return@drawWithContent

    val fallbackStart = Rect(
        left = 0f,
        top = fallbackTopPx.coerceIn(0f, size.height),
        right = size.width,
        bottom = size.height,
    )
    val start = (startBounds ?: fallbackStart).let { bounds ->
        Rect(
            left = bounds.left.coerceIn(0f, size.width),
            top = bounds.top.coerceIn(0f, size.height),
            right = bounds.right.coerceIn(0f, size.width),
            bottom = bounds.bottom.coerceIn(0f, size.height),
        )
    }
    val left = start.left + (0f - start.left) * shellReveal
    val top = start.top + (0f - start.top) * shellReveal
    val right = start.right + (size.width - start.right) * shellReveal
    val bottom = start.bottom + (size.height - start.bottom) * shellReveal
    val shellWidth = (right - left).coerceAtLeast(1f)
    val shellHeight = (bottom - top).coerceAtLeast(1f)
    val flatten = smoothStep(fraction, 0.72f, 1f)
    val startCorner = min(cornerPx, min(shellWidth, shellHeight) * 0.5f)
    val corner = min(startCorner * (1f - flatten), min(shellWidth, shellHeight) * 0.5f)
    val shellPath = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(left, top, right, bottom),
                cornerRadius = CornerRadius(corner),
            )
        )
    }

    drawRoundRect(
        color = Color.Black.copy(alpha = 0.16f * layerAlpha),
        topLeft = Offset(left, top + shadowPx * 0.18f),
        size = Size(shellWidth, shellHeight),
        cornerRadius = CornerRadius(corner),
    )
    clipPath(shellPath) {
        drawRect(
            color = shellColor.copy(alpha = layerAlpha),
            topLeft = Offset(left, top),
            size = Size(shellWidth, shellHeight),
        )
        withTransform({
            translate(left = left, top = top)
            scale(
                scaleX = shellWidth / size.width.coerceAtLeast(1f),
                scaleY = 1f,
                pivot = Offset.Zero,
            )
        }) {
            contentDrawScope.drawContent()
        }
    }
}

private fun smoothStep(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    val progress = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return progress * progress * (3f - 2f * progress)
}
