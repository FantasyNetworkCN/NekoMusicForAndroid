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
import com.kyant.backdrop.backdrops.LayerBackdrop
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
 * [Glass Bottom Bar](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar) 一致。也可传 [sampleBackdrop] 显式指定采样源。
 *
 * 勿在 `drawBackdrop` **之前**再 `.clip(shape)`：会干扰部分机型上的 [RenderEffect](https://kyant.gitbook.io/backdrop/api/backdrop-effects.md)（折射/模糊看起来像失效）。
 * 若在已应用 `layerBackdrop(同一 LayerBackdrop)` 的区域内再对同一实例 `drawBackdrop`，或 LazyColumn 多行共享
 * 同一 export 并各自 `drawBackdrop`，会触发 RenderThread SIGSEGV。
 *
 * 液态叠色随 [MaterialTheme] 深浅色：暗色为深色霜化底 + 弱顶光，浅色为白霜化（与系统玻璃语义一致）。
 *
 * Kyant 要求效果顺序为 **color filter ⇒ blur ⇒ lens**；可选的 [opacity] 须在 vibrancy/blur 之前。
 * 官方 [Glass Bottom Bar](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar) 终稿 **未** 对采样层做 opacity，
 * 额外衰减易让模糊/折射看起来像「假磨砂」；默认 **1** 表示不应用该 color filter。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    /**
     * 与 `Modifier.layerBackdrop(同一实例)` 对应；非空时优先于 [LocalLiquidLayerBackdrop]。
     * 对齐教程在父级 `rememberLayerBackdrop { drawRect(..); drawContent() }` 后传入同一 [LayerBackdrop]。
     */
    sampleBackdrop: LayerBackdrop? = null,
    backgroundAlpha: Float = 0.28f,
    borderAlpha: Float = 0.14f,
    highlightAlpha: Float = 0.08f,
    borderColor: Color = Color.White,
    /**
     * 对采样到的 backdrop 做矩阵透明度（Kyant [opacity](https://kyant.gitbook.io/backdrop/api/backdrop-effects#opacity)），
     * 仅 `LocalLiquidLayerBackdrop` 非空且 API 31+ 时生效；与 [liquidBlur] 等同属液态路径。
     */
    @FloatRange(from = 0.0, to = 1.0) liquidBackdropOpacity: Float = 1f,
    /** Kyant 液态路径专用；教程 [Glass Bottom Bar](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar) 底栏约 4.dp。 */
    liquidBlur: Dp = 14.dp,
    liquidLensHeight: Dp = 16.dp,
    liquidLensAmount: Dp = 32.dp,
    content: @Composable () -> Unit
) {
    val backdrop = sampleBackdrop ?: LocalLiquidLayerBackdrop.current
    val density = LocalDensity.current
    val ui = LocalLiquidGlassUiScale.current
    val bgAlpha = (backgroundAlpha * ui.tintStrength).coerceIn(0.02f, 1f)
    val bdAlpha = (borderAlpha * ui.tintStrength).coerceIn(0.02f, 1f)
    val hiAlpha = (highlightAlpha * ui.tintStrength).coerceIn(0.02f, 1f)
    val liqBlur = liquidBlur.scaledBy(ui.blurStrength)
    val liqLensH = liquidLensHeight.scaledBy(ui.lensHeightStrength)
    val liqLensAmt = liquidLensAmount.scaledBy(ui.lensAmountStrength)
    val useLiquid = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    if (useLiquid) {
        val blurPx = with(density) { liqBlur.toPx() }
        val lensH = with(density) { liqLensH.toPx() }
        val lensAmt = with(density) { liqLensAmt.toPx() }
        // 教程 onDrawSurface 约半透明白；过厚会盖住 vibrancy/blur/lens。
        val frostTop = (hiAlpha * 0.75f).coerceIn(0.04f, 0.11f)
        val frostBase = (bgAlpha * 0.16f).coerceIn(0.05f, 0.13f)
        val darkFrostBase = (bgAlpha * 0.12f).coerceIn(0.04f, 0.11f)
        val darkFrostTop = (hiAlpha * 0.55f).coerceIn(0.03f, 0.08f)
        val opacitySample = liquidBackdropOpacity.coerceIn(0f, 1f)
        Box(
            modifier = modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        if (opacitySample < 0.999f) {
                            opacity(opacitySample)
                        }
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
                                        Color.White.copy(alpha = darkFrostTop * 0.35f),
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
                    color = borderColor.copy(alpha = bdAlpha),
                    shape = shape
                )
        ) {
            content()
        }
    } else {
        val fallbackFill =
            if (isDarkTheme) Color(0xFF1A1A2E).copy(alpha = bgAlpha)
            else MaterialTheme.colorScheme.surface.copy(alpha = (bgAlpha * 1.15f).coerceIn(0.35f, 0.92f))
        val fallbackSheenTop =
            if (isDarkTheme) Color.White.copy(alpha = hiAlpha * 0.55f)
            else Color.White.copy(alpha = hiAlpha)
        Box(
            modifier = modifier
                .clip(shape)
                .background(fallbackFill)
                .border(
                    width = 0.5.dp,
                    color = borderColor.copy(alpha = bdAlpha),
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
