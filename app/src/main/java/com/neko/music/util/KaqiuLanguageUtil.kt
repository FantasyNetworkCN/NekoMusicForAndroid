package com.neko.music.util

import android.content.Context

/**
 * 卡丘语语言工具
 * 用于在卡丘语模式下转换文本，添加可爱的"喵～"后缀
 */
object KaqiuLanguageUtil {
    
    private const val PREFS_NAME = "app_settings"
    private const val KEY_KAQIU_MODE_ENABLED = "kaqiu_mode_enabled"
    
    /**
     * 检查卡丘语模式是否启用
     */
    fun isKaqiuModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_KAQIU_MODE_ENABLED, false)
    }
    
    /**
     * 设置卡丘语模式
     */
    fun setKaqiuModeEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_KAQIU_MODE_ENABLED, enabled).apply()
    }
    
    /**
     * 卡丘语文本转换
     * 将普通文本转换为卡丘语风格的文本
     */
    fun toKaqiuText(text: String): String {
        if (text.isBlank()) return text
        
        // 已经包含"喵～"的文本不再添加
        if (text.contains("喵～") || text.endsWith("喵") || text.endsWith("～")) {
            return text
        }
        
        // 根据文本末尾的标点符号来添加喵～
        return when {
            text.endsWith("。") -> text.dropLast(1) + "喵～"
            text.endsWith("！") -> text.dropLast(1) + "喵～"
            text.endsWith("？") -> text.dropLast(1) + "喵～？"
            text.endsWith("：") -> text.dropLast(1) + "喵～："
            text.endsWith(";") -> text.dropLast(1) + "喵～;"
            text.endsWith(",") -> text.dropLast(1) + "喵～，"
            text.endsWith("、") -> text.dropLast(1) + "喵～、"
            text.endsWith("(") -> text.dropLast(1) + "喵～（"
            text.endsWith(")") -> text.dropLast(1) + "喵～）"
            text.endsWith("[") -> text.dropLast(1) + "喵～["
            text.endsWith("]") -> text.dropLast(1) + "喵～]"
            text.endsWith("\"") -> text.dropLast(1) + "喵～\""
            text.endsWith("'") -> text.dropLast(1) + "喵～'"
            text.endsWith("…") -> text.dropLast(1) + "喵～"
            text.endsWith(" ") -> text.dropLast(1) + "喵～"
            else -> text + "喵～"
        }
    }
    
    /**
     * 根据卡丘语模式获取文本
     * 如果卡丘语模式启用，则返回卡丘语版本的文本，否则返回原文本
     */
    fun getText(context: Context, text: String): String {
        return if (isKaqiuModeEnabled(context)) {
            toKaqiuText(text)
        } else {
            text
        }
    }
    
    /**
     * 根据卡丘语模式获取文本（用于带格式参数的字符串）
     */
    fun getText(context: Context, format: String, vararg args: Any): String {
        val formattedText = String.format(format, *args)
        return getText(context, formattedText)
    }
}
