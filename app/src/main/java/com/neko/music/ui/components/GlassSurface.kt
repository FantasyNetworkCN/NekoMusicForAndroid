package com.neko.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 毛玻璃效果容器
 * 使用半透明背景 + 顶部高光 + 细边框模拟玻璃质感
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundAlpha: Float = 0.28f,
    borderAlpha: Float = 0.14f,
    highlightAlpha: Float = 0.08f,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF1A1A2E).copy(alpha = backgroundAlpha))
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = borderAlpha),
                shape = shape
            )
    ) {
        // 顶部玻璃反光
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = highlightAlpha),
                            Color.Transparent
                        )
                    )
                )
        )
        content()
    }
}
