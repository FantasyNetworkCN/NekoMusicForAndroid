package com.neko.music.data.manager

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val sharedPref: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    private val editor = sharedPref.edit()

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_NICKNAME = "nickname"
        /** 旧版本昵称存在 "username" 键下，仅供读取时做一次性兼容 */
        private const val LEGACY_KEY_NICKNAME = "username"
        private const val KEY_EMAIL = "email"
        private const val KEY_IS_VIP = "is_vip"
        private const val KEY_VIP_EXPIRES_AT = "vip_expires_at"
    }

    /**
     * 保存 Token 和用户信息
     */
    fun saveToken(
        token: String,
        userId: Int,
        nickname: String,
        email: String,
        isVip: Boolean = false,
        vipExpiresAt: String? = null
    ) {
        editor.putString(KEY_TOKEN, token)
        editor.putInt(KEY_USER_ID, userId)
        editor.putString(KEY_NICKNAME, nickname)
        editor.putString(KEY_EMAIL, email)
        editor.putBoolean(KEY_IS_VIP, isVip)
        if (vipExpiresAt.isNullOrBlank()) {
            editor.remove(KEY_VIP_EXPIRES_AT)
        } else {
            editor.putString(KEY_VIP_EXPIRES_AT, vipExpiresAt)
        }
        editor.apply()
    }

    /**
     * 更新本地缓存的昵称（服务端修改成功后调用）
     */
    fun updateNickname(nickname: String) {
        editor.putString(KEY_NICKNAME, nickname)
        editor.apply()
    }

    fun updateVipStatus(isVip: Boolean, vipExpiresAt: String?) {
        editor.putBoolean(KEY_IS_VIP, isVip)
        if (vipExpiresAt.isNullOrBlank()) {
            editor.remove(KEY_VIP_EXPIRES_AT)
        } else {
            editor.putString(KEY_VIP_EXPIRES_AT, vipExpiresAt)
        }
        editor.apply()
    }

    fun isVip(): Boolean = sharedPref.getBoolean(KEY_IS_VIP, false)

    fun getVipExpiresAt(): String? = sharedPref.getString(KEY_VIP_EXPIRES_AT, null)

    /**
     * 获取 Token
     */
    fun getToken(): String? {
        return sharedPref.getString(KEY_TOKEN, null)
    }

    /**
     * 获取用户ID
     */
    fun getUserId(): Int {
        return sharedPref.getInt(KEY_USER_ID, -1)
    }

    /**
     * 获取昵称
     */
    fun getNickname(): String? {
        return sharedPref.getString(KEY_NICKNAME, null)
            ?: sharedPref.getString(LEGACY_KEY_NICKNAME, null)
    }

    /**
     * 获取邮箱
     */
    fun getEmail(): String? {
        return sharedPref.getString(KEY_EMAIL, null)
    }

    /**
     * 检查是否已登录
     */
    fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    /**
     * 清除 Token 和用户信息（登出）
     */
    fun clearToken() {
        editor.clear()
        editor.apply()
    }
}