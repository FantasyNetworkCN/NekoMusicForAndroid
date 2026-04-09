package com.neko.music.config

import androidx.compose.ui.graphics.Color

/**
 * 应用配置类
 * 用于管理所有硬编码的配置值，便于统一管理和修改
 */
object AppConfig {
    
    // ==================== 触摸事件配置 ====================
    object TouchConfig {
        const val TOUCH_SLOP_DEFAULT = 8f // 触摸阈值（像素）
        const val CLICK_DURATION_MAX = 300L // 点击最大持续时间（毫秒）
        const val CLICK_DISTANCE_MAX = 10f // 点击最大移动距离（像素）
    }
    
    // ==================== 悬浮窗配置 ====================
    object FloatWindowConfig {
        const val DEFAULT_X = 0 // 默认水平位置
        const val DEFAULT_Y = 80 // 默认垂直位置
        const val UPDATE_INTERVAL = 500L // 更新间隔（毫秒）
    }
    
    // ==================== 桌面歌词配置 ====================
    object DesktopLyricConfig {
        const val DEFAULT_Y = 100 // 默认垂直位置
        const val UPDATE_INTERVAL = 500L // 更新间隔（毫秒）
    }
    
    // ==================== 排名配置 ====================
    object RankingConfig {
        val GOLD_COLOR = Color(0xFFFFD700) // 金色
        val SILVER_COLOR = Color(0xFFC0C0C0) // 银色
        val BRONZE_COLOR = Color(0xFFCD7F32) // 铜色
    }
    
    // ==================== 状态码配置 ====================
    object StatusCode {
        const val SUCCESS = 200
        const val NOT_FOUND = 404
        const val SERVER_ERROR = 500
        const val NETWORK_ERROR = 1001
        const val PARSE_ERROR = 1002
    }
    
    // ==================== API配置 ====================
    object ApiConfig {
        const val DEFAULT_TIMEOUT = 30000L // 默认超时时间（毫秒）
        const val RETRY_COUNT = 3 // 重试次数
        const val PAGE_SIZE_DEFAULT = 20 // 默认分页大小
    }
    
    // ==================== 缓存配置 ====================
    object CacheConfig {
        const val MAX_CACHE_SIZE = 100 * 1024 * 1024L // 最大缓存大小（100MB）
        const val CACHE_EXPIRE_TIME = 24 * 60 * 60 * 1000L // 缓存过期时间（24小时）
    }
    
    // ==================== 播放器配置 ====================
    object PlayerConfig {
        const val SEEK_INTERVAL = 1000L // 快进快退间隔（毫秒）
        const val FADE_DURATION = 300L // 淡入淡出时间（毫秒）
        const val MIN_BUFFER_MS = 15000L // 最小缓冲时间
        const val MAX_BUFFER_MS = 50000L // 最大缓冲时间
    }
    
    // ==================== 动画配置 ====================
    object AnimationConfig {
        const val DURATION_SHORT = 200L
        const val DURATION_MEDIUM = 300L
        const val DURATION_LONG = 500L
    }
    
    // ==================== UI配置 ====================
    object UIConfig {
        const val CORNER_RADIUS_SMALL = 4
        const val CORNER_RADIUS_MEDIUM = 8
        const val CORNER_RADIUS_LARGE = 16
        const val SPACING_SMALL = 4
        const val SPACING_MEDIUM = 8
        const val SPACING_LARGE = 16
    }
    
    // ==================== 日志配置 ====================
    object LogConfig {
        const val TAG_DEFAULT = "NekoMusic"
        const val TAG_PLAYER = "NekoMusic-Player"
        const val TAG_UI = "NekoMusic-UI"
        const val TAG_API = "NekoMusic-API"
        const val TAG_CACHE = "NekoMusic-Cache"
    }
    
    // ==================== 权限配置 ====================
    object PermissionConfig {
        const val REQUEST_CODE_FLOATING_WINDOW = 1001
        const val REQUEST_CODE_STORAGE = 1002
        const val REQUEST_CODE_AUDIO = 1003
    }
    
    // ==================== 偏好设置配置 ====================
    object PrefConfig {
        const val KEY_LANGUAGE = "language"
        const val KEY_THEME = "theme"
        const val KEY_DESKTOP_LYRIC = "desktop_lyric_enabled"
        const val KEY_FLOAT_WINDOW = "fuck_china_os_enabled"
        const val KEY_CACHE = "cache_enabled"
        const val KEY_FOCUS_LOCK = "focus_lock_enabled"
        
        const val DEFAULT_LANGUAGE = "system"
        const val DEFAULT_THEME = "auto"
    }
}