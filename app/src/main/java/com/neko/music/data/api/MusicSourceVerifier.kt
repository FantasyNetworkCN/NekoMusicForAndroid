package com.neko.music.data.api

import com.neko.music.util.UrlConfig
import com.neko.music.util.installNekoClientHeader
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class VerifiedMusicSource(
    val baseUrl: String,
    val sourceUrl: String,
)

/**
 * 自定义音乐连接源准入校验。
 *
 * 目标站必须公开 AGPL 来源清单，并提供一个可访问的 HTTPS 源码地址。该检查用于确认
 * 部署方履行 AGPL 的源码提供义务，同时要求目标站使用相同的 API 协议版本。
 */
object MusicSourceVerifier {
    private const val EXPECTED_SCHEMA = "nekomusic-agpl-source/v1"
    private const val EXPECTED_PROJECT = "NekoMusic"
    private const val EXPECTED_LICENSE = "AGPL-3.0-only"
    private const val EXPECTED_API_PROTOCOL = 1
    private const val TIMEOUT_MS = 12_000L

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun verify(rawBaseUrl: String): Result<VerifiedMusicSource> = withContext(Dispatchers.IO) {
        val baseUrl = UrlConfig.normalizeBaseUrl(rawBaseUrl)
            ?: return@withContext Result.failure(
                IllegalArgumentException("无效的音乐连接源地址")
            )
        val client = HttpClient(OkHttp) {
            installNekoClientHeader()
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MS
                connectTimeoutMillis = TIMEOUT_MS
                socketTimeoutMillis = TIMEOUT_MS
            }
        }
        try {
            val response = client.get(UrlConfig.getSourceManifestUrl(baseUrl))
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    IllegalStateException("开源清单不可用：HTTP ${response.status.value}")
                )
            }
            val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
                ?: return@withContext Result.failure(IllegalStateException("开源清单不是 JSON 对象"))
            if (root["success"]?.jsonPrimitive?.booleanOrNull != true) {
                return@withContext Result.failure(IllegalStateException("开源清单未声明成功"))
            }
            val data = root["data"]?.jsonObject
                ?: return@withContext Result.failure(IllegalStateException("开源清单缺少 data"))
            if (data.string("schema") != EXPECTED_SCHEMA
                || data.string("project") != EXPECTED_PROJECT
                || data.string("license") != EXPECTED_LICENSE
                || data["apiProtocol"]?.jsonPrimitive?.intOrNull != EXPECTED_API_PROTOCOL
            ) {
                return@withContext Result.failure(
                    IllegalStateException("目标服务未声明兼容的 AGPL 开源协议")
                )
            }
            val sourceUrl = normalizeSourceUrl(data.string("sourceUrl"))
                ?: return@withContext Result.failure(
                    IllegalStateException("开源清单中的源码地址无效")
                )
            val sourceResponse = client.head(sourceUrl)
            if (!sourceResponse.status.isSuccess()) {
                return@withContext Result.failure(
                    IllegalStateException("源码地址不可访问：HTTP ${sourceResponse.status.value}")
                )
            }
            Result.success(VerifiedMusicSource(baseUrl = baseUrl, sourceUrl = sourceUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            client.close()
        }
    }

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.content?.trim().orEmpty()

    private fun normalizeSourceUrl(raw: String): String? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        val loopback = host == "localhost" || host == "::1" || host == "[::1]" || host.startsWith("127.")
        if (scheme != "https" && !(scheme == "http" && loopback)) return null
        return runCatching { URI(scheme, null, host, uri.port, uri.rawPath, null, null).toASCIIString() }
            .getOrNull()
    }
}
