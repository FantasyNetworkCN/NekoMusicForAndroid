package com.neko.music.ui.components

import androidx.compose.runtime.compositionLocalOf
import com.kyant.backdrop.backdrops.LayerBackdrop

/**
 * 与 [com.kyant.backdrop.backdrops.layerBackdrop] 配套；为 null 时 [GlassSurface] 使用 CPU 模拟玻璃。
 *
 * **只应在 `layerBackdrop` 子树外**（如悬浮底栏）`provides` 非 null；`NavHost` 内保持默认 null，避免与
 * [Glass Bottom Bar](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar) 冲突导致 SIGSEGV。
 *
 * API：https://kyant.gitbook.io/backdrop/api/backdrops · 多层玻璃：[Glass Bottom Sheet](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-sheet)
 */
val LocalLiquidLayerBackdrop = compositionLocalOf<LayerBackdrop?> { null as LayerBackdrop? }
