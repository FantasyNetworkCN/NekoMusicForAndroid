package com.neko.music.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neko.music.data.api.PlaylistInfo
import com.neko.music.data.model.Music

/**
 * 首页顶部「搜索 + 推荐歌单」与 [com.neko.music.ui.screens.HomeScreen] 内列表共享，
 * 以便在 **layerBackdrop 外** 的 [com.neko.music.ui.home.HomeLiquidHeroOverlay] 中绘制真液态玻璃。
 */
class HomeLiquidHeroState {
    var recommendedPlaylists: List<PlaylistInfo> by mutableStateOf(emptyList())
    var rankingMusic: List<Music> by mutableStateOf(emptyList())
    var latestMusic: List<Music> by mutableStateOf(emptyList())
    var playlistsLoading: Boolean by mutableStateOf(false)
    var loadError: Boolean by mutableStateOf(false)
}
