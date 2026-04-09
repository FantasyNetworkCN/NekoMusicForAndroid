package com.neko.music.util

import android.content.Context
import android.widget.Toast
import com.neko.music.R
import com.neko.music.data.manager.TokenManager
import io.ktor.serialization.JsonConvertException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException

/**
 * 认证错误处理工具
 * 用于检测和处理认证相关的错误
 */
object AuthErrorHandler {

    /**
     * 检测异常是否为认证错误
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun isAuthError(exception: Exception): Boolean {
        return when (exception) {
            is JsonConvertException -> {
                // 检查是否是缺少必需字段的反序列化错误（通常发生在认证失效时API返回了错误页面）
                val message = exception.message ?: ""
                message.contains("MissingFieldException", ignoreCase = true) ||
                message.contains("Field.*is required", ignoreCase = true) ||
                exception.cause is MissingFieldException
            }
            is MissingFieldException -> true
            else -> {
                // 检查异常消息中是否包含认证相关的关键词
                val message = exception.message ?: ""
                message.contains("Unauthorized", ignoreCase = true) ||
                message.contains("401", ignoreCase = true) ||
                message.contains("Invalid token", ignoreCase = true) ||
                message.contains("认证", ignoreCase = true) ||
                message.contains("令牌", ignoreCase = true)
            }
        }
    }

    /**
     * 处理认证错误
     * 清除 Token 并显示 Toast 提示
     */
    fun handleAuthError(context: Context, exception: Exception) {
        if (isAuthError(exception)) {
            // 清除 Token
            val tokenManager = TokenManager(context)
            tokenManager.clearToken()

            // 显示 Toast 提示
            Toast.makeText(
                context,
                context.getString(R.string.auth_expired),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * 通用的 API 错误处理
     * 自动检测认证错误并进行处理
     * @return 如果是认证错误返回 true，否则返回 false
     */
    fun handleApiError(context: Context, exception: Exception): Boolean {
        if (isAuthError(exception)) {
            handleAuthError(context, exception)
            return true
        }
        return false
    }
}