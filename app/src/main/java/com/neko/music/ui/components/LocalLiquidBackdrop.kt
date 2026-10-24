package com.neko.music.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.kyant.backdrop.backdrops.LayerBackdrop

/**
 * 与 [com.kyant.backdrop.backdrops.layerBackdrop] 配套；为 null 时 [GlassSurface] 使用 Material 3 实色回退。
 *
 * **常规**：在 **`layerBackdrop(同一实例)` 子树内**不要对同一实例再 `drawBackdrop`（会 SIGSEGV），故默认 **null**。
 * **长列表 + 真液态**：应对「背景单独 [layerBackdrop]、列表与标题在兄弟层
 * `CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop)`」采样同一录屏纹理（见
 * [MyPlaylistsScreen]、[PlaylistDetailScreen]）。禁止多行共享同一 `exportedBackdrop` 并各自 `drawBackdrop`
 *（易 SIGSEGV）。不需要真液态时保持 null，走 [GlassSurface] Material 3 回退（如 [MineScreen] 列表行）。
 *
 * 单块浮层/底栏等仍可按 [Glass Bottom Sheet](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-sheet)、
 * [Glass Bottom Bar](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar) 在 **layerBackdrop 外**或
 * 单导出目标上接 [LocalLiquidLayerBackdrop]。[LocalNavHostRecordingBackdrop] 供需要读主录屏实例的页用。
 *
 * [opacity](https://kyant.gitbook.io/backdrop/api/backdrop-effects#opacity) 在 [GlassSurface] 的 `effects` 中。
 */
val LocalLiquidLayerBackdrop = compositionLocalOf<LayerBackdrop?> { null as LayerBackdrop? }

/** `MainActivity` 中 `NavHost` 的 `layerBackdrop` 实例（例如页顶 overlay 需与主录屏对齐时读取）。 */
val LocalNavHostRecordingBackdrop = compositionLocalOf<LayerBackdrop?> { null as LayerBackdrop? }

/** 当前是否真正使用 Kyant 硬件液态玻璃；关闭开关、缺少采样层或系统版本不足时统一走 Material 3。 */
@Composable
internal fun isLiquidGlassEffectActive(backdrop: LayerBackdrop?): Boolean =
    backdrop != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        LocalLiquidGlassHardwareEffectsEnabled.current

/** 硬件液态玻璃是否可用；不依赖具体采样层，供背景遮罩等全局装饰判断。 */
@Composable
internal fun isLiquidGlassHardwareAvailable(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        LocalLiquidGlassHardwareEffectsEnabled.current
