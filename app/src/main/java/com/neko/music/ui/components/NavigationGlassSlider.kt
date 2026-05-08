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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

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
 */
@Composable
fun NavigationGlassSlider(
    mainBackdrop: Backdrop?,
    tabCount: Int,
    thumbLeftDp: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val safeCount = tabCount.coerceAtLeast(1)

    if (mainBackdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        NavigationGlassSliderFallback(
            tabCount = safeCount,
            thumbLeftDp = thumbLeftDp,
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
                        vibrancy()
                        blur(with(density) { 4.dp.toPx() })
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            lens(
                                refractionHeight = with(density) { 12.dp.toPx() },
                                refractionAmount = with(density) { 16.dp.toPx() },
                                chromaticAberration = true
                            )
                        }
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 0.2f))
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

        Box(
            modifier = Modifier
                .offset(x = thumbX, y = thumbY)
                .size(thumbW, thumbH)
                .background(Color.White.copy(alpha = 0.22f), thumbShape)
        )
    }
}
