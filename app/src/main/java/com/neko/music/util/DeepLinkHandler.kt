package com.neko.music.util

import kotlinx.coroutines.channels.Channel

/**
 * Deep Link 路由事件处理器
 * 用于在 MainActivity 和 MainScreen 之间传递外部唤醒的导航指令
 */
object DeepLinkHandler {

    /**
     * 单消费者事件队列。
     *
     * Channel 会保留冷启动时尚未建立订阅的事件；之前用无 replay 的 SharedFlow 时，
     * onCreate 里解析出的 deep link 可能在任何收集器挂载前就丢失。
     */
    val deepLinkEvent = Channel<DeepLinkRoute>(Channel.BUFFERED)

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

        /** 打开隐私政策页 */
        data object PrivacyPolicy : DeepLinkRoute()

        /** 加入一起听房间；token 来自随机分享 URL。 */
        data class Together(val token: String) : DeepLinkRoute()
    }
}
