package com.neko.music.ui.components

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
 * 底部 [Glass Slider](https://kyant.gitbook.io/backdrop/tutorials/glass-slider)：
 * 独立 `trackBackdrop` 录轨道，拇指用 `rememberCombinedBackdrop(main, track)` 同时折射主界面与轨道。
 */
@Composable
fun NavigationGlassSlider(
    mainBackdrop: Backdrop?,
    selectedIndex: Int,
    tabCount: Int = 3,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val idx = selectedIndex.coerceIn(0, (tabCount - 1).coerceAtLeast(0))
    val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)

    if (mainBackdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        NavigationGlassSliderFallback(
            selectedIndex = idx,
            tabCount = tabCount,
            trackColor = trackColor,
            modifier = modifier
        )
        return
    }

    val trackBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(mainBackdrop, trackBackdrop)

    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(28.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val segment = maxWidth / tabCount
        val thumbW = 52.dp
        val thumbH = 28.dp
        val centerX = segment * (idx + 0.5f)
        val targetOffsetX = (centerX - thumbW / 2).coerceIn(0.dp, maxWidth - thumbW)

        val thumbOffsetX by animateDpAsState(
            targetValue = targetOffsetX,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "navGlassThumb"
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(6.dp)
                .layerBackdrop(trackBackdrop)
                .background(trackColor, CircleShape)
        )

        val thumbShape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffsetX)
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
                        drawRect(Color.White.copy(alpha = 0.22f))
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
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(28.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val segment = maxWidth / tabCount
        val thumbW = 52.dp
        val centerX = segment * (selectedIndex + 0.5f)
        val targetOffsetX = (centerX - thumbW / 2).coerceIn(0.dp, maxWidth - thumbW)
        val thumbOffsetX by animateDpAsState(
            targetValue = targetOffsetX,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "navGlassThumbFb"
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(6.dp)
                .background(trackColor.copy(alpha = 0.5f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffsetX)
                .size(thumbW, 28.dp)
                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
        )
    }
}
