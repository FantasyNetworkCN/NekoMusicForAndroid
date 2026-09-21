package com.neko.music.data.manager

import android.content.Context
import android.content.SharedPreferences

/**
 * 登录会话：**只有 Token 落盘**。
 *
 * 昵称 / 邮箱 / 会员状态等资料只放在进程内存里，由登录接口与
 * `GET /api/user/info` 在运行时填充，避免本地缓存过期
 * （在别处改了昵称，客户端还显示旧的）。
 */
class TokenManager(context: Context) {
    private val sharedPref: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    private val editor = sharedPref.edit()

    companion object {
        private const val KEY_TOKEN = "token"

        /** 旧版本曾把用户资料写进 SharedPreferences，这里仅用于清理 */
        private const val LEGACY_KEY_USER_ID = "user_id"
        private const val LEGACY_KEY_NICKNAME = "nickname"
        private const val LEGACY_KEY_USERNAME = "username"
        private const val LEGACY_KEY_EMAIL = "email"
        private const val LEGACY_KEY_IS_VIP = "is_vip"
        private const val LEGACY_KEY_VIP_EXPIRES_AT = "vip_expires_at"

        /** 进程内缓存的用户资料（不落盘） */
        @Volatile private var cachedUserId: Int = -1
        @Volatile private var cachedNickname: String? = null
        @Volatile private var cachedEmail: String? = null
        @Volatile private var cachedIsVip: Boolean = false
        @Volatile private var cachedVipExpiresAt: String? = null
    }

    init {
        purgeLegacyProfile()
    }

    /** 清掉旧版本残留在磁盘上的用户资料（昵称等不再落盘） */
    private fun purgeLegacyProfile() {
        val legacyKeys = listOf(
            LEGACY_KEY_USER_ID,
            LEGACY_KEY_NICKNAME,
            LEGACY_KEY_USERNAME,
            LEGACY_KEY_EMAIL,
            LEGACY_KEY_IS_VIP,
            LEGACY_KEY_VIP_EXPIRES_AT,
        )
        if (legacyKeys.any { sharedPref.contains(it) }) {
            val cleanup = sharedPref.edit()
            legacyKeys.forEach { cleanup.remove(it) }
            cleanup.apply()
        }
    }

    /**
     * 保存登录信息：Token 落盘，用户资料只放内存
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
        editor.apply()
        applyProfile(userId, nickname, email, isVip, vipExpiresAt)
    }

    /** 更新内存中的昵称（服务端修改成功后调用） */
    fun updateNickname(nickname: String) {
        cachedNickname = nickname
    }

    /**
     * 用服务端最新资料刷新内存中的昵称 / 邮箱 / VIP 状态（Token 不变）。
     *
     * 登录态是否有效由服务端判定（`GET /api/user/info` 的 401）；用户资料不写本地存储。
     */
    fun updateProfile(
        nickname: String,
        email: String,
        isVip: Boolean,
        vipExpiresAt: String?,
        userId: Int = -1,
    ) {
        applyProfile(
            userId = if (userId > 0) userId else cachedUserId,
            nickname = nickname,
            email = email,
            isVip = isVip,
            vipExpiresAt = vipExpiresAt,
        )
    }

    private fun applyProfile(
        userId: Int,
        nickname: String,
        email: String,
        isVip: Boolean,
        vipExpiresAt: String?,
    ) {
        cachedUserId = userId
        cachedNickname = nickname
        cachedEmail = email
        updateVipStatus(isVip, vipExpiresAt)
    }

    fun updateVipStatus(isVip: Boolean, vipExpiresAt: String?) {
        cachedIsVip = isVip
        cachedVipExpiresAt = vipExpiresAt?.takeIf { it.isNotBlank() }
    }

    fun isVip(): Boolean = cachedIsVip

    fun getVipExpiresAt(): String? = cachedVipExpiresAt

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
        return cachedUserId
    }

    /**
     * 获取昵称
     */
    fun getNickname(): String? {
        return cachedNickname
    }

    /**
     * 获取邮箱
     */
    fun getEmail(): String? {
        return cachedEmail
    }

    /**
     * 检查是否已登录
     */
    fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    /**
     * 清除 Token 与内存中的用户资料（登出）
     */
    fun clearToken() {
        editor.clear()
        editor.apply()
        cachedUserId = -1
        cachedNickname = null
        cachedEmail = null
        cachedIsVip = false
        cachedVipExpiresAt = null
    }
}
