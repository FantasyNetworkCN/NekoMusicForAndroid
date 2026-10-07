package com.neko.music.util

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * 通用请求防重放（Android 客户端）。
 *
 * 服务端要求所有动态接口（`/api/` 与 `/loser/` 前缀）以及客户端版本检查 `/version` 携带一次性请求头
 * [NEKO_NONCE_HEADER]，同一个 nonce 只能消费一次，重复发送（重放）返回 `409` 并带
 * [NEKO_REPLAY_STATUS_HEADER] 说明原因。本文件提供：
 *
 *   1. [NoncePool]：向 `GET /api/replay/nonce` 批量预取读 / 写两类 nonce 并缓存，
 *      低水位时后台补齐，本地提前作废（服务端 TTL 120s，本地 90s）；
 *   2. [NekoReplayNoncePlugin]：Ktor 客户端插件，自动为受保护请求附加 nonce，
 *      遇到 `409`（`missing` / `invalid`）时清空缓存、换一个新 nonce 重试一次；
 *   3. [installNekoReplayProtection]：由 [installNekoClientHeader] 一并安装，
 *      因此所有 Ktor 客户端自动获得该能力，无需逐个改造。
 *
 * 只处理发往本站后端（[UrlConfig.getBaseUrl] 同主机）的请求；豁免清单与后端
 * `ReplayProtectionFilter` 保持一致，不一致只会多领 nonce，不会误拦。
 */

/** 一次性防重放 nonce 请求头；由 [NekoReplayNoncePlugin] 自动附加。 */
const val NEKO_NONCE_HEADER = "X-Neko-Nonce"

/** 服务端拒绝原因响应头（`missing` / `invalid`），用于判断是否换新 nonce 重试。 */
const val NEKO_REPLAY_STATUS_HEADER = "X-Neko-Replay-Status"

private const val TAG = "NekoReplay"

/** 读 / 写两类 nonce，必须与业务方法匹配（GET 读，其余写）。 */
private const val SCOPE_READ = "r"
private const val SCOPE_WRITE = "w"

/** nonce 本地提前作废时间；服务端 TTL 为 120 秒，留出网络与重试余量。 */
private const val NONCE_LOCAL_MAX_AGE_MS = 90_000L
private const val NONCE_BATCH = 16
private const val NONCE_LOW_WATER = 4
private const val REPLAY_RETRY_LIMIT = 1

/** 与服务端 `EXEMPT_PATHS` 对应。 */
private val EXEMPT_PATHS = setOf(
    "/api/replay/nonce",
    "/api/music/latest",
    "/api/music/ranking",
    "/api/payment/zpay/notify",
    "/api/user/qrlogin/status",
)

/** 与服务端 `EXEMPT_PREFIXES` 对应。 */
private val EXEMPT_PREFIXES = listOf("/api/music/cover/", "/api/user/avatar/")

/**
 * 非 `/api` 前缀、但按同一约定提前携带 nonce 的接口（客户端版本检查）。
 * 服务端当前尚未对该路径强制校验，提前携带是为后续纳管做好兼容，多带一次无副作用。
 */
private val PROTECTED_PATHS = setOf("/version")

/** 本站后端主机名；只对发往本站的请求附加 nonce，避免泄漏到第三方。 */
private val BACKEND_HOST: String by lazy { Url(UrlConfig.getBaseUrl()).host }

private class NonceEntry(val value: String, val issuedAt: Long)

/** 读 / 写 nonce 池；池内元素一次使用后即丢弃。 */
private object NoncePool {
    private val mutex = Mutex()
    /** 串行化领取：并发请求同时发现池空时，只让一个真正回源，其余等它补齐后复用。 */
    private val fetchMutex = Mutex()
    private val readPool = ArrayDeque<NonceEntry>()
    private val writePool = ArrayDeque<NonceEntry>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refilling = false

    private fun poolOf(scopeKey: String) = if (scopeKey == SCOPE_READ) readPool else writePool

    /** 取一个未过期的 nonce；池空时补一批（并发调用只会回源一次）。 */
    suspend fun obtain(client: HttpClient, scopeKey: String): String? {
        pop(scopeKey)?.let { nonce ->
            maybeRefill(client)
            return nonce
        }
        fetchMutex.withLock {
            // 等锁期间可能已被其它请求补齐，先复用再考虑回源。
            pop(scopeKey)?.let { return it }
            fetch(client, scopeKey, NONCE_BATCH)
        }
        return pop(scopeKey)
    }

    /** 被服务端判定为重放后调用：清空旧池（多为 IP 变化 / 服务端重启）并重新领取。 */
    suspend fun recover(client: HttpClient, scopeKey: String): String? {
        mutex.withLock { readPool.clear(); writePool.clear() }
        fetchMutex.withLock {
            if (!fetch(client, scopeKey, NONCE_BATCH)) return null
        }
        return pop(scopeKey)
    }

    private suspend fun pop(scopeKey: String): String? = mutex.withLock {
        val queue = poolOf(scopeKey)
        val now = System.currentTimeMillis()
        while (queue.isNotEmpty()) {
            val entry = queue.removeFirst()
            if (now - entry.issuedAt < NONCE_LOCAL_MAX_AGE_MS) return@withLock entry.value
        }
        null
    }

    /** 任一池低于低水位时后台补一批；失败只记日志，不阻塞业务请求。 */
    private suspend fun maybeRefill(client: HttpClient) {
        val needRead: Boolean
        val needWrite: Boolean
        mutex.withLock {
            if (refilling) return
            needRead = readPool.size < NONCE_LOW_WATER
            needWrite = writePool.size < NONCE_LOW_WATER
            if (!needRead && !needWrite) return
            refilling = true
        }
        scope.launch {
            try {
                fetchMutex.withLock {
                    fetch(client, if (needRead) SCOPE_READ else SCOPE_WRITE, NONCE_BATCH)
                }
            } finally {
                mutex.withLock { refilling = false }
            }
        }
    }

    private suspend fun fetch(client: HttpClient, scopeKey: String, count: Int): Boolean {
        val read = if (scopeKey == SCOPE_READ) count else 0
        val write = if (scopeKey == SCOPE_WRITE) count else 0
        return try {
            val response = client.get("${UrlConfig.getBaseUrl()}/api/replay/nonce?read=$read&write=$write")
            if (response.status.value != HttpStatusCode.OK.value) {
                Log.w(TAG, "领取防重放 nonce 失败：HTTP ${response.status.value}")
                return false
            }
            val nonces = JSONObject(response.bodyAsText())
                .optJSONObject("data")
                ?.optJSONObject("nonces")
            if (nonces == null) {
                Log.w(TAG, "领取防重放 nonce 失败：响应缺少 nonces")
                return false
            }
            store(SCOPE_READ, nonces.optJSONArray("read"))
            store(SCOPE_WRITE, nonces.optJSONArray("write"))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "领取防重放 nonce 异常", e)
            false
        }
    }

    private suspend fun store(scopeKey: String, array: JSONArray?) {
        if (array == null || array.length() == 0) return
        val now = System.currentTimeMillis()
        mutex.withLock {
            val queue = poolOf(scopeKey)
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isNotEmpty()) queue.addLast(NonceEntry(value, now))
            }
        }
    }
}

/** 该请求是否需要 nonce；返回读 / 写类别，不需要时返回 null。 */
private fun replayScopeOf(request: HttpRequestBuilder): String? {
    val scopeKey = when (request.method) {
        HttpMethod.Get -> SCOPE_READ
        HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete -> SCOPE_WRITE
        else -> return null
    }
    val url = request.url.build()
    if (url.host != BACKEND_HOST) return null
    if (isMultipart(request)) return null
    val path = url.encodedPath
    if (path in EXEMPT_PATHS) return null
    if (EXEMPT_PREFIXES.any { path.startsWith(it) }) return null
    // /loser/ 下的 pull 进度流为 SSE 长连接，无法逐请求携带 nonce
    if (path.endsWith("/pull")) return null
    val dynamic = path.startsWith("/api/") || path.startsWith("/loser/")
    if (!dynamic && path !in PROTECTED_PATHS) return null
    return scopeKey
}

private fun isMultipart(request: HttpRequestBuilder): Boolean {
    val fromHeader = request.headers[HttpHeaders.ContentType]
    if (fromHeader != null && fromHeader.startsWith("multipart/", ignoreCase = true)) return true
    val body = request.body as? OutgoingContent ?: return false
    val contentType = body.contentType?.toString() ?: return false
    return contentType.startsWith("multipart/", ignoreCase = true)
}

/** 复制请求并写入 nonce；每次发送都用独立副本，避免共享 builder 被反复改写。 */
private fun withNonce(request: HttpRequestBuilder, nonce: String?): HttpRequestBuilder {
    val copy = HttpRequestBuilder().takeFrom(request)
    if (!nonce.isNullOrEmpty()) {
        copy.headers.remove(NEKO_NONCE_HEADER)
        copy.headers.append(NEKO_NONCE_HEADER, nonce)
    }
    return copy
}

private fun isReplayRejected(call: HttpClientCall): Boolean {
    val response = call.response
    if (response.status != HttpStatusCode.Conflict) return false
    return when (response.headers[NEKO_REPLAY_STATUS_HEADER]) {
        "missing", "invalid" -> true
        else -> false
    }
}

/**
 * 自动附加一次性 nonce，并在被判定为重放时换新 nonce 重试一次。
 *
 * 重试是安全的：被拒的请求在进入业务逻辑前就返回 409，不会产生重复副作用。
 */
val NekoReplayNoncePlugin: ClientPlugin<Unit> = createClientPlugin("NekoReplayNonce") {
    val httpClient = client
    on(Send) { request ->
        val scopeKey = replayScopeOf(request) ?: return@on proceed(request)
        var call = proceed(withNonce(request, NoncePool.obtain(httpClient, scopeKey)))
        var attempt = 0
        while (attempt < REPLAY_RETRY_LIMIT && isReplayRejected(call)) {
            attempt++
            val nonce = NoncePool.recover(httpClient, scopeKey) ?: break
            Log.i(TAG, "防重放校验失败，换新 nonce 重试：${request.method.value} ${request.url.build().encodedPath}")
            call = proceed(withNonce(request, nonce))
        }
        call
    }
}

/** 给该 [HttpClientConfig] 安装防重放能力（由 [installNekoClientHeader] 调用）。 */
fun HttpClientConfig<*>.installNekoReplayProtection() {
    install(NekoReplayNoncePlugin)
}
