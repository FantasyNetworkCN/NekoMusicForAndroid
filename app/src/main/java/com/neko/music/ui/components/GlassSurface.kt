package com.neko.music.ui.components

import android.os.Build
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * 玻璃容器：在 [LocalLiquidLayerBackdrop] 非空且 API 31+ 时使用 Kyant Backdrop（vibrancy + blur；**API 33+ 再加 lens** 折射，接近官方「液态玻璃」教程）。
 *
 * 使用说明与教程见官方文档：https://kyant.gitbook.io/backdrop
 * - 底栏玻璃：https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar
 * - 多层玻璃 / 避免 SIGSEGV：https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-sheet
 *
 * 主界面中 NavHost 内应保持 [LocalLiquidLayerBackdrop] 为 null，仅在已 `layerBackdrop` 的内容之上的悬浮层提供 backdrop。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundAlpha: Float = 0.28f,
    borderAlpha: Float = 0.14f,
    highlightAlpha: Float = 0.08f,
    borderColor: Color = Color.White,
    content: @Composable () -> Unit
) {
    val backdrop = LocalLiquidLayerBackdrop.current
    val density = LocalDensity.current
    val useLiquid = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    if (useLiquid) {
        val blurPx = with(density) { 12.dp.toPx() }
        val lensH = with(density) { 10.dp.toPx() }
        val lensAmt = with(density) { 22.dp.toPx() }
        Box(
            modifier = modifier
                .clip(shape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(blurPx)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            lens(lensH, lensAmt)
                        }
                    },
                    highlight = { null },
                    shadow = { null },
                    innerShadow = null,
                    onDrawSurface = {
                        drawRect(Color(0xFF1A1A2E).copy(alpha = backgroundAlpha))
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = highlightAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                    }
                )
        ) {
            content()
        }
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(Color(0xFF1A1A2E).copy(alpha = backgroundAlpha))
                .border(
                    width = 0.5.dp,
                    color = borderColor.copy(alpha = borderAlpha),
                    shape = shape
                )
        ) {
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
}
