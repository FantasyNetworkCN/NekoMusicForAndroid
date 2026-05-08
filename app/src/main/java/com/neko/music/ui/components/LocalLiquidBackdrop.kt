package com.neko.music.ui.components

import androidx.compose.runtime.compositionLocalOf
import com.kyant.backdrop.backdrops.LayerBackdrop

/**
 * 与 [com.kyant.backdrop.backdrops.layerBackdrop] 配套；为 null 时 [GlassSurface] 使用 CPU 模拟玻璃。
 *
 * API 说明：https://kyant.gitbook.io/backdrop/api/backdrops
 * 勿在已挂 `layerBackdrop(同一 LayerBackdrop)` 的子树内再对该 backdrop `drawBackdrop`，否则可能死循环崩溃（见 Glass Bottom Sheet 教程）。
 */
val LocalLiquidLayerBackdrop = compositionLocalOf<LayerBackdrop?> { null as LayerBackdrop? }
