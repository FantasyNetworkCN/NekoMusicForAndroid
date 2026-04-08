package com.neko.music.util

import com.neko.music.BuildConfig

/**
 * URL配置管理类
 * 统一管理应用中所有的URL，避免硬编码
 */
object UrlConfig {
    
    /**
     * 获取API基础URL
     */
    fun getBaseUrl(): String = BuildConfig.BASE_URL
    
    /**
     * 获取音乐封面URL
     * @param musicId 音乐ID
     */
    fun getMusicCoverUrl(musicId: Int): String = "${BuildConfig.BASE_URL}/api/music/cover/$musicId"
    
    /**
     * 获取音乐文件URL
     * @param musicId 音乐ID
     */
    fun getMusicFileUrl(musicId: Int): String = "${BuildConfig.BASE_URL}/api/music/file/$musicId"
    
    /**
     * 获取用户头像URL
     * @param userId 用户ID
     * @param updateTime 更新时间（可选，用于刷新缓存）
     */
    fun getUserAvatarUrl(userId: Int, updateTime: Long? = null): String {
        return if (updateTime != null) {
            "${BuildConfig.BASE_URL}/api/user/avatar/$userId?t=$updateTime"
        } else {
            "${BuildConfig.BASE_URL}/api/user/avatar/$userId"
        }
    }
    
    /**
     * 获取默认用户头像URL
     */
    fun getDefaultAvatarUrl(): String = "${BuildConfig.BASE_URL}/api/user/avatar/default"
    
    /**
     * 获取音乐详情URL
     * @param musicId 音乐ID
     */
    fun getMusicDetailUrl(musicId: Int): String = "${BuildConfig.BASE_URL}/detail/$musicId"
    
    /**
     * 获取歌单详情URL
     * @param playlistId 歌单ID
     */
    fun getPlaylistDetailUrl(playlistId: Int): String = "${BuildConfig.BASE_URL}/playlist/$playlistId"
    
    /**
     * 获取版本检查URL
     */
    fun getVersionCheckUrl(): String = "${BuildConfig.BASE_URL}/version.json"
    
    /**
     * 构建完整URL（用于处理以/开头的相对路径）
     * @param path 路径
     */
    fun buildFullUrl(path: String): String {
        return if (path.startsWith("/")) {
            "${BuildConfig.BASE_URL}$path"
        } else {
            path
        }
    }
    
    /**
     * 构建音乐封面URL（支持多种格式）
     * @param musicId 音乐ID
     * @param coverFilePath 封面文件路径（可选）
     */
    fun buildMusicCoverUrl(musicId: Int, coverFilePath: String? = null): String {
        return if (!coverFilePath.isNullOrEmpty() && coverFilePath.startsWith("/")) {
            "${BuildConfig.BASE_URL}$coverFilePath"
        } else {
            getMusicCoverUrl(musicId)
        }
    }
}