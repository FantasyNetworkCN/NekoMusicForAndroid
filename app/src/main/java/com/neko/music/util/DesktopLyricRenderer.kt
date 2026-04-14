package com.neko.music.util

/**
 * 桌面歌词渲染器 - 使用C++实现的JNI接口
 * 提供高性能的歌词渲染功能
 */
object DesktopLyricRenderer {

    init {
        System.loadLibrary("DesktopLyricRenderer")
    }

    /**
     * 初始化渲染器
     */
    external fun nativeInitialize()

    /**
     * 解析LRC歌词
     * @param lrcText 歌词文本
     * @param musicId 音乐ID
     */
    external fun nativeParseLyrics(lrcText: String, musicId: Int)

    /**
     * 获取当前歌词
     * @param currentTime 当前播放时间（秒）
     * @return JSON格式的歌词数据，包含text、translation、hasLyric等字段
     */
    external fun nativeGetCurrentLyric(currentTime: Float): String

    /**
     * 获取前后歌词上下文（用于歌词滚动效果）
     * @param currentTime 当前播放时间（秒）
     * @param contextLines 前后各显示的行数
     * @return JSON数组，包含前后歌词的信息
     */
    external fun nativeGetLyricContext(currentTime: Float, contextLines: Int): String
    
    /**
     * 获取当前音乐ID
     * @return 当前音乐ID
     */
    external fun nativeGetCurrentMusicId(): Int
    
    /**
     * 获取歌词数量
     * @return 歌词数量
     */
    external fun nativeGetLyricCount(): Int
    
    /**
     * 清理资源
     */
    external fun nativeCleanup()
    
    /**
     * 初始化渲染器
     */
    fun initialize() {
        nativeInitialize()
    }
    
    /**
     * 解析歌词
     */
    fun parseLyrics(lrcText: String, musicId: Int) {
        nativeParseLyrics(lrcText, musicId)
    }
    
    /**
     * 获取当前歌词数据
     */
    fun getCurrentLyric(currentTime: Float): String {
        return nativeGetCurrentLyric(currentTime)
    }
    
    /**
     * 获取歌词上下文
     */
    fun getLyricContext(currentTime: Float, contextLines: Int = 3): String {
        return nativeGetLyricContext(currentTime, contextLines)
    }
    
    /**
     * 获取当前音乐ID
     */
    fun getCurrentMusicId(): Int {
        return nativeGetCurrentMusicId()
    }
    
    /**
     * 获取歌词数量
     */
    fun getLyricCount(): Int {
        return nativeGetLyricCount()
    }
    
    /**
     * 获取VR HUD数据（为VR场景优化）
     * @param currentTime 当前播放时间（秒）
     * @return JSON格式的歌词数据，包含text、translation、time、currentTime、hasLyric等字段
     */
    fun getVRHUDData(currentTime: Float): String {
        return nativeGetCurrentLyric(currentTime)
    }
    
    /**
     * 获取VR HUD上下文（为VR场景的滚动效果优化）
     * @param currentTime 当前播放时间（秒）
     * @param contextLines 前后各显示的行数
     * @return JSON数组，包含前后歌词的信息
     */
    fun getVRHUDContext(currentTime: Float, contextLines: Int = 3): String {
        return nativeGetLyricContext(currentTime, contextLines)
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        nativeCleanup()
    }
}