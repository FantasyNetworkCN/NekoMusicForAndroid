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
 * 透明 `trackBackdrop` 铺满 Tab 区供 `rememberCombinedBackdrop` 采样；**拇指为圆角玻璃 pill**，
 * 宽度对齐单列 Tab、叠在文字**背后**，随选中项平移，视觉上「包住」当前 Tab 文案。
 */
@Composable
fun NavigationGlassSlider(
    mainBackdrop: Backdrop?,
    selectedIndex: Int,
    tabCount: Int = 3,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val safeCount = tabCount.coerceAtLeast(1)
    val idx = selectedIndex.coerceIn(0, safeCount - 1)

    if (mainBackdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        NavigationGlassSliderFallback(
            selectedIndex = idx,
            tabCount = safeCount,
            modifier = modifier
        )
        return
    }

    val trackBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(mainBackdrop, trackBackdrop)
    val thumbShape = RoundedCornerShape(18.dp)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val segment = maxWidth / safeCount
        val horizontalInset = 5.dp
        val thumbW = (segment - horizontalInset * 2).coerceAtLeast(12.dp)
        val thumbH = (maxHeight - 10.dp).coerceAtLeast(30.dp)
        val thumbY = (maxHeight - thumbH) / 2
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
                .offset(x = thumbOffsetX, y = thumbY)
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
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val safeCount = tabCount.coerceAtLeast(1)
        val segment = maxWidth / safeCount
        val horizontalInset = 5.dp
        val thumbW = (segment - horizontalInset * 2).coerceAtLeast(12.dp)
        val thumbH = (maxHeight - 10.dp).coerceAtLeast(30.dp)
        val thumbY = (maxHeight - thumbH) / 2
        val targetOffsetX = segment * selectedIndex + horizontalInset

        val thumbOffsetX by animateDpAsState(
            targetValue = targetOffsetX,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "navGlassThumbFb"
        )

        Box(
            modifier = Modifier
                .offset(x = thumbOffsetX, y = thumbY)
                .size(thumbW, thumbH)
                .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
        )
    }
}
