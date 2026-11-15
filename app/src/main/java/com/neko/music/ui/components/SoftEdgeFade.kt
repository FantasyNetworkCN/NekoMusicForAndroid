package com.neko.music.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * 让可滚动内容在视口顶/底边缘柔和消失，而不是被容器边界硬裁切。
 *
 * DstIn 只修改当前内容层自身的 alpha，不会在动态模糊背景上盖一层固定底色。
 */
internal fun Modifier.softEdgeFade(
    topFade: Dp,
    bottomFade: Dp,
): Modifier = this
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
