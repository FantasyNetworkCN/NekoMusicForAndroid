package com.neko.music.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 深浅两套 alpha（背景 / 描边 / 高光） */
@Immutable
data class LiquidGlassTint6(
    val backgroundDark: Float,
    val backgroundLight: Float,
    val borderDark: Float,
    val borderLight: Float,
    val highlightDark: Float,
    val highlightLight: Float,
) {
    fun background(isDark: Boolean) = if (isDark) backgroundDark else backgroundLight
    fun border(isDark: Boolean) = if (isDark) borderDark else borderLight
    fun highlight(isDark: Boolean) = if (isDark) highlightDark else highlightLight
}

@Immutable
data class LiquidGlassLiquid3(
    val blur: Dp,
    val lensHeight: Dp,
    val lensAmount: Dp,
)

@Immutable
data class LiquidGlassPanel(
    val tint: LiquidGlassTint6,
    val liquid: LiquidGlassLiquid3,
)

/** 不区分深浅的固定 alpha（如迷你播放器条） */
@Immutable
data class LiquidGlassFixedTint(
    val backgroundAlpha: Float,
    val borderAlpha: Float,
    val highlightAlpha: Float,
)

@Immutable
data class PlaylistDetailLiquidGlassParams(
    val batchDockTopCornerDp: Dp = 20.dp,
    val batchDialogCornerDp: Dp = 22.dp,
    val batchDialogScrimAlpha: Float = 0.42f,
    val batchDockBackgroundAlphaDark: Float = 0.34f,
    val batchDockBackgroundAlphaLight: Float = 0.30f,
    val batchDockBorderAlphaDark: Float = 0.24f,
    val batchDockBorderAlphaLight: Float = 0.20f,
    val batchDockHighlightAlphaDark: Float = 0.10f,
    val batchDockHighlightAlphaLight: Float = 0.12f,
    val batchDockDarkBorderSakuraAlpha: Float = 0.55f,
    val batchDockLiquidBlur: Dp = 12.dp,
    val batchDockLiquidLensHeight: Dp = 18.dp,
    val batchDockLiquidLensAmount: Dp = 30.dp,
    val sharePanelTopCornerDp: Dp = 22.dp,
    val sharePanelBackgroundAlphaDark: Float = 0.26f,
    val sharePanelBackgroundAlphaLight: Float = 0.30f,
    val sharePanelBorderAlphaDark: Float = 0.26f,
    val sharePanelBorderAlphaLight: Float = 0.20f,
    val sharePanelHighlightAlphaDark: Float = 0.10f,
    val sharePanelHighlightAlphaLight: Float = 0.12f,
    val sharePanelDarkBorderRoseAlpha: Float = 0.55f,
    val sharePanelLiquidBlur: Dp = 11.dp,
    val sharePanelLiquidLensHeight: Dp = 16.dp,
    val sharePanelLiquidLensAmount: Dp = 28.dp,
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

@Immutable
data class PlaylistQueueRowLiquidGlass(
    val playingBackgroundDark: Float = 0.46f,
    val playingBackgroundLight: Float = 0.36f,
    val idleBackgroundDark: Float = 0.22f,
    val idleBackgroundLight: Float = 0.20f,
    val borderPlayingDark: Float = 0.24f,
    val borderPlayingLight: Float = 0.22f,
    val borderIdleDark: Float = 0.12f,
    val borderIdleLight: Float = 0.16f,
    val highlightPlayingDark: Float = 0.11f,
    val highlightPlayingLight: Float = 0.12f,
    val highlightIdleDark: Float = 0.06f,
    val highlightIdleLight: Float = 0.08f,
    val liquid: LiquidGlassLiquid3 = LiquidGlassLiquid3(8.dp, 16.dp, 26.dp),
    /** 暗色下描边使用 [com.neko.music.ui.theme.SakuraPink] 时的 alpha */
    val darkBorderSakuraAlpha: Float = 0.55f,
) {
    fun backgroundAlpha(isPlaying: Boolean, isDark: Boolean): Float = when {
        isPlaying && isDark -> playingBackgroundDark
        isPlaying && !isDark -> playingBackgroundLight
        !isPlaying && isDark -> idleBackgroundDark
        else -> idleBackgroundLight
    }

    fun borderAlpha(isPlaying: Boolean, isDark: Boolean): Float = when {
        isPlaying && isDark -> borderPlayingDark
        isPlaying && !isDark -> borderPlayingLight
        !isPlaying && isDark -> borderIdleDark
        else -> borderIdleLight
    }

    fun highlightAlpha(isPlaying: Boolean, isDark: Boolean): Float = when {
        isPlaying && isDark -> highlightPlayingDark
        isPlaying && !isDark -> highlightPlayingLight
        !isPlaying && isDark -> highlightIdleDark
        else -> highlightIdleLight
    }
}

@Immutable
data class RankingItemLiquidGlass(
    val backgroundTop3Dark: Float = 0.32f,
    val backgroundTop3Light: Float = 0.12f,
    val backgroundRestDark: Float = 0.22f,
    val backgroundRestLight: Float = 0.08f,
    val borderTop3Dark: Float = 0.2f,
    val borderTop3Light: Float = 0.14f,
    val borderRestDark: Float = 0.14f,
    val borderRestLight: Float = 0.1f,
    val highlightTop3Dark: Float = 0.1f,
    val highlightTop3Light: Float = 0.06f,
    val highlightRestDark: Float = 0.06f,
    val highlightRestLight: Float = 0.04f,
    val liquid: LiquidGlassLiquid3 = LiquidGlassLiquid3(4.dp, 16.dp, 32.dp),
) {
    fun backgroundAlpha(isTop3: Boolean, isDark: Boolean): Float = when {
        isTop3 && isDark -> backgroundTop3Dark
        isTop3 && !isDark -> backgroundTop3Light
        !isTop3 && isDark -> backgroundRestDark
        else -> backgroundRestLight
    }

    fun borderAlpha(isTop3: Boolean, isDark: Boolean): Float = when {
        isTop3 && isDark -> borderTop3Dark
        isTop3 && !isDark -> borderTop3Light
        !isTop3 && isDark -> borderRestDark
        else -> borderRestLight
    }

    fun highlightAlpha(isTop3: Boolean, isDark: Boolean): Float = when {
        isTop3 && isDark -> highlightTop3Dark
        isTop3 && !isDark -> highlightTop3Light
        !isTop3 && isDark -> highlightRestDark
        else -> highlightRestLight
    }
}

/**
 * 全应用液态玻璃可调默认值（与当前各页写死数值一致）。
 * 修改此处即可全局对照调节；个别页面仍可在 Composable 内组合 [LiquidGlassTint6] / [LiquidGlassPanel]。
 */
object LiquidGlassDefaults {

    val liquidSoft: LiquidGlassLiquid3 = LiquidGlassLiquid3(4.dp, 16.dp, 32.dp)
    val liquidMedium: LiquidGlassLiquid3 = LiquidGlassLiquid3(8.dp, 16.dp, 26.dp)
    val liquidPlayerChrome: LiquidGlassLiquid3 = LiquidGlassLiquid3(14.dp, 18.dp, 30.dp)
    val liquidPlayerChromeBottom: LiquidGlassLiquid3 = LiquidGlassLiquid3(14.dp, 18.dp, 32.dp)
    val liquidSharePanel: LiquidGlassLiquid3 = LiquidGlassLiquid3(11.dp, 16.dp, 28.dp)
    val liquidPlaylistModal: LiquidGlassLiquid3 = LiquidGlassLiquid3(14.dp, 20.dp, 34.dp)
    val liquidAppUpdate: LiquidGlassLiquid3 = LiquidGlassLiquid3(6.dp, 16.dp, 32.dp)
    val liquidShareSection: LiquidGlassLiquid3 = LiquidGlassLiquid3(9.dp, 12.dp, 22.dp)

    val playlistDetail: PlaylistDetailLiquidGlassParams = PlaylistDetailLiquidGlassParams()

    val screenListCard: LiquidGlassTint6 = LiquidGlassTint6(
        backgroundDark = 0.28f,
        backgroundLight = 0.08f,
        borderDark = 0.14f,
        borderLight = 0.08f,
        highlightDark = 0.08f,
        highlightLight = 0.04f,
    )

    val searchHistoryRow: LiquidGlassTint6 = LiquidGlassTint6(
        backgroundDark = 0.22f,
        backgroundLight = 0.06f,
        borderDark = 0.12f,
        borderLight = 0.06f,
        highlightDark = 0.06f,
        highlightLight = 0.03f,
    )

    val playlistModalBottomSheet: LiquidGlassPanel = LiquidGlassPanel(
        tint = LiquidGlassTint6(
            backgroundDark = 0.42f,
            backgroundLight = 0.36f,
            borderDark = 0.26f,
            borderLight = 0.22f,
            highlightDark = 0.12f,
            highlightLight = 0.14f,
        ),
        liquid = liquidPlaylistModal,
    )

    val playlistQueueRow: PlaylistQueueRowLiquidGlass = PlaylistQueueRowLiquidGlass()

    val rankingRetryButton: LiquidGlassPanel = LiquidGlassPanel(
        tint = LiquidGlassTint6(
            backgroundDark = 0.28f,
            backgroundLight = 0.12f,
            borderDark = 0.18f,
            borderLight = 0.14f,
            highlightDark = 0.08f,
            highlightLight = 0.08f,
        ),
        liquid = liquidSoft,
    )

    val rankingListItem: RankingItemLiquidGlass = RankingItemLiquidGlass()

    val latestListItem: LiquidGlassPanel = LiquidGlassPanel(
        tint = LiquidGlassTint6(
            backgroundDark = 0.22f,
            backgroundLight = 0.12f,
            borderDark = 0.14f,
            borderLight = 0.12f,
            highlightDark = 0.08f,
            highlightLight = 0.06f,
        ),
        liquid = liquidSoft,
    )

    val rankingLatestTopBar: LiquidGlassPanel = LiquidGlassPanel(
        tint = LiquidGlassTint6(
            backgroundDark = 0.35f,
            backgroundLight = 0.30f,
            borderDark = 0.18f,
            borderLight = 0.20f,
            highlightDark = 0.08f,
            highlightLight = 0.10f,
        ),
        liquid = liquidSoft,
    )

    val searchTopSearchField: LiquidGlassPanel = LiquidGlassPanel(
        tint = LiquidGlassTint6(
            backgroundDark = 0.35f,
            backgroundLight = 0.30f,
            borderDark = 0.18f,
            borderLight = 0.20f,
            highlightDark = 0.08f,
            highlightLight = 0.10f,
        ),
        liquid = liquidSoft,
    )

    val searchTopTypeRow: LiquidGlassPanel = LiquidGlassPanel(
        tint = LiquidGlassTint6(
            backgroundDark = 0.32f,
            backgroundLight = 0.28f,
            borderDark = 0.15f,
            borderLight = 0.20f,
            highlightDark = 0.08f,
            highlightLight = 0.11f,
        ),
        liquid = liquidSoft,
    )

    val homeHeroSearchBarDarkBorderSakuraAlpha: Float = 0.55f
    val homeHeroShortcutCardDarkBorderSakuraAlpha: Float = 0.48f

    val homeHeroSearchBar: LiquidGlassPanel = LiquidGlassPanel(
        tint = LiquidGlassTint6(
            backgroundDark = 0.35f,
            backgroundLight = 0.30f,
            borderDark = 0.20f,
            borderLight = 0.20f,
            highlightDark = 0.09f,
            highlightLight = 0.11f,
        ),
        liquid = liquidSoft,
    )

    val homeHeroShortcutCard: LiquidGlassPanel = LiquidGlassPanel(
        tint = LiquidGlassTint6(
            backgroundDark = 0.34f,
            backgroundLight = 0.30f,
            borderDark = 0.18f,
            borderLight = 0.20f,
            highlightDark = 0.09f,
            highlightLight = 0.12f,
        ),
        liquid = liquidSoft,
    )

    val playerTopBar: LiquidGlassPanel = LiquidGlassPanel(
        tint = LiquidGlassTint6(
            backgroundDark = 0.22f,
            backgroundLight = 0.28f,
            borderDark = 0.28f,
            borderLight = 0.22f,
            highlightDark = 0.12f,
            highlightLight = 0.14f,
        ),
        liquid = liquidPlayerChrome,
    )

    val playerBottomChrome: LiquidGlassPanel = LiquidGlassPanel(
        tint = LiquidGlassTint6(
            backgroundDark = 0.24f,
            backgroundLight = 0.32f,
            borderDark = 0.28f,
            borderLight = 0.22f,
            highlightDark = 0.11f,
            highlightLight = 0.13f,
        ),
        liquid = liquidPlayerChromeBottom,
    )

    val playerShareBottomSheet: LiquidGlassPanel = LiquidGlassPanel(
        tint = LiquidGlassTint6(
            backgroundDark = 0.26f,
            backgroundLight = 0.30f,
            borderDark = 0.26f,
            borderLight = 0.20f,
            highlightDark = 0.10f,
            highlightLight = 0.12f,
        ),
        liquid = liquidSharePanel,
    )

    val bottomNavigationDock: LiquidGlassPanel = LiquidGlassPanel(
        tint = LiquidGlassTint6(
            backgroundDark = 0.32f,
            backgroundLight = 0.26f,
            borderDark = 0.15f,
            borderLight = 0.20f,
            highlightDark = 0.08f,
            highlightLight = 0.12f,
        ),
        liquid = liquidSoft,
    )

    val miniPlayerBar: LiquidGlassFixedTint = LiquidGlassFixedTint(
        backgroundAlpha = 0.32f,
        borderAlpha = 0.15f,
        highlightAlpha = 0.08f,
    )

    val myPlaylistsListRow: LiquidGlassPanel = LiquidGlassPanel(
        tint = LiquidGlassTint6(
            backgroundDark = 0.24f,
            backgroundLight = 0.12f,
            borderDark = 0.16f,
            borderLight = 0.12f,
            highlightDark = 0.09f,
            highlightLight = 0.06f,
        ),
        liquid = liquidMedium,
    )

    val myPlaylistsDialogPrimaryButton: LiquidGlassTint6 = LiquidGlassTint6(
        backgroundDark = 0.32f,
        backgroundLight = 0.12f,
        borderDark = 0.18f,
        borderLight = 0.12f,
        highlightDark = 0.08f,
        highlightLight = 0.08f,
    )

    val appUpdateDialogDarkBorderSakuraAlpha: Float = 0.48f
    val appUpdateSuccessDarkBorderGreenAlpha: Float = 0.35f
    val appUpdateErrorDarkBorderRedAlpha: Float = 0.4f

    val appUpdateDialog: LiquidGlassPanel = LiquidGlassPanel(
        tint = LiquidGlassTint6(
            backgroundDark = 0.34f,
            backgroundLight = 0.22f,
            borderDark = 0.18f,
            borderLight = 0.12f,
            highlightDark = 0.09f,
            highlightLight = 0.07f,
        ),
        liquid = liquidAppUpdate,
    )

    /** 创建/编辑歌单弹窗（面板与 [appUpdateDialog] 同档液态参数） */
    val myPlaylistsDialog: LiquidGlassPanel get() = appUpdateDialog

    val myPlaylistsDialogInput: LiquidGlassPanel get() = searchTopSearchField

    val shareSheetSectionDarkBorderSakuraAlpha: Float = 0.42f

    val shareSheetSection: LiquidGlassPanel = LiquidGlassPanel(
        tint = LiquidGlassTint6(
            backgroundDark = 0.17f,
            backgroundLight = 0.22f,
            borderDark = 0.22f,
            borderLight = 0.17f,
            highlightDark = 0.07f,
            highlightLight = 0.09f,
        ),
        liquid = liquidShareSection,
    )

    /** 会员中心列表底部留白（底栏/迷你播放器） */
    val vipCenterListBottomInsetDp: Dp = 160.dp

    val vipCenterHero: LiquidGlassPanel = LiquidGlassPanel(
        tint = LiquidGlassTint6(
            backgroundDark = 0.34f,
            backgroundLight = 0.14f,
            borderDark = 0.20f,
            borderLight = 0.12f,
            highlightDark = 0.10f,
            highlightLight = 0.06f,
        ),
        liquid = liquidMedium,
    )

    val vipCenterPricingCard: LiquidGlassPanel = LiquidGlassPanel(
        tint = LiquidGlassTint6(
            backgroundDark = 0.28f,
            backgroundLight = 0.10f,
            borderDark = 0.16f,
            borderLight = 0.10f,
            highlightDark = 0.09f,
            highlightLight = 0.05f,
        ),
        liquid = liquidMedium,
    )
}

/** @see LiquidGlassDefaults.playlistDetail */
object PlaylistDetailLiquidGlass {
    val defaultParams: PlaylistDetailLiquidGlassParams
        get() = LiquidGlassDefaults.playlistDetail
}
