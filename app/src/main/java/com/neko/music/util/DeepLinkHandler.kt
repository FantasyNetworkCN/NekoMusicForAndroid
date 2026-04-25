package com.neko.music.util

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Deep Link 路由事件处理器
 * 用于在 MainActivity 和 MainScreen 之间传递外部唤醒的导航指令
 */
object DeepLinkHandler {

    val deepLinkEvent = MutableSharedFlow<DeepLinkRoute>(extraBufferCapacity = 1)

    sealed class DeepLinkRoute {
        /**
         * 打开音乐播放页（仅使用ID，标题和歌手由应用内部获取）
         * @param id 音乐ID
         */
        data class Player(val id: Int) : DeepLinkRoute()

        /**
         * 打开歌单详情页
         * @param id 歌单ID
         */
        data class Playlist(val id: Int) : DeepLinkRoute()
    }
}
