package com.neko.music.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.neko.music.data.model.Music
import com.neko.music.ui.screens.PlayerScreen

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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val heightPx = constraints.maxHeight.toFloat()
        val anchoredTopPx = startTopPx.coerceIn(0f, heightPx)
        val cornerPx = with(density) { 30.dp.toPx() }
        val shadowPx = with(density) { 24.dp.toPx() }

        // 交互期间使用独立纯色背景，只改变透明度，不跟随页面缩放或位移。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = (progress().coerceIn(0f, 1f) * 5f).coerceIn(0f, 1f)
                }
                .background(MaterialTheme.colorScheme.background),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val fraction = progress().coerceIn(0f, 1f)
                    val corner = cornerPx * (1f - fraction)
                    shape = RoundedCornerShape(
                        topStart = CornerSize(corner),
                        topEnd = CornerSize(corner),
                        bottomEnd = CornerSize(0f),
                        bottomStart = CornerSize(0f),
                    )
                    clip = true
                    // 从迷你播放器上沿开始展开，向上拖动距离与页面位移保持 1:1。
                    translationY = anchoredTopPx * (1f - fraction)
                    alpha = if (fraction > 0f) 1f else 0f
                    shadowElevation = shadowPx * fraction
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
            )
        }
    }
}
