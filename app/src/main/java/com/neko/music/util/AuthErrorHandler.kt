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
     *
     * 注意：JSON 结构/字段异常（[MissingFieldException]、[JsonConvertException]）属于接口数据问题，
     * **不是**登录失效。历史上这里把它们当成认证错误处理，导致任意接口字段变动都会
     * `clearToken()` 把用户踢下线（例如打开创建者为 `username` 字段的歌单）。
     * 真正的登录失效由服务端返回 401 + `{"success":false,"message":"无效的认证令牌"}` 表达。
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun isAuthError(exception: Exception): Boolean {
        // 反序列化失败只说明接口返回的字段与服务端实现不一致，不能据此清除登录态。
        if (exception is MissingFieldException || exception is JsonConvertException) {
            return false
        }
        // 检查异常消息中是否包含认证相关的关键词
        val message = exception.message ?: ""
        return message.contains("Unauthorized", ignoreCase = true) ||
            message.contains("401", ignoreCase = true) ||
            message.contains("Invalid token", ignoreCase = true) ||
            message.contains("认证", ignoreCase = true) ||
            message.contains("令牌", ignoreCase = true)
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
