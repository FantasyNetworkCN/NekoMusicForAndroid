package com.neko.music.data.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GET /api/user/info` 契约测试（见 Neko歌姬计划文档 README 第 20 节）。
 *
 * 客户端只持久化 Token，启动时用本接口刷新昵称/VIP；Token 失效时服务端返回
 * `401 + {"success":false,"message":"请先登录"}`，必须以 HTTP 状态码判定登录态，
 * 不能靠解析异常推断（否则接口字段一变动就会误踢用户下线）。
 */
class UserInfoResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun `成功响应可解析出用户资料与 VIP 状态`() {
        val body = """
            {
              "success": true,
              "message": "获取用户信息成功",
              "data": {
                "user": {
                  "id": 3,
                  "nickname": "喵",
                  "email": "neko@example.com",
                  "createdAt": "2026-01-29 12:00:00",
                  "isVip": false,
                  "vipExpiresAt": null
                }
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<UserInfoResponse>(body)

        assertTrue(response.success)
        val user = response.data?.user
        assertEquals(3, user?.id)
        assertEquals("喵", user?.nickname)
        assertEquals("neko@example.com", user?.email)
        assertEquals(false, user?.isVip)
        assertNull(user?.vipExpiresAt)
    }

    @Test
    fun `401 响应体能正常解析（无 data，视为未登录）`() {
        val response = json.decodeFromString<UserInfoResponse>(
            """{"success":false,"message":"请先登录"}"""
        )

        assertEquals(false, response.success)
        assertEquals("请先登录", response.message)
        assertNull(response.data)
    }
}
