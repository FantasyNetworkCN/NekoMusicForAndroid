package com.neko.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.neko.music.ui.theme.DeepBlue
import com.neko.music.ui.theme.Lilac
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.isAppDarkTheme

/** 与 MainActivity 主 Nav 一致：先铺底色再 `drawContent()`，供页内独立 [rememberLiquidPageBackdrop] 使用。 */
fun liquidBackdropLayerFillOnDraw(fill: Color): ContentDrawScope.() -> Unit = {
    drawRect(fill)
    drawContent()
}

@Composable
fun rememberLiquidPageBackdrop(fillColor: Color) =
    rememberLayerBackdrop(onDraw = remember(fillColor) { liquidBackdropLayerFillOnDraw(fillColor) })

/** 歌单类子页**仅深色模式**底图遮罩：先压暗壁纸，再叠低饱和品牌色；亮色模式不绘制。 */
@Composable
fun PlaylistPageDarkTintOverlay(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (!isAppDarkTheme() || !enabled) return

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.18f),
                            Color.Black.copy(alpha = 0.30f),
                            Color.Black.copy(alpha = 0.36f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DeepBlue.copy(alpha = 0.50f),
                            RoseRed.copy(alpha = 0.20f),
                            Lilac.copy(alpha = 0.10f),
                            DeepBlue.copy(alpha = 0.66f)
                        )
                    )
                )
        )
    }
}
