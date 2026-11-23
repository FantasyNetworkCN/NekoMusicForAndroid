package com.neko.music.util

import android.content.Context
import java.net.URI

/**
 * URL配置管理类
 * 统一管理应用中所有的URL，避免硬编码
 */
object UrlConfig {

    const val DEFAULT_BASE_URL = "https://music.nekocore.cn"

    private const val PREFS_NAME = "app_settings"
    private const val KEY_SOURCE_BASE_URL = "music_source_base_url"

    @Volatile
    private var baseUrl: String = DEFAULT_BASE_URL

    /** 启动时在 Application 初始化，之后所有 API 都读取同一份运行时基础地址。 */
    fun initialize(context: Context) {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SOURCE_BASE_URL, null)
        baseUrl = normalizeBaseUrl(stored) ?: DEFAULT_BASE_URL
    }

    /**
     * 获取API基础URL
     */
    fun getBaseUrl(): String = baseUrl

    fun isUsingDefaultBaseUrl(): Boolean = baseUrl == DEFAULT_BASE_URL

    fun getSourceManifestUrl(candidateBaseUrl: String = baseUrl): String =
        "${candidateBaseUrl.trimEnd('/')}/api/music/source"

    /**
     * 仅接受 HTTP(S) 基础地址；远程明文 HTTP 一律拒绝，本机回环地址可供本地开发使用。
     * 查询串与片段不属于 API 基础地址，路径部分可作为反向代理前缀保留。
     */
    fun normalizeBaseUrl(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val candidate = if ("://" in text) text else "https://$text"
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        if (scheme != "https" && !(scheme == "http" && isLoopbackHost(host))) return null
        val path = uri.rawPath.orEmpty().trimEnd('/')
        if (path.isNotEmpty() && !path.startsWith('/')) return null
        return runCatching {
            URI(scheme, null, host, uri.port, path.ifEmpty { null }, null, null)
                .toASCIIString()
                .trimEnd('/')
        }.getOrNull()
    }

    /** 只有服务端清单校验通过后才会调用。 */
    fun setVerifiedBaseUrl(context: Context, raw: String): String? {
        val normalized = normalizeBaseUrl(raw) ?: return null
        baseUrl = normalized
        val editor = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
        if (normalized == DEFAULT_BASE_URL)
            editor.remove(KEY_SOURCE_BASE_URL)
        else
            editor.putString(KEY_SOURCE_BASE_URL, normalized)
        editor.apply()
        return normalized
    }

    fun resetToDefault(context: Context) {
        baseUrl = DEFAULT_BASE_URL
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SOURCE_BASE_URL)
            .apply()
    }

    private fun isLoopbackHost(host: String): Boolean =
        host == "localhost" || host == "::1" || host == "[::1]" || host.startsWith("127.")

    /**
     * 获取音乐封面URL
     * @param musicId 音乐ID
     */
    fun getMusicCoverUrl(musicId: Int): String = "$baseUrl/api/music/cover/$musicId"

    /**
     * 获取音乐文件URL
     * @param musicId 音乐ID
     */
    fun getMusicFileUrl(musicId: Int, quality: String? = null): String {
        val normalized = quality?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return if (normalized == null) {
            "$baseUrl/api/music/file/$musicId"
        } else {
            "$baseUrl/api/music/file/$musicId?quality=$normalized"
        }
    }

    /** VIP 人声 / 伴奏分离 SSE：分片完成后立即推送双轨媒体地址。 */
    fun getServerStemStreamUrl(musicId: Int): String =
        "$baseUrl/api/music/file/$musicId/stream?separate=1"

    fun isLocalUri(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        if (value.startsWith("content://") || value.startsWith("file://")) return true
        if (!value.startsWith("/")) return false
        // /api/、/uploads/、/media/ 都是服务端地址，不是本地文件
        return !value.startsWith("/api/") &&
            !value.startsWith("/uploads/") &&
            !value.startsWith("/media/")
    }

    /**
     * 获取用户头像URL
     * @param userId 用户ID
     */
    fun getUserAvatarUrl(userId: Int): String = "$baseUrl/api/user/avatar/$userId"

    /**
     * 获取API基础URL
     */
    fun getApiUrl(path: String): String = "$baseUrl$path"

    /** 听歌识曲 WebSocket 端点；HTTPS 站点对应 WSS。 */
    fun getMusicRecognitionWsUrl(): String {
        val wsBase = when {
            baseUrl.startsWith("https://") -> "wss://" + baseUrl.removePrefix("https://")
            baseUrl.startsWith("http://") -> "ws://" + baseUrl.removePrefix("http://")
            else -> baseUrl
        }
        return "$wsBase/api/music/recognize/ws"
    }

    /** 一起听实时连接；ticket 只能用于一次建连。 */
    fun getTogetherWsUrl(ticket: String): String {
        val wsBase = when {
            baseUrl.startsWith("https://") -> "wss://" + baseUrl.removePrefix("https://")
            baseUrl.startsWith("http://") -> "ws://" + baseUrl.removePrefix("http://")
            else -> baseUrl
        }
        return "$wsBase/api/listen/ws?ticket=${java.net.URLEncoder.encode(ticket, "UTF-8")}"
    }

    fun getTogetherRoomUrl(token: String): String = "$baseUrl/together/$token"

    /**
     * 获取艺术家搜索URL
     */
    fun getArtistSearchUrl(): String = "$baseUrl/api/artists/search"

    /**
     * 获取播放列表搜索URL
     */
    fun getPlaylistSearchUrl(): String = "$baseUrl/api/playlists/search"

    /**
     * 获取默认用户头像URL
     */
    fun getDefaultAvatarUrl(): String = "$baseUrl/api/user/avatar/default"

    /**
     * 获取音乐详情URL
     * @param musicId 音乐ID
     */
    fun getMusicDetailUrl(musicId: Int): String = "$baseUrl/detail/$musicId"

    /**
     * 获取歌单详情URL
     * @param playlistId 歌单ID
     */
    fun getPlaylistDetailUrl(playlistId: Int): String = "$baseUrl/playlist/$playlistId"

    /**
     * 获取版本检查URL
     */
    fun getVersionCheckUrl(): String = "$baseUrl/version"

    /**
     * 构建完整URL（用于处理以/开头的相对路径）
     * @param path 路径
     */
    fun buildFullUrl(path: String): String {
        if (isLocalUri(path)) return path
        return if (path.startsWith("/")) {
            "$baseUrl$path"
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
        if (isLocalUri(coverFilePath)) return coverFilePath.orEmpty()
        return if (!coverFilePath.isNullOrEmpty() && coverFilePath.startsWith("/")) {
            "$baseUrl$coverFilePath"
        } else {
            getMusicCoverUrl(musicId)
        }
    }
}
