package com.neko.music.ui.screens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 歌单详情页各块 [com.neko.music.ui.components.GlassSurface] 的液态参数。
 * 默认值与当前视觉一致；在此改一处即可对照调节。
 */
@Immutable
data class PlaylistDetailLiquidGlassParams(
    /** 批量底栏上圆角（单侧圆角矩形） */
    val batchDockTopCornerDp: Dp = 20.dp,
    /** 批量删除确认卡片圆角 */
    val batchDialogCornerDp: Dp = 22.dp,
    /** 批量删除确认背后遮罩透明度 */
    val batchDialogScrimAlpha: Float = 0.42f,
    /** 批量底栏 / 删除确认卡：亮面背景 alpha */
    val batchDockBackgroundAlphaDark: Float = 0.34f,
    val batchDockBackgroundAlphaLight: Float = 0.30f,
    val batchDockBorderAlphaDark: Float = 0.24f,
    val batchDockBorderAlphaLight: Float = 0.20f,
    val batchDockHighlightAlphaDark: Float = 0.10f,
    val batchDockHighlightAlphaLight: Float = 0.12f,
    /** 暗色下樱粉描边的不透明度乘子（底色为 [com.neko.music.ui.theme.SakuraPink]） */
    val batchDockDarkBorderSakuraAlpha: Float = 0.55f,
    val batchDockLiquidBlur: Dp = 12.dp,
    val batchDockLiquidLensHeight: Dp = 18.dp,
    val batchDockLiquidLensAmount: Dp = 30.dp,

    /** 分享底栏面板上圆角 */
    val sharePanelTopCornerDp: Dp = 22.dp,
    val sharePanelBackgroundAlphaDark: Float = 0.26f,
    val sharePanelBackgroundAlphaLight: Float = 0.30f,
    val sharePanelBorderAlphaDark: Float = 0.26f,
    val sharePanelBorderAlphaLight: Float = 0.20f,
    val sharePanelHighlightAlphaDark: Float = 0.10f,
    val sharePanelHighlightAlphaLight: Float = 0.12f,
    /** 暗色下玫红描边（底色为 [com.neko.music.ui.theme.RoseRed]） */
    val sharePanelDarkBorderRoseAlpha: Float = 0.55f,
    val sharePanelLiquidBlur: Dp = 11.dp,
    val sharePanelLiquidLensHeight: Dp = 16.dp,
    val sharePanelLiquidLensAmount: Dp = 28.dp,

    /** 歌单行玻璃圆角 */
    val musicRowCornerDp: Dp = 12.dp,
    val musicRowBackgroundAlphaDark: Float = 0.24f,
    val musicRowBackgroundAlphaLight: Float = 0.12f,
    val musicRowBorderAlphaDark: Float = 0.16f,
    val musicRowBorderAlphaLight: Float = 0.12f,
    val musicRowHighlightAlphaDark: Float = 0.09f,
    val musicRowHighlightAlphaLight: Float = 0.06f,
    val musicRowLiquidBlur: Dp = 8.dp,
    val musicRowLiquidLensHeight: Dp = 16.dp,
    val musicRowLiquidLensAmount: Dp = 26.dp,
)

object PlaylistDetailLiquidGlass {
    val defaultParams: PlaylistDetailLiquidGlassParams = PlaylistDetailLiquidGlassParams()
}
