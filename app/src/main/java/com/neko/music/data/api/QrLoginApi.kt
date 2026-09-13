package com.neko.music.data.api

import android.util.Log
import com.neko.music.util.UrlConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 扫码登录接口返回：{ success, message, data: { status } } */
@Serializable
data class QrLoginResult(
    val success: Boolean = false,
    val message: String? = null,
    val data: QrLoginData? = null
)

@Serializable
data class QrLoginData(
    val status: String? = null
)

@Serializable
private data class QrLoginRequestBody(
    val sessionId: String,
    val approve: Boolean? = null
)

/**
 * 扫码登录（手机端）：把 PC 端二维码里的 sessionId 上报为「已扫描」，再由用户确认或取消。
 *
 * 对应后端 `/api/user/qrlogin/scan` 与 `/api/user/qrlogin/confirm`，两者都需要用户令牌。
 */
class QrLoginApi(private val token: String?) {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }

    private val baseUrl = UrlConfig.getBaseUrl()

    /** 上报已扫码（PC 端会提示「已扫码，请在手机上确认」） */
    suspend fun scan(sessionId: String): QrLoginResult = post("/api/user/qrlogin/scan", sessionId, approve = null)

    /** 确认或拒绝本次登录 */
    suspend fun confirm(sessionId: String, approve: Boolean): QrLoginResult =
        post("/api/user/qrlogin/confirm", sessionId, approve)

    private suspend fun post(path: String, sessionId: String, approve: Boolean?): QrLoginResult {
        return try {
            val response = client.post("$baseUrl$path") {
                contentType(ContentType.Application.Json)
                token?.let { header("Authorization", "Bearer $it") }
                setBody(QrLoginRequestBody(sessionId = sessionId, approve = approve))
            }
            response.body()
        } catch (e: Exception) {
            Log.e("QrLoginApi", "扫码登录请求失败: $path", e)
            QrLoginResult(success = false, message = "网络错误: ${e.message}")
        }
    }
}
