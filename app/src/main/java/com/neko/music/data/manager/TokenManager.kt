package com.neko.music.data.manager

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val sharedPref: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    private val editor = sharedPref.edit()

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_EMAIL = "email"
    }

    /**
     * 保存 Token 和用户信息
     */
    fun saveToken(token: String, userId: Int, username: String, email: String) {
        editor.putString(KEY_TOKEN, token)
        editor.putInt(KEY_USER_ID, userId)
        editor.putString(KEY_USERNAME, username)
        editor.putString(KEY_EMAIL, email)
        editor.apply()
    }

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
     * 获取用户名
     */
    fun getUsername(): String? {
        return sharedPref.getString(KEY_USERNAME, null)
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