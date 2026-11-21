package com.neko.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 大屏断点沿用 Material 的 compact / medium / expanded 划分。 */
internal enum class NekoWindowWidthClass {
    Compact,
    Medium,
    Expanded,
}

/** 页面正文的最大宽度，避免平板上出现过长的文字行和过大的卡片。 */
internal val NekoReadableContentWidth: Dp = 920.dp

/** 首页、播放器等媒体页可以使用更宽的内容区域。 */
internal val NekoMediaContentWidth: Dp = 1180.dp

@Composable
internal fun currentNekoWindowWidthClass(): NekoWindowWidthClass =
    when {
        LocalConfiguration.current.screenWidthDp >= 840 -> NekoWindowWidthClass.Expanded
        LocalConfiguration.current.screenWidthDp >= 600 -> NekoWindowWidthClass.Medium
        else -> NekoWindowWidthClass.Compact
    }

@Composable
internal fun nekoPageHorizontalPadding(): Dp =
    when (currentNekoWindowWidthClass()) {
        NekoWindowWidthClass.Compact -> 16.dp
        NekoWindowWidthClass.Medium -> 24.dp
        NekoWindowWidthClass.Expanded -> 32.dp
    }

/** 将页面正文限制在可读宽度内，并保持在大屏中央。 */
@Composable
internal fun NekoAdaptivePageContent(
    modifier: Modifier = Modifier,
    maxWidth: Dp = NekoReadableContentWidth,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxSize(),
            content = content,
        )
    }
}

/** 根据卡片实际宽度选择封面比例，避免平板横屏时所有卡片都被拉成长条。 */
internal fun adaptiveCoverAspectRatio(cardWidth: Dp): Float =
    when {
        cardWidth < 240.dp -> 1f
        cardWidth < 360.dp -> 1.15f
        else -> 1.5f
    }

/** 首页推荐网格按可用宽度增加列数，并限制单列过宽。 */
internal fun adaptiveGridColumns(windowWidthDp: Int): Int =
    when {
        windowWidthDp >= 1200 -> 4
        windowWidthDp >= 840 -> 3
        else -> 2
    }

/** 首页卡片按实际列数估算宽度，避免横屏时封面被拉伸成过宽的长条。 */
@Composable
internal fun adaptiveHomeCardAspectRatio(): Float {
    val windowWidthDp = LocalConfiguration.current.screenWidthDp
    val columns = adaptiveGridColumns(windowWidthDp)
    val estimatedCardWidth = (windowWidthDp.dp - 96.dp) / columns.toFloat()
    return adaptiveCoverAspectRatio(estimatedCardWidth)
}
