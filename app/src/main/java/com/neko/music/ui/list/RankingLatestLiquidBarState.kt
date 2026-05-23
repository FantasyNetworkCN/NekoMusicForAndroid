package com.neko.music.ui.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neko.music.data.model.Music

/**
 * 与 [com.neko.music.ui.screens.RankingScreen] 共享；顶栏在 MainActivity 外挂时写入 [barInsetPx]。
 */
class RankingLiquidBarState {
    var musicList: List<Music> by mutableStateOf(emptyList())
    var loading: Boolean by mutableStateOf(true)
    var loadError: Boolean by mutableStateOf(false)
    /** 顶栏高度（px），由 MainActivity 外挂顶栏 [onBarHeightChanged] 写入，供列表 contentPadding。 */
    var barInsetPx: Int by mutableIntStateOf(0)
}

/**
 * 与 [com.neko.music.ui.screens.LatestScreen] 共享；顶栏在 MainActivity 外挂时写入 [barInsetPx]。
 */
class LatestLiquidBarState {
    var musicList: List<Music> by mutableStateOf(emptyList())
    var loading: Boolean by mutableStateOf(true)
    var loadError: Boolean by mutableStateOf(false)
    var barInsetPx: Int by mutableIntStateOf(0)
}
