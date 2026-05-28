package com.neko.music.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neko.music.data.api.PlaylistInfo
import com.neko.music.data.model.Music

/**
 * 首页顶部「搜索 + 推荐歌单」数据：由 [com.neko.music.ui.screens.HomeScreen] 拉取并写入；
 * [HomeLiquidHeroOverlay] 在 MainActivity 中与 NavHost 同级叠放，采样主 [liquidBackdrop] 绘制真液态玻璃。
 */
class HomeLiquidHeroState {
    var recommendedPlaylists: List<PlaylistInfo> by mutableStateOf(emptyList())
    var dailyRecommendedMusic: List<Music> by mutableStateOf(emptyList())
    var rankingMusic: List<Music> by mutableStateOf(emptyList())
    var latestMusic: List<Music> by mutableStateOf(emptyList())
    var playlistsLoading: Boolean by mutableStateOf(false)
    var loadError: Boolean by mutableStateOf(false)
}
