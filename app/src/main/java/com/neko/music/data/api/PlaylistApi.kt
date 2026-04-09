package com.neko.music.data.api

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PlaylistListResponse(
    val success: Boolean,
    val message: String = "",
    val playlists: List<PlaylistInfo>? = null,
    val results: List<PlaylistInfo>? = null
)

@Serializable
data class PlaylistResponse(
    val success: Boolean,
    val message: String = "",
    val playlist: PlaylistInfo? = null
)

@Serializable
data class PlaylistInfo(
    val id: Int,
    val name: String,
    val description: String? = null,
    val coverPath: String? = null,
    val musicCount: Int,
    val createdAt: String,
    val updatedAt: String,
    val userId: Int? = null,
    val username: String? = null,
    val creatorAvatar: String? = null,
    val creator: CreatorInfo? = null,
    val firstMusicCover: String? = null
)

@Serializable
data class PlaylistMusic(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val coverPath: String?,
    val filePath: String,
    val fileFormat: String,
    val language: String,
    val position: Int,
    val addedAt: String
)

@Serializable
data class PlaylistMusicListResponse(
    val success: Boolean,
    val message: String,
    val playlistId: Int,
    val total: Int,
    val musicList: List<PlaylistMusic>? = null
)

@Serializable
data class CreatePlaylistRequest(
    val name: String,
    val description: String? = null
)

@Serializable
data class UpdatePlaylistRequest(
    val id: Int,
    val name: String,
    val description: String? = null
)

@Serializable
data class DeletePlaylistRequest(
    val id: Int
)

class PlaylistApi(private val token: String?, private val context: android.content.Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
    }

    private val baseUrl = "https://music.cnmsb.xin/api/user/playlist"

    suspend fun searchPlaylists(): PlaylistListResponse {
        return try {
            val response = client.post("https://music.cnmsb.xin/api/playlists/search") {
                contentType(ContentType.Application.Json)
                setBody("""{"query":"歌单"}""")
            }
            val responseText = response.body<String>()
            Log.d("PlaylistApi", "搜索歌单响应: $responseText")
            val responseBody = response.body<PlaylistListResponse>()
            // 将results字段转换为playlists字段
            return PlaylistListResponse(
                success = responseBody.success,
                message = responseBody.message,
                playlists = responseBody.results,
                results = null
            )
        } catch (e: Exception) {
            Log.e("PlaylistApi", "搜索歌单异常: ${e.message}", e)
            PlaylistListResponse(false, "网络错误: ${e.message}", null)
        }
    }

    suspend fun getMyPlaylists(): PlaylistListResponse {
        return try {
            val response = client.get("https://music.cnmsb.xin/api/user/playlists") {
                headers {
                    append("Authorization", token ?: "")
                }
            }
            val responseText = response.body<String>()
            Log.d("PlaylistApi", "获取歌单列表响应: $responseText")
            response.body()
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            PlaylistListResponse(false, "网络错误: ${e.message}", null)
        }
    }

    suspend fun createPlaylist(name: String): PlaylistResponse {
        return try {
            client.post("$baseUrl/create") {
                headers {
                    append("Authorization", token ?: "")
                    append("Content-Type", "application/json")
                }
                setBody(CreatePlaylistRequest(name, null))
            }.body()
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            PlaylistResponse(false, "网络错误: ${e.message}", null)
        }
    }

    suspend fun updatePlaylist(playlistId: Int, name: String, description: String? = null): PlaylistResponse {
        return try {
            client.post("$baseUrl/update") {
                headers {
                    append("Authorization", token ?: "")
                    append("Content-Type", "application/json")
                }
                setBody(UpdatePlaylistRequest(playlistId, name, description))
            }.body()
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            PlaylistResponse(false, "网络错误: ${e.message}", null)
        }
    }

    suspend fun deletePlaylist(playlistId: Int): PlaylistResponse {
        return try {
            client.post("$baseUrl/delete") {
                headers {
                    append("Authorization", token ?: "")
                    append("Content-Type", "application/json")
                }
                setBody(DeletePlaylistRequest(playlistId))
            }.body()
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            PlaylistResponse(false, "网络错误: ${e.message}", null)
        }
    }

    suspend fun getPlaylistMusic(playlistId: Int): PlaylistMusicListResponse {
        return try {
            client.get("$baseUrl/music/$playlistId").body()
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            PlaylistMusicListResponse(false, "网络错误: ${e.message}", playlistId, 0, null)
        }
    }

    suspend fun getPlaylistDetail(playlistId: Int): PlaylistResponse {
        return try {
            val response = client.get("https://music.cnmsb.xin/api/playlist/$playlistId")
            Log.d("PlaylistApi", "获取歌单详情响应: ${response.body<String>()}")
            response.body()
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            PlaylistResponse(false, "网络错误: ${e.message}", null)
        }
    }

    suspend fun addMusicToPlaylist(playlistId: Int, musicId: Int): PlaylistResponse {
        return try {
            val response = client.post("$baseUrl/music/add") {
                headers {
                    token?.let { append("Authorization", it) }
                    append("Content-Type", "application/json")
                }
                setBody(
                    """
                        {
                            "playlistId": $playlistId,
                            "musicId": $musicId
                        }
                        """.trimIndent()
                )
            }
            val status = response.status
            val bodyText = response.body<String>()
            Log.d("PlaylistApi", "添加到歌单响应: status=$status, body=$bodyText")
            response.body()
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            Log.e("PlaylistApi", "添加到歌单异常: ${e.message}", e)
            PlaylistResponse(false, "网络错误: ${e.message}", null)
        }
    }

    suspend fun removeMusicFromPlaylist(playlistId: Int, musicId: Int): PlaylistResponse {
        return try {
            val response = client.post("https://music.cnmsb.xin/api/user/playlist/music/remove") {
                headers {
                    token?.let { append("Authorization", it) }
                    append("Content-Type", "application/json")
                }
                setBody(
                    """
                        {
                            "playlistId": $playlistId,
                            "musicId": $musicId
                        }
                        """.trimIndent()
                )
            }
            val status = response.status
            val bodyText = response.body<String>()
            Log.d("PlaylistApi", "移除音乐响应: status=$status, body=$bodyText")
            response.body()
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            Log.e("PlaylistApi", "移除音乐异常: ${e.message}", e)
            PlaylistResponse(false, "网络错误: ${e.message}", null)
        }
    }
}