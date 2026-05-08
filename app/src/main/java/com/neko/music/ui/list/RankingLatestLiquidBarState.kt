package com.neko.music.ui.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neko.music.data.model.Music

/**
 * 与 [com.neko.music.ui.screens.RankingScreen] 共享，供 layerBackdrop 外的顶栏真液态采样。
 */
class RankingLiquidBarState {
    var musicList: List<Music> by mutableStateOf(emptyList())
    var loading: Boolean by mutableStateOf(true)
    var loadError: Boolean by mutableStateOf(false)
}

/**
 * 与 [com.neko.music.ui.screens.LatestScreen] 共享。
 */
class LatestLiquidBarState {
    var musicList: List<Music> by mutableStateOf(emptyList())
    var loading: Boolean by mutableStateOf(true)
    var loadError: Boolean by mutableStateOf(false)
}
