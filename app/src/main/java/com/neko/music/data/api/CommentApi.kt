package com.neko.music.data.api

import android.util.Log
import com.neko.music.util.UrlConfig
import com.neko.music.util.preferHttp2AlpnOverHttp1
import com.neko.music.util.protocolLogSuffixOrEmpty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CommentUser(
    val id: Int = 0,
    val nickname: String = ""
)

@Serializable
data class CommentReply(
    val id: Int = 0,
    val content: String = "",
    val createdAt: String = "",
    val ipRegion: String = "",
    val canDelete: Boolean = false,
    val user: CommentUser? = null,
    val replyToUser: CommentUser? = null
)

@Serializable
data class CommentItem(
    val id: Int = 0,
    val musicId: Int = 0,
    val content: String = "",
    val createdAt: String = "",
    val ipRegion: String = "",
    val canDelete: Boolean = false,
    val user: CommentUser? = null,
    val replyCount: Int = 0,
    val replies: List<CommentReply> = emptyList()
)

@Serializable
data class CommentPageData(
    val musicId: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
    val total: Int = 0,
    val totalComments: Int = 0,
    val totalPages: Int = 0,
    val hasMore: Boolean = false,
    val comments: List<CommentItem> = emptyList()
)

@Serializable
data class CommentListResponse(
    val success: Boolean = false,
    val message: String = "",
    val data: CommentPageData? = null
)

@Serializable
data class PostCommentRequest(
    val musicId: Int,
    val content: String,
    val parentId: Int? = null
)

@Serializable
data class PostedComment(
    val id: Int = 0,
    val musicId: Int = 0,
    val parentId: Int? = null,
    val content: String = "",
    val ipRegion: String = "",
    val createdAt: String = "",
    val isReply: Boolean = false
)

@Serializable
data class PostCommentResponse(
    val success: Boolean = false,
    val message: String = "",
    val data: PostedComment? = null
)

@Serializable
data class DeleteCommentData(
    val id: Int = 0,
    val repliesDeleted: Int = 0
)

@Serializable
data class DeleteCommentResponse(
    val success: Boolean = false,
    val message: String = "",
    val data: DeleteCommentData? = null
)

/**
 * 歌曲评论接口：`/api/comments`，评论 / 回复 / 删除共用一个端点。
 * 契约见 `Neko歌姬计划文档/README.md`「歌曲评论 API」。
 */
class CommentApi(private val context: android.content.Context) {
    private val client = HttpClient(OkHttp) {
        engine {
            config { preferHttp2AlpnOverHttp1() }
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    private val baseUrl = UrlConfig.getBaseUrl()

    private fun HttpRequestBuilder.auth(token: String?) {
        if (!token.isNullOrBlank()) {
            header("Authorization", token)
        }
    }

    /**
     * 获取评论列表（未登录也可以查看，登录后会返回 canDelete）。
     */
    suspend fun getComments(
        musicId: Int,
        page: Int = 1,
        pageSize: Int = 20,
        token: String? = null
    ): CommentListResponse {
        return try {
            client.get("$baseUrl/api/comments") {
                parameter("musicId", musicId)
                parameter("page", page)
                parameter("pageSize", pageSize)
                auth(token)
            }.body()
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            Log.e("CommentApi", "获取评论失败${e.protocolLogSuffixOrEmpty()}", e)
            CommentListResponse(success = false, message = "网络错误: ${e.message}")
        }
    }

    /**
     * 发表评论；[parentId] 不为空时表示回复（楼层或楼层内回复的 id 均可）。
     */
    suspend fun postComment(
        token: String,
        musicId: Int,
        content: String,
        parentId: Int? = null
    ): PostCommentResponse {
        return try {
            client.post("$baseUrl/api/comments") {
                auth(token)
                contentType(ContentType.Application.Json)
                setBody(PostCommentRequest(musicId = musicId, content = content, parentId = parentId))
            }.body()
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            Log.e("CommentApi", "发表评论失败${e.protocolLogSuffixOrEmpty()}", e)
            PostCommentResponse(success = false, message = "网络错误: ${e.message}")
        }
    }

    /**
     * 删除评论（物理删除，删楼层会连带删除其下全部回复）。
     */
    suspend fun deleteComment(token: String, id: Int): DeleteCommentResponse {
        return try {
            client.delete("$baseUrl/api/comments") {
                auth(token)
                parameter("id", id)
            }.body()
        } catch (e: Exception) {
            com.neko.music.util.AuthErrorHandler.handleApiError(context, e)
            Log.e("CommentApi", "删除评论失败${e.protocolLogSuffixOrEmpty()}", e)
            DeleteCommentResponse(success = false, message = "网络错误: ${e.message}")
        }
    }
}
