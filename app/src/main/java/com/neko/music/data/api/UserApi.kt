package com.neko.music.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import android.util.Log
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonArray

class UserApi(private val token: String? = null) {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
                coerceInputValues = true
            })
        }
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 600_000  // 10分钟
            connectTimeoutMillis = 600_000  // 10分钟
            socketTimeoutMillis = 600_000  // 10分钟
        }
    }

    private val baseUrl = "https://music.cnmsb.xin"

    /**
     * 用户登录
     */
    suspend fun login(username: String, password: String): LoginResponse {
        return try {
            val response = client.post("$baseUrl/api/user/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username = username, password = password))
            }
            response.body()
        } catch (e: Exception) {
            Log.e("UserApi", "登录失败", e)
            LoginResponse(success = false, message = "网络错误: ${e.message}", data = null)
        }
    }

    /**
     * 用户注册
     */
    suspend fun register(username: String, password: String, email: String, verificationCode: String): RegisterResponse {
        return try {
            val response = client.post("$baseUrl/api/user/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(
                    username = username,
                    password = password,
                    email = email,
                    verificationCode = verificationCode
                ))
            }
            response.body()
        } catch (e: Exception) {
            Log.e("UserApi", "注册失败", e)
            RegisterResponse(success = false, message = "网络错误: ${e.message}", data = null)
        }
    }

    /**
     * 发送验证码
     */
    suspend fun sendVerificationCode(email: String, username: String): VerificationResponse {
        return try {
            val response = client.post("$baseUrl/api/user/send-verification") {
                contentType(ContentType.Application.Json)
                setBody(VerificationRequest(email = email, username = username))
            }
            response.body()
        } catch (e: Exception) {
            Log.e("UserApi", "发送验证码失败", e)
            VerificationResponse(success = false, message = "网络错误: ${e.message}", data = null)
        }
    }

    /**
     * 发送重置密码验证码
     */
    suspend fun sendResetCode(email: String): VerificationResponse {
        return try {
            val response = client.post("$baseUrl/api/user/send-reset-code") {
                contentType(ContentType.Application.Json)
                setBody(SendResetCodeRequest(email = email))
            }
            response.body()
        } catch (e: Exception) {
            Log.e("UserApi", "发送重置密码验证码失败", e)
            VerificationResponse(success = false, message = "网络错误: ${e.message}", data = null)
        }
    }

    /**
     * 重置密码
     */
    suspend fun resetPassword(email: String, code: String, newPassword: String): ResetPasswordResponse {
        return try {
            val response = client.post("$baseUrl/api/user/reset-password") {
                contentType(ContentType.Application.Json)
                setBody(ResetPasswordRequest(
                    email = email,
                    code = code,
                    newPassword = newPassword
                ))
            }
            response.body()
        } catch (e: Exception) {
            Log.e("UserApi", "重置密码失败", e)
            ResetPasswordResponse(success = false, message = "网络错误: ${e.message}")
        }
    }

    /**
     * 修改密码
     */
    suspend fun updatePassword(oldPassword: String, newPassword: String): UpdatePasswordResponse {
        return try {
            val response = client.post("$baseUrl/api/user/password/change") {
                contentType(ContentType.Application.Json)
                headers {
                    token?.let { append("Authorization", "Bearer $it") }
                }
                setBody(UpdatePasswordRequest(
                    oldPassword = oldPassword,
                    newPassword = newPassword
                ))
            }
            
            val responseText = response.body<String>()
            Log.d("UserApi", "修改密码响应: $responseText")
            
            val jsonResponse = Json.parseToJsonElement(responseText) as JsonObject
            
            // 检查是否成功
            val success = jsonResponse["success"]?.toString()?.toBoolean() ?: false
            val message = if (success) {
                // 成功响应: {"success": true, "message": "密码修改成功"}
                jsonResponse["message"]?.toString()?.removeSurrounding("\"") ?: "密码修改成功"
            } else {
                // 失败响应: {"error": "原密码错误"}
                jsonResponse["error"]?.toString()?.removeSurrounding("\"") ?: "修改密码失败"
            }
            
            UpdatePasswordResponse(success = success, message = message)
        } catch (e: Exception) {
            Log.e("UserApi", "修改密码失败", e)
            UpdatePasswordResponse(success = false, message = "网络错误: ${e.message}")
        }
    }
    
    /**
     * 更换头像
     */
    suspend fun updateAvatar(imageData: ByteArray): UpdateAvatarResponse {
        return try {
            // 使用 POST 方法上传头像到 /api/user/avatar/upload
            val response = client.post("$baseUrl/api/user/avatar/upload") {
                headers {
                    token?.let { append("Authorization", "Bearer $it") }
                }
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("avatar", imageData, Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=avatar.jpg")
                                append(HttpHeaders.ContentType, "image/jpeg")
                            })
                        }
                    )
                )
            }

            val responseText = response.body<String>()
            Log.d("UserApi", "更换头像响应状态: ${response.status}")
            Log.d("UserApi", "更换头像响应内容: $responseText")

            // 如果响应为空，根据状态码判断成功
            if (responseText.isBlank()) {
                val success = response.status.value in 200..299
                val message = if (success) "头像更新成功" else "头像更新失败"
                return UpdateAvatarResponse(success = success, message = message)
            }

            // 如果响应不为空，尝试解析 JSON
            try {
                val jsonResponse = Json.parseToJsonElement(responseText) as JsonObject
                val success = jsonResponse["success"]?.toString()?.toBoolean() ?: false
                val message = jsonResponse["message"]?.toString()?.removeSurrounding("\"") ?: ""
                val avatarPath = jsonResponse["avatarPath"]?.toString()?.removeSurrounding("\"")

                UpdateAvatarResponse(success = success, message = message, avatarUrl = avatarPath)
            } catch (jsonException: Exception) {
                // JSON 解析失败，根据状态码判断
                val success = response.status.value in 200..299
                val message = if (success) "头像更新成功" else "头像更新失败: $responseText"
                UpdateAvatarResponse(success = success, message = message)
            }
        } catch (e: Exception) {
            Log.e("UserApi", "更换头像失败", e)
            UpdateAvatarResponse(success = false, message = "网络错误: ${e.message}")
        }
    }

    /**
     * 获取用户上传审核通过的音乐
     */
    suspend fun getUploadedMusic(): UploadedMusicResponse {
        return try {
            val response = client.get("$baseUrl/api/user/uploaded-music") {
                token?.let {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $it")
                    }
                }
            }
            
            // 先尝试自动反序列化
            try {
                val result = response.body<UploadedMusicResponse>()
                Log.d("UserApi", "获取上传音乐成功: ${result.success}, 数量: ${result.total}")
                result
            } catch (e: Exception) {
                // 如果自动反序列化失败，尝试手动解析
                Log.w("UserApi", "自动反序列化失败，尝试手动解析: ${e.message}")
                val responseBody = response.bodyAsText()
                Log.d("UserApi", "响应内容: $responseBody")
                
                // 返回默认值，避免应用崩溃
                UploadedMusicResponse(
                    success = false,
                    message = "数据解析错误: ${e.message}",
                    userId = -1,
                    musicList = emptyList(),
                    total = 0
                )
            }
        } catch (e: Exception) {
            Log.e("UserApi", "获取上传音乐失败", e)
            UploadedMusicResponse(
                success = false,
                message = "网络错误: ${e.message}",
                userId = -1,
                musicList = emptyList(),
                total = 0
            )
        }
    }

    /**
     * 上传音乐
     * 根据 123.md API 文档实现
     */
    suspend fun uploadMusic(
        audioFile: ByteArray,
        audioFileName: String,
        title: String,
        artist: String,
        album: String,
        language: String,
        tags: String,
        duration: Int,  // 音乐时长，单位秒（必填）
        uploadUserId: Int = 0,  // 上传用户ID（通常传0）
        lyricsFile: ByteArray? = null,
        coverImage: ByteArray? = null
    ): UploadMusicResponse {
        return try {
            Log.d("UserApi", "开始上传音乐: $title - $artist, 时长: $duration 秒")
            
            val response = client.post("$baseUrl/api/user/upload") {
                headers {
                    token?.let { append("Authorization", "Bearer $it") }
                }
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            // 音乐文件
                            append("musicFile", audioFile, Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=$audioFileName")
                                append(HttpHeaders.ContentType, "audio/mpeg")
                            })
                            
                            // 歌词文件（可选）
                            if (lyricsFile != null) {
                                append("lyricsFile", lyricsFile, Headers.build {
                                    append(HttpHeaders.ContentDisposition, "filename=lyrics.lrc")
                                    append(HttpHeaders.ContentType, "text/plain; charset=UTF-8")
                                })
                            }
                            
                            // 封面图片（可选）
                            if (coverImage != null) {
                                append("coverFile", coverImage, Headers.build {
                                    append(HttpHeaders.ContentDisposition, "filename=cover.jpg")
                                    append(HttpHeaders.ContentType, "image/jpeg")
                                })
                            }
                            
                            // 音乐信息
                            append("title", title)
                            append("artist", artist)
                            append("album", album)
                            append("language", language)
                            append("tags", tags)
                            append("duration", duration.toString())
                            append("uploadUserId", uploadUserId.toString())
                        }
                    )
                )
            }

            val responseText = response.body<String>()
            Log.d("UserApi", "上传音乐响应状态: ${response.status}")
            Log.d("UserApi", "上传音乐响应内容: $responseText")

            // 如果响应为空，根据状态码判断成功
            if (responseText.isBlank()) {
                val success = response.status.value in 200..299
                val message = if (success) "上传成功" else "上传失败"
                return UploadMusicResponse(success = success, message = message)
            }

            // 如果响应不为空，尝试解析 JSON
            try {
                val jsonResponse = Json.parseToJsonElement(responseText) as JsonObject
                val success = jsonResponse["success"]?.toString()?.toBoolean() ?: false
                val message = jsonResponse["message"]?.toString()?.removeSurrounding("\"") ?: ""
                
                // 根据 123.md 文档，响应格式为：
                // {"success": true, "message": "上传成功，等待审核", "data": {"id": 1, "status": "pending", "createdAt": "2026-02-12T16:30:00"}}
                val data = jsonResponse["data"] as? JsonObject
                val musicId = data?.get("id")?.toString()?.toIntOrNull()

                UploadMusicResponse(success = success, message = message, musicId = musicId)
            } catch (jsonException: Exception) {
                // JSON 解析失败，根据状态码判断
                val success = response.status.value in 200..299
                val message = if (success) "上传成功" else "上传失败: $responseText"
                UploadMusicResponse(success = success, message = message)
            }
        } catch (e: Exception) {
            Log.e("UserApi", "上传音乐失败", e)
            UploadMusicResponse(success = false, message = "网络错误: ${e.message}")
        }
    }
}

// 数据模型
@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String,
    val verificationCode: String
)

@Serializable
data class VerificationRequest(
    val email: String,
    val username: String
)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val data: LoginData?
)

@Serializable
data class LoginData(
    val user: UserData,
    val token: String
)

@Serializable
data class UserData(
    val id: Int,
    val username: String,
    val email: String,
    val createdAt: String
)

@Serializable
data class RegisterResponse(
    val success: Boolean,
    val message: String,
    val data: Map<String, String>?
)

@Serializable
data class VerificationResponse(
    val success: Boolean,
    val message: String,
    val data: String?
)

@Serializable
data class UpdatePasswordRequest(
    val oldPassword: String,
    val newPassword: String
)

@Serializable
data class UpdatePasswordResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class UpdateAvatarResponse(
    val success: Boolean,
    val message: String,
    val avatarUrl: String? = null
)

@Serializable
data class UploadedMusic(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val language: String,
    val tags: String,
    val fileFormat: String,
    val filePath: String,
    val coverPath: String? = null,
    val createdAt: String
)

@Serializable
data class UploadedMusicResponse(
    val success: Boolean,
    val message: String,
    val userId: Int,
    val musicList: List<UploadedMusic>,
    val total: Int
)

@Serializable
data class UploadMusicResponse(
    val success: Boolean,
    val message: String,
    val musicId: Int? = null
)

@Serializable
data class SendResetCodeRequest(
    val email: String
)

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val newPassword: String
)

@Serializable
data class ResetPasswordResponse(
    val success: Boolean,
    val message: String
)