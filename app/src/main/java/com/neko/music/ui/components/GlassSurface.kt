package com.neko.music.ui.components

import android.os.Build
import androidx.annotation.FloatRange
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.opacity
import com.kyant.backdrop.effects.vibrancy

/**
 * 玻璃容器：在 [LocalLiquidLayerBackdrop] 非空且 API 31+ 时使用 Kyant Backdrop（vibrancy + blur；**API 33+ 再加 lens** 折射，接近官方「液态玻璃」教程）。
 *
 * 使用说明与教程见官方文档：https://kyant.gitbook.io/backdrop
 * - 底栏玻璃：https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar
 * - 多层玻璃 / 避免 SIGSEGV：https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-sheet
 *
 * [LocalLiquidLayerBackdrop] 仅应在 **`layerBackdrop` 子树之外** 提供（例如底栏 / 迷你播放器），与
 * [Glass Bottom Bar](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar) 一致。
 * 若在已应用 `layerBackdrop(同一 LayerBackdrop)` 的区域内再对同一实例 `drawBackdrop`，或 LazyColumn 多行共享
 * 同一 export 并各自 `drawBackdrop`，会触发 RenderThread SIGSEGV。
 *
 * 液态叠色随 [MaterialTheme] 深浅色：暗色为深色霜化底 + 弱顶光，浅色为白霜化（与系统玻璃语义一致）。
 *
 * Kyant 要求效果顺序为 **color filter ⇒ blur ⇒ lens**；[opacity](https://kyant.gitbook.io/backdrop/api/backdrop-effects#opacity)
 * 属于 color filter，在 [liquidBackdropOpacity] 中传入 0..1，在 vibrancy/blur 之前应用。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundAlpha: Float = 0.28f,
    borderAlpha: Float = 0.14f,
    highlightAlpha: Float = 0.08f,
    borderColor: Color = Color.White,
    /**
     * 对采样到的 backdrop 做矩阵透明度（Kyant [opacity](https://kyant.gitbook.io/backdrop/api/backdrop-effects#opacity)），
     * 仅 `LocalLiquidLayerBackdrop` 非空且 API 31+ 时生效；与 [liquidBlur] 等同属液态路径。
     */
    @FloatRange(from = 0.0, to = 1.0) liquidBackdropOpacity: Float = 0.92f,
    /** Kyant 液态路径专用；教程 [Glass Bottom Bar](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar) 底栏约 4.dp。 */
    liquidBlur: Dp = 14.dp,
    liquidLensHeight: Dp = 16.dp,
    liquidLensAmount: Dp = 32.dp,
    content: @Composable () -> Unit
) {
    val backdrop = LocalLiquidLayerBackdrop.current
    val density = LocalDensity.current
    val useLiquid = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    if (useLiquid) {
        val blurPx = with(density) { liquidBlur.toPx() }
        val lensH = with(density) { liquidLensHeight.toPx() }
        val lensAmt = with(density) { liquidLensAmount.toPx() }
        val frostTop = (highlightAlpha * 2.5f).coerceIn(0.08f, 0.32f)
        val frostBase = (backgroundAlpha * 0.45f).coerceIn(0.08f, 0.2f)
        val darkFrostBase = (backgroundAlpha * 0.55f).coerceIn(0.14f, 0.36f)
        val darkFrostTop = (highlightAlpha * 2.2f).coerceIn(0.08f, 0.22f)
        Box(
            modifier = modifier
                .clip(shape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        opacity(liquidBackdropOpacity.coerceIn(0f, 1f))
                        vibrancy()
                        blur(blurPx)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            lens(lensH, lensAmt)
                        }
                    },
                    innerShadow = null,
                    onDrawSurface = {
                        if (isDarkTheme) {
                            drawRect(Color.Black.copy(alpha = darkFrostBase))
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = darkFrostTop * 0.42f),
                                        Color.Transparent
                                    )
                                )
                            )
                        } else {
                            drawRect(Color.White.copy(alpha = frostBase))
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = frostTop),
                                        Color.White.copy(alpha = 0.04f)
                                    )
                                )
                            )
                        }
                    }
                )
                .border(
                    width = 0.5.dp,
                    color = borderColor.copy(alpha = borderAlpha),
                    shape = shape
                )
        ) {
            content()
        }
    } else {
        val fallbackFill =
            if (isDarkTheme) Color(0xFF1A1A2E).copy(alpha = backgroundAlpha)
            else MaterialTheme.colorScheme.surface.copy(alpha = (backgroundAlpha * 1.15f).coerceIn(0.35f, 0.92f))
        val fallbackSheenTop =
            if (isDarkTheme) Color.White.copy(alpha = highlightAlpha * 0.55f)
            else Color.White.copy(alpha = highlightAlpha)
        Box(
            modifier = modifier
                .clip(shape)
                .background(fallbackFill)
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
                                fallbackSheenTop,
                                Color.Transparent
                            )
                        )
                    )
            )
            content()
        }
    }
}
