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
import io.ktor.client.statement.HttpResponse
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 通用请求防重放（Android 客户端）。
 *
 * 服务端要求所有动态接口（`/api/` 与 `/loser/` 前缀）以及客户端版本检查 `/version` 携带一次性请求头
 * [NEKO_NONCE_HEADER]，同一个 nonce 只能消费一次，重复发送（重放）返回 `409` 并带
 * [NEKO_REPLAY_STATUS_HEADER] 说明原因。本文件提供：
 *
 *   1. [NoncePool]：按「换题 → 解题 → 兑换」向 `GET /api/replay/challenge` +
 *      `GET /api/replay/nonce` 批量预取读 / 写两类 nonce 并缓存，低水位时后台补齐，
 *      本地提前作废（服务端 TTL 120s，本地 90s）；
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

/** 服务端约定的解题算法标识；换题响应里的 `algorithm` 必须与它一致，否则不盲解。 */
private const val POW_ALGORITHM = "sha256-leading-zero-bits"

/** 难度上限：服务端远低于此值，这里只是防止异常输入把线程卡死。 */
private const val MAX_DIFFICULTY_BITS = 64

/** 一轮领取（换题 → 解题 → 兑换）的最大尝试次数；被拒就换一道题重解再来。 */
private const val CLAIM_ATTEMPTS = 2

/** 被限额（429）时的最长退避；领取处在请求路径上，上限比 Web 端取得更小。 */
private const val RETRY_AFTER_MAX_MS = 1_000L

private const val HTTP_OK = 200
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_CONFLICT = 409
private const val HTTP_BAD_REQUEST = 400

/** 与服务端 `EXEMPT_PATHS` 对应。 */
private val EXEMPT_PATHS = setOf(
    "/api/replay/challenge",
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

/** 一道待解的挑战题（与 `GET /api/replay/challenge` 的响应字段对应）。 */
private class Challenge(val id: String, val seed: String, val difficulty: Int)

/**
 * 解出挑战题：找一个十进制计数器 `counter`，使 `SHA-256("$seed:$counter")` 的前导零比特数
 * 达到 [difficulty]，返回计数器的十进制字符串作为 `proof`。
 *
 * 服务端只验一次哈希，客户端要试 2^difficulty 量级的次数——这种成本不对称正是该方案的基础，
 * 所以这里复用同一个 [MessageDigest] 与消息缓冲区，不做多余分配。
 */
internal fun solveProof(seed: String, difficulty: Int): String {
    require(difficulty in 0..MAX_DIFFICULTY_BITS) { "挑战难度非法：$difficulty" }

    val prefix = "$seed:".toByteArray(Charsets.UTF_8)
    // 缓冲区够放下最长 19 位十进制计数器，随用随覆盖
    val message = ByteArray(prefix.size + 20)
    prefix.copyInto(message)
    val digest = MessageDigest.getInstance("SHA-256")

    var counter = 0L
    while (true) {
        val length = prefix.size + writeDecimal(message, prefix.size, counter)
        digest.reset()
        digest.update(message, 0, length)
        if (meetsDifficulty(digest.digest(), difficulty)) return counter.toString()
        counter += 1
    }
}

/** 把 [value] 的十进制写进 [target] 的 [offset] 处，返回写入的字节数。 */
private fun writeDecimal(target: ByteArray, offset: Int, value: Long): Int {
    if (value == 0L) {
        target[offset] = '0'.code.toByte()
        return 1
    }
    var digits = 0
    var remaining = value
    while (remaining > 0) {
        digits += 1
        remaining /= 10
    }
    remaining = value
    var index = offset + digits - 1
    while (remaining > 0) {
        target[index] = ('0'.code + (remaining % 10).toInt()).toByte()
        index -= 1
        remaining /= 10
    }
    return digits
}

/** 摘要的前导零比特数是否达到 [bits]（与后端 `ReplayChallengeService.meetsDifficulty` 一致）。 */
internal fun meetsDifficulty(hash: ByteArray, bits: Int): Boolean {
    val fullBytes = bits / 8
    val remainingBits = bits % 8
    if (hash.size < fullBytes + if (remainingBits > 0) 1 else 0) return false
    for (index in 0 until fullBytes) {
        if (hash[index].toInt() != 0) return false
    }
    if (remainingBits == 0) return true
    val mask = (0xFF shl (8 - remainingBits)) and 0xFF
    return (hash[fullBytes].toInt() and mask) == 0
}

/** 被限额时的退避时长：听 `Retry-After`，并设上限避免拖住请求路径。 */
private fun retryAfterMs(response: HttpResponse): Long {
    val seconds = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull() ?: return 0L
    if (seconds <= 0L) return 0L
    return (seconds * 1000L).coerceAtMost(RETRY_AFTER_MAX_MS)
}

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
            claim(client, read, write)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "领取防重放 nonce 异常", e)
            false
        }
    }

    /** 一轮领取：换题 → 本地解题 → 兑换；被拒（题目失效 / 解答不合格）就换一道题重解再试。 */
    private suspend fun claim(client: HttpClient, read: Int, write: Int): Boolean {
        repeat(CLAIM_ATTEMPTS) {
            val challenge = fetchChallenge(client, read, write)
            val query = if (challenge == null) {
                // 服务端还没有挑战接口（分批发版期间）时回退为直接领取：新服务端会拒绝，
                // 拿不到 nonce 也不会带来副作用。
                "read=$read&write=$write"
            } else {
                // 解题要试 2^difficulty 量级的哈希，放到计算线程上做，别卡住调用方
                val proof = withContext(Dispatchers.Default) {
                    solveProof(challenge.seed, challenge.difficulty)
                }
                "challenge=${challenge.id}&proof=$proof"
            }
            val response = client.get("${UrlConfig.getBaseUrl()}/api/replay/nonce?$query")
            when (response.status.value) {
                HTTP_OK -> return storeNonces(response.bodyAsText())
                HTTP_TOO_MANY_REQUESTS -> {
                    // 限额：退避一小会儿就放弃，交给下一次补领，别在这儿空转
                    delay(retryAfterMs(response))
                    return false
                }
                HTTP_BAD_REQUEST, HTTP_CONFLICT -> Unit
                else -> {
                    Log.w(TAG, "领取防重放 nonce 失败：HTTP ${response.status.value}")
                    return false
                }
            }
        }
        Log.w(TAG, "连续 $CLAIM_ATTEMPTS 轮领取防重放 nonce 均被拒")
        return false
    }

    /**
     * 换一道挑战题；返回 null 表示本轮没有题目可用（服务端还没有挑战接口、被限额或临时故障），
     * 调用方回退为直接领取即可——两条路都安全，旧服务端能正常签发，新服务端会拒绝无挑战的领取。
     */
    private suspend fun fetchChallenge(client: HttpClient, read: Int, write: Int): Challenge? {
        val response = client.get(
            "${UrlConfig.getBaseUrl()}/api/replay/challenge?read=$read&write=$write"
        )
        if (response.status.value == HTTP_TOO_MANY_REQUESTS) {
            delay(retryAfterMs(response))
            return null
        }
        if (response.status.value != HTTP_OK) return null
        val data = JSONObject(response.bodyAsText()).optJSONObject("data") ?: return null
        val algorithm = data.optString("algorithm")
        if (algorithm != POW_ALGORITHM) {
            Log.w(TAG, "未知的挑战算法：$algorithm")
            return null
        }
        val id = data.optString("challenge")
        val seed = data.optString("seed")
        val difficulty = data.optInt("difficulty", -1)
        if (id.isEmpty() || seed.isEmpty() || difficulty < 0) return null
        return Challenge(id, seed, difficulty)
    }

    /** 解析兑换响应并写入池；结构不对时返回 false。 */
    private suspend fun storeNonces(body: String): Boolean {
        val nonces = JSONObject(body).optJSONObject("data")?.optJSONObject("nonces")
        if (nonces == null) {
            Log.w(TAG, "领取防重放 nonce 失败：响应缺少 nonces")
            return false
        }
        store(SCOPE_READ, nonces.optJSONArray("read"))
        store(SCOPE_WRITE, nonces.optJSONArray("write"))
        return true
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
