package com.neko.music.data.api

import com.neko.music.util.AuthErrorHandler
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归测试：服务端「创建者昵称」字段名不统一。
 *
 * - 自建歌单 `/api/playlist/1` → `"creator":{"id":4,"nickname":"哈基蜂"}`
 * - 外部导入歌单 `/api/playlist/407` → `"creator":{"id":48,"username":"cxy"}`
 *
 * 旧模型硬性要求 `nickname`，解析 407 这类歌单会抛 [MissingFieldException]，
 * 又被 [AuthErrorHandler] 误判成登录失效并 `clearToken()`，表现为「打开该歌单掉登录」。
 */
class PlaylistCreatorDecodingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun detailBody(playlistId: Int, userId: Int, creator: String): String =
        """{"success":true,"message":"获取歌单详情成功","playlist":{""" +
            """"id":$playlistId,"userId":$userId,"name":"测试歌单","musicCount":1,""" +
            """"createdAt":"2026-09-18 21:35:33","updatedAt":"2026-09-18 21:35:41",""" +
            """"creator":$creator}}"""

    @Test
    fun `creator 返回 nickname 时可正常解析`() {
        val response = json.decodeFromString<PlaylistResponse>(
            detailBody(1, 4, """{"id":4,"nickname":"哈基蜂"}""")
        )
        assertEquals("哈基蜂", response.playlist?.creator?.displayName)
    }

    @Test
    fun `creator 仅返回 username 时可正常解析（歌单 407）`() {
        val response = json.decodeFromString<PlaylistResponse>(
            detailBody(407, 48, """{"id":48,"username":"cxy"}""")
        )
        assertEquals("cxy", response.playlist?.creator?.displayName)
        assertTrue(response.success)
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun `接口字段缺失抛出的反序列化异常不应被判定为登录失效`() {
        val thrown = try {
            json.decodeFromString<PlaylistMusicListResponse>(
                """{"success":true,"message":"ok","playlistId":407}"""
            )
            null
        } catch (e: Exception) {
            e
        }

        assertNotNull("缺少 total 字段时应抛反序列化异常", thrown)
        assertTrue("应为 MissingFieldException", thrown is MissingFieldException)
        assertFalse("字段缺失不能当成登录失效，否则会误清 token", AuthErrorHandler.isAuthError(thrown!!))
    }
}
