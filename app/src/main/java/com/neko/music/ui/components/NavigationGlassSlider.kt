package com.neko.music.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.opacity
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

internal fun navTabSegmentWidth(maxWidth: Dp, tabCount: Int): Dp =
    maxWidth / tabCount.coerceAtLeast(1)

internal fun navTabThumbWidth(maxWidth: Dp, tabCount: Int): Dp {
    val segment = navTabSegmentWidth(maxWidth, tabCount)
    val horizontalInset = 4.dp
    return (segment - horizontalInset * 2).coerceAtLeast(12.dp)
}

internal fun navTabThumbLeftForIndex(maxWidth: Dp, tabCount: Int, index: Int): Dp {
    val safe = tabCount.coerceAtLeast(1)
    val segment = maxWidth / safe
    val horizontalInset = 4.dp
    val idx = index.coerceIn(0, safe - 1)
    return segment * idx + horizontalInset
}

internal fun navTabThumbClampLeft(left: Dp, maxWidth: Dp, tabCount: Int): Dp {
    val w = navTabThumbWidth(maxWidth, tabCount)
    val maxL = (maxWidth - w).coerceAtLeast(0.dp)
    return left.coerceIn(0.dp, maxL)
}

internal fun navTabThumbClampLeftPx(leftPx: Float, maxWidth: Dp, tabCount: Int, density: Density): Float {
    val wPx = with(density) { navTabThumbWidth(maxWidth, tabCount).toPx() }
    val maxPx = with(density) { maxWidth.toPx() }
    return leftPx.coerceIn(0f, (maxPx - wPx).coerceAtLeast(0f))
}

internal fun navTabThumbLeftPxForIndex(maxWidth: Dp, tabCount: Int, index: Int, density: Density): Float =
    with(density) { navTabThumbLeftForIndex(maxWidth, tabCount, index).toPx() }

internal fun navTabIndexForThumbLeft(thumbLeft: Dp, maxWidth: Dp, tabCount: Int, density: Density): Int {
    val safe = tabCount.coerceAtLeast(1)
    val w = navTabThumbWidth(maxWidth, tabCount)
    val segmentPx = with(density) { navTabSegmentWidth(maxWidth, tabCount).toPx() }
    val centerPx = with(density) { (thumbLeft + w / 2).toPx() }
    return (centerPx / segmentPx).toInt().coerceIn(0, safe - 1)
}

/**
 * [Glass Slider](https://kyant.gitbook.io/backdrop/tutorials/glass-slider) 变体：
 * 透明 `trackBackdrop` 铺满 Tab 区；拇指为 **胶囊形**，**`thumbLeftDp` 由父级驱动**（Animatable + 拖动 snap），保证跟手。
 * [thumbSquishProgress] 参考 AndroidLiquidGlass `LiquidBottomTabs`：按压/拖动时略压扁拇指并带高光。
 *
 * @param darkBarStyle 与底栏背景一致：深色底栏用偏亮拇指叠色，浅色底栏用偏暗叠色（避免「永远像亮色模式」）。
 */
@Composable
fun NavigationGlassSlider(
    mainBackdrop: Backdrop?,
    tabCount: Int,
    thumbLeftDp: Dp,
    /** 0..1，与 LiquidBottomTabs 中 pressProgress 类似，驱动 lens / layerBlock / 高光 */
    thumbSquishProgress: Float = 0f,
    darkBarStyle: Boolean = true,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val safeCount = tabCount.coerceAtLeast(1)

    if (mainBackdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        NavigationGlassSliderFallback(
            tabCount = safeCount,
            thumbLeftDp = thumbLeftDp,
            thumbSquishProgress = thumbSquishProgress,
            darkBarStyle = darkBarStyle,
            modifier = modifier
        )
        return
    }

    val trackBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(mainBackdrop, trackBackdrop)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val horizontalInset = 4.dp
        val thumbW = navTabThumbWidth(maxWidth, safeCount)
        val verticalInset = 4.dp
        val thumbH = (maxHeight - verticalInset * 2).coerceAtLeast(28.dp)
        val thumbY = verticalInset
        val capsuleRadius = minOf(thumbW, thumbH) / 2
        val thumbShape = RoundedCornerShape(capsuleRadius)
        val thumbX = navTabThumbClampLeft(thumbLeftDp, maxWidth, safeCount)
        val p = thumbSquishProgress.coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(trackBackdrop)
        )

        Box(
            modifier = Modifier
                .offset(x = thumbX, y = thumbY)
                .drawBackdrop(
                    backdrop = combinedBackdrop,
                    shape = { thumbShape },
                    effects = {
                        opacity(0.94f)
                        vibrancy()
                        blur(with(density) { lerp(4f, 6f, p).dp.toPx() })
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            lens(
                                refractionHeight = with(density) { lerp(10f, 16f, p).dp.toPx() },
                                refractionAmount = with(density) { lerp(12f, 20f, p).dp.toPx() },
                                chromaticAberration = true
                            )
                        }
                    },
                    highlight = {
                        if (p > 0.02f) Highlight.Default.copy(alpha = p * 0.75f) else null
                    },
                    shadow = {
                        if (p > 0.02f) Shadow.Default.copy(alpha = p * 0.55f) else null
                    },
                    innerShadow = {
                        if (p > 0.02f) {
                            InnerShadow(
                                radius = lerpDp(2.dp, 8.dp, p),
                                alpha = p
                            )
                        } else {
                            null
                        }
                    },
                    layerBlock = if (p > 0.02f) {
                        {
                            val sx = lerp(1f, 1.1f, p)
                            val sy = lerp(1f, 0.93f, p * 0.5f)
                            scaleX = sx
                            scaleY = sy
                        }
                    } else {
                        null
                    },
                    onDrawSurface = {
                        val a0 = if (darkBarStyle) 0.2f else 0.11f
                        val a1 = if (darkBarStyle) 0.12f else 0.07f
                        drawRect(
                            if (darkBarStyle) {
                                Color.White.copy(alpha = lerp(a0, a1, p))
                            } else {
                                Color.Black.copy(alpha = lerp(a0, a1, p))
                            }
                        )
                    }
                )
                .size(thumbW, thumbH)
        )
    }
}

@Composable
private fun NavigationGlassSliderFallback(
    tabCount: Int,
    thumbLeftDp: Dp,
    thumbSquishProgress: Float = 0f,
    darkBarStyle: Boolean = true,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val safeCount = tabCount.coerceAtLeast(1)
        val thumbW = navTabThumbWidth(maxWidth, safeCount)
        val verticalInset = 4.dp
        val thumbH = (maxHeight - verticalInset * 2).coerceAtLeast(28.dp)
        val thumbY = verticalInset
        val capsuleRadius = minOf(thumbW, thumbH) / 2
        val thumbShape = RoundedCornerShape(capsuleRadius)
        val thumbX = navTabThumbClampLeft(thumbLeftDp, maxWidth, safeCount)
        val p = thumbSquishProgress.coerceIn(0f, 1f)
        val sx = lerp(1f, 1.08f, p)
        val sy = lerp(1f, 0.95f, p * 0.45f)

        Box(
            modifier = Modifier
                .offset(x = thumbX, y = thumbY)
                .graphicsLayer {
                    scaleX = sx
                    scaleY = sy
                }
                .size(thumbW, thumbH)
                .background(
                    color = if (darkBarStyle) {
                        Color.White.copy(alpha = lerp(0.22f, 0.16f, p))
                    } else {
                        Color.Black.copy(alpha = lerp(0.12f, 0.09f, p))
                    },
                    shape = thumbShape
                )
        )
    }
}
