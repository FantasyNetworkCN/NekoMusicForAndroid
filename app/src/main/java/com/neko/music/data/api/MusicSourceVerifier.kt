package com.neko.music.data.api

import com.neko.music.util.UrlConfig
import com.neko.music.util.installNekoClientHeader
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

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
    private val AGPL_SPDX_IDS = setOf("AGPL-3.0", "AGPL-3.0-only", "AGPL-3.0-or-later")
    private const val MIN_SOURCE_BLOBS = 50
    private const val MIN_TOTAL_SOURCE_BYTES = 100_000L
    private val REQUIRED_SOURCE_FILES = mapOf(
        "backend/pom.xml" to 4_096L,
        "backend/src/main/java/com/neko/music/Main.java" to 1_024L,
        "backend/src/main/java/com/neko/music/handlers/AgplSourceManifestHandler.java" to 1_024L,
        "backend/src/main/java/com/neko/music/handlers/ServletRegistrar.java" to 4_096L,
        "frontend/package.json" to 256L,
        "frontend/src/main.js" to 256L,
        "README.md" to 256L,
    )
    private val LICENSE_PATHS = setOf(
        "LICENSE", "LICENSE.txt", "LICENSE.md", "COPYING", "COPYING.txt",
    )

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
            val sourceUrl = normalizeGithubRepositoryUrl(data.string("sourceUrl"))
                ?: return@withContext Result.failure(
                    IllegalStateException("开源清单中的来源不是 GitHub 仓库根地址")
                )
            verifyGithubRepository(client, sourceUrl)
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

    private suspend fun verifyGithubRepository(client: HttpClient, sourceUrl: String) {
        val (owner, repository) = githubOwnerAndRepository(sourceUrl)
            ?: throw IllegalStateException("来源不是有效 GitHub 仓库")
        val response = client.get("https://api.github.com/repos/$owner/$repository") {
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("GitHub 仓库校验失败：HTTP ${response.status.value}")
        }
        val repo = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
            ?: throw IllegalStateException("GitHub 仓库响应无效")
        if (repo["private"]?.jsonPrimitive?.booleanOrNull == true
            || repo["disabled"]?.jsonPrimitive?.booleanOrNull == true
        ) {
            throw IllegalStateException("GitHub 仓库不是公开可用状态")
        }
        val spdxId = repo["license"]?.jsonObject
            ?.get("spdx_id")
            ?.jsonPrimitive
            ?.content
            .orEmpty()
        if (spdxId !in AGPL_SPDX_IDS) {
            throw IllegalStateException("GitHub 仓库不是 AGPL 开源仓库")
        }
        val htmlUrl = repo["html_url"]?.jsonPrimitive?.content.orEmpty()
        if (!htmlUrl.trimEnd('/').equals(sourceUrl.trimEnd('/'), ignoreCase = true)) {
            throw IllegalStateException("GitHub 仓库身份不匹配")
        }
        val defaultBranch = repo["default_branch"]?.jsonPrimitive?.content
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw IllegalStateException("GitHub 仓库缺少默认分支")
        verifyGithubSourceTree(client, owner, repository, defaultBranch)
    }

    private suspend fun verifyGithubSourceTree(
        client: HttpClient,
        owner: String,
        repository: String,
        defaultBranch: String,
    ) {
        val encodedBranch = URLEncoder.encode(defaultBranch, "UTF-8").replace("+", "%20")
        val response = client.get(
            "https://api.github.com/repos/$owner/$repository/git/trees/$encodedBranch?recursive=1"
        ) {
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("GitHub 源码树校验失败：HTTP ${response.status.value}")
        }
        val tree = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
            ?: throw IllegalStateException("GitHub 源码树响应无效")
        if (tree["truncated"]?.jsonPrimitive?.booleanOrNull == true) {
            throw IllegalStateException("GitHub 源码树不完整，无法完成校验")
        }
        val blobs = tree["tree"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.filter { it["type"]?.jsonPrimitive?.content == "blob" }
            .orEmpty()
        if (blobs.size < MIN_SOURCE_BLOBS) {
            throw IllegalStateException("GitHub 仓库文件过少，疑似空仓库")
        }
        val totalBytes = blobs.sumOf { it["size"]?.jsonPrimitive?.longOrNull ?: 0L }
        if (totalBytes < MIN_TOTAL_SOURCE_BYTES) {
            throw IllegalStateException("GitHub 仓库源码体积异常")
        }
        val byPath = blobs.associateBy { it["path"]?.jsonPrimitive?.content.orEmpty() }
        if (LICENSE_PATHS.none(byPath::containsKey)) {
            throw IllegalStateException("GitHub 仓库根目录缺少许可证文件")
        }
        for ((path, minimumSize) in REQUIRED_SOURCE_FILES) {
            val entry = byPath[path]
                ?: throw IllegalStateException("GitHub 仓库缺少核心源码文件：$path")
            val size = entry["size"]?.jsonPrimitive?.longOrNull ?: 0L
            if (size < minimumSize) {
                throw IllegalStateException("GitHub 源码文件内容异常：$path")
            }
        }
    }

    private fun normalizeGithubRepositoryUrl(raw: String): String? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        if (scheme != "https" || host != "github.com") return null
        val segments = uri.path.orEmpty().trim('/').split('/').filter(String::isNotEmpty)
        if (segments.size != 2) return null
        val repository = segments[1].removeSuffix(".git")
        if (segments[0].isBlank() || repository.isBlank()) return null
        return "https://github.com/${segments[0]}/$repository"
    }

    private fun githubOwnerAndRepository(sourceUrl: String): Pair<String, String>? {
        val uri = runCatching { URI(sourceUrl) }.getOrNull() ?: return null
        val segments = uri.path.orEmpty().trim('/').split('/').filter(String::isNotEmpty)
        return if (segments.size == 2) segments[0] to segments[1].removeSuffix(".git") else null
    }
}
