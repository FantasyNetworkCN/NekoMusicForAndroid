package com.neko.music.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.util.Log
import com.neko.music.data.model.Music

class FavoriteApi(private val context: android.content.Context) {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    private val baseUrl = "https://music.cnmsb.xin"

    /**
     * 获取收藏列表
     */
    suspend fun getFavorites(token: String): FavoriteListResponse {
        return try {
            val response = client.get("$baseUrl/api/user/favorites") {
                header("Authorization", token)
            }
            response.body()
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            Log.e("FavoriteApi", "获取收藏列表失败", e)
            FavoriteListResponse(success = false, favorites = emptyList())
        }
    }

    /**
     * 添加收藏
     */
    suspend fun addFavorite(token: String, musicId: Int): FavoriteResponse {
        return try {
            val response = client.post("$baseUrl/api/user/favorites") {
                header("Authorization", token)
                contentType(ContentType.Application.Json)
                setBody(AddFavoriteRequest(musicId = musicId))
            }
            response.body()
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            Log.e("FavoriteApi", "添加收藏失败", e)
            FavoriteResponse(success = false, message = "网络错误: ${e.message}")
        }
    }

    /**
     * 取消收藏
     */
    suspend fun removeFavorite(token: String, musicId: Int): FavoriteResponse {
        return try {
            val response = client.delete("$baseUrl/api/user/favorites/$musicId") {
                header("Authorization", token)
            }
            response.body()
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            Log.e("FavoriteApi", "取消收藏失败", e)
            FavoriteResponse(success = false, message = "网络错误: ${e.message}")
        }
    }

    /**
     * 获取收藏歌单列表
     */
    suspend fun getFavoritePlaylists(token: String): FavoritePlaylistListResponse {
        return try {
            val response = client.get("$baseUrl/api/user/favorite-playlists") {
                header("Authorization", token)
            }
            response.body()
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            Log.e("FavoriteApi", "获取收藏歌单列表失败", e)
            FavoritePlaylistListResponse(success = false, playlists = emptyList())
        }
    }

    /**
     * 收藏歌单
     */
    suspend fun addFavoritePlaylist(token: String, playlistId: Int): FavoriteResponse {
        return try {
            val response = client.post("$baseUrl/api/user/favorite-playlists") {
                header("Authorization", token)
                contentType(ContentType.Application.Json)
                setBody(AddFavoritePlaylistRequest(playlistId = playlistId))
            }
            response.body()
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            Log.e("FavoriteApi", "收藏歌单失败", e)
            FavoriteResponse(success = false, message = "网络错误: ${e.message}")
        }
    }

    /**
     * 取消收藏歌单
     */
    suspend fun removeFavoritePlaylist(token: String, playlistId: Int): FavoriteResponse {
        return try {
            val response = client.delete("$baseUrl/api/user/favorite-playlists/$playlistId") {
                header("Authorization", token)
            }
            response.body()
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            Log.e("FavoriteApi", "取消收藏歌单失败", e)
            FavoriteResponse(success = false, message = "网络错误: ${e.message}")
        }
    }

    /**
     * 检查歌单是否已收藏
     */
    suspend fun isPlaylistFavorited(token: String, playlistId: Int): Boolean {
        return try {
            val response: FavoritePlaylistListResponse = getFavoritePlaylists(token)
            if (response.success) {
                response.playlists.any { it.id == playlistId }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("FavoriteApi", "检查收藏状态失败", e)
            false
        }
    }
}

// 数据模型
@Serializable
data class AddFavoriteRequest(
    val musicId: Int
)

@Serializable
data class AddFavoritePlaylistRequest(
    val playlistId: Int
)

@Serializable
data class FavoriteResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class FavoriteListResponse(
    val success: Boolean,
    val favorites: List<FavoriteMusic>
)

@Serializable
data class FavoriteMusic(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val filename: String,
    val cover: String = ""
)

@Serializable
data class FavoritePlaylistInfo(
    val id: Int,
    val name: String,
    val description: String? = null,
    val musicCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val favoriteTime: Long,
    val creator: CreatorInfo? = null
)

@Serializable
data class CreatorInfo(
    val id: Int,
    val username: String
)

@Serializable
data class FavoritePlaylistListResponse(
    val success: Boolean,
    val playlists: List<FavoritePlaylistInfo>
)