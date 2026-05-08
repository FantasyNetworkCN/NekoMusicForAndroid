package com.neko.music.ui.components

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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

/**
 * [Glass Slider](https://kyant.gitbook.io/backdrop/tutorials/glass-slider) 变体：
 * 透明 `trackBackdrop` 铺满 Tab 区供 `rememberCombinedBackdrop` 采样；拇指为 **胶囊形**（圆角半径 =
 * `min(宽, 高) / 2`，两端半圆而非扁长方条），叠在 Tab 文字背后随选中项平移。
 */
@Composable
fun NavigationGlassSlider(
    mainBackdrop: Backdrop?,
    selectedIndex: Int,
    tabCount: Int = 3,
    /** 水平拖动产生的额外偏移（与 Tab 切换动画叠加） */
    dragOffsetXDp: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val safeCount = tabCount.coerceAtLeast(1)
    val idx = selectedIndex.coerceIn(0, safeCount - 1)

    if (mainBackdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        NavigationGlassSliderFallback(
            selectedIndex = idx,
            tabCount = safeCount,
            dragOffsetXDp = dragOffsetXDp,
            modifier = modifier
        )
        return
    }

    val trackBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(mainBackdrop, trackBackdrop)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val segment = maxWidth / safeCount
        val horizontalInset = 4.dp
        val thumbW = (segment - horizontalInset * 2).coerceAtLeast(12.dp)
        val verticalInset = 4.dp
        val thumbH = (maxHeight - verticalInset * 2).coerceAtLeast(28.dp)
        val thumbY = verticalInset
        val capsuleRadius = minOf(thumbW, thumbH) / 2
        val thumbShape = RoundedCornerShape(capsuleRadius)
        val targetOffsetX = segment * idx + horizontalInset

        val thumbOffsetX by animateDpAsState(
            targetValue = targetOffsetX,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "navGlassThumb"
        )

        // 文档：独立 track layer；此处不画可见横条，仅占位供拇指 combined 折射
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(trackBackdrop)
        )

        Box(
            modifier = Modifier
                .offset(x = thumbOffsetX + dragOffsetXDp, y = thumbY)
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
    selectedIndex: Int,
    tabCount: Int,
    dragOffsetXDp: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val safeCount = tabCount.coerceAtLeast(1)
        val segment = maxWidth / safeCount
        val horizontalInset = 4.dp
        val thumbW = (segment - horizontalInset * 2).coerceAtLeast(12.dp)
        val verticalInset = 4.dp
        val thumbH = (maxHeight - verticalInset * 2).coerceAtLeast(28.dp)
        val thumbY = verticalInset
        val capsuleRadius = minOf(thumbW, thumbH) / 2
        val thumbShape = RoundedCornerShape(capsuleRadius)
        val targetOffsetX = segment * selectedIndex + horizontalInset

        val thumbOffsetX by animateDpAsState(
            targetValue = targetOffsetX,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "navGlassThumbFb"
        )

        Box(
            modifier = Modifier
                .offset(x = thumbOffsetX + dragOffsetXDp, y = thumbY)
                .size(thumbW, thumbH)
                .background(Color.White.copy(alpha = 0.22f), thumbShape)
        )
    }
}
