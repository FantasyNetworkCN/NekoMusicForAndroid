package com.neko.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** 与 MainActivity 主 Nav 一致：先铺底色再 `drawContent()`，供页内独立 [rememberLiquidPageBackdrop] 使用。 */
fun liquidBackdropLayerFillOnDraw(fill: Color): ContentDrawScope.() -> Unit = {
    drawRect(fill)
    drawContent()
}

@Composable
fun rememberLiquidPageBackdrop(fillColor: Color) =
    rememberLayerBackdrop(onDraw = remember(fillColor) { liquidBackdropLayerFillOnDraw(fillColor) })
