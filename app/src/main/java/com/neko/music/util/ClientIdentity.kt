package com.neko.music.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.UUID

/** 身份服务暂时不可用；消息可直接展示给用户。 */
class ClientIdentityUnavailableException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/**
 * Android 客户端实例身份。
 *
 * 每个安装实例在 Android Keystore 中生成一把不可导出的 EC P-256 私钥。优先使用 TEE（服务端同样
 * 接受），仅在 TEE 不可用时尝试 StrongBox；密钥生成时把服务端挑战写入 Key Attestation 证书。
 * 注册后保存实例 ID 与 7 天短期令牌，每个动态请求再用私钥签名。
 *
 * 私钥始终留在 Keystore；SharedPreferences 里的令牌单独泄露也不能冒充本实例。
 */
object ClientIdentity {
    const val CLIENT_ID = "nkc_pub_android_prod"
    const val PLATFORM = "android"
    const val PROTOCOL = "neko-client-v1"

    private const val TAG = "NekoClientIdentity"
    private const val KEY_ALIAS = "neko_client_identity_p256_v1"
    private const val PREFS = "neko_client_identity"
    private const val PREF_INSTANCE = "instance_id"
    private const val PREF_TOKEN = "token"
    private const val PREF_EXPIRES_AT = "expires_at"
    private const val PREF_SPKI = "public_key_spki"
    private const val TOKEN_REFRESH_AHEAD_MS = 24 * 60 * 60 * 1000L
    private const val EMPTY_BODY_HASH =
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private const val STREAMED_BODY_HASH = "-"
    private const val RETRY_BASE_MS = 15_000L
    private const val RETRY_MAX_MS = 5 * 60_000L

    private val mutex = Mutex()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cached: Identity? = null

    @Volatile
    private var failureCount = 0

    @Volatile
    private var retryAtMillis = 0L

    @Volatile
    private var lastFailure: Throwable? = null

    private data class Identity(
        val instanceId: String,
        val token: String,
        val expiresAtMillis: Long,
        val privateKey: PrivateKey,
        val publicKeySpki: String,
    )

    private data class KeyMaterial(
        val privateKey: PrivateKey,
        val chain: List<X509Certificate>,
        val publicKeySpki: String,
    )

    private class IdentityHttpException(val status: Int, message: String) : IOException(message)

    /** Application.onCreate 中调用，早于任何 HttpClient 创建。 */
    fun install(context: Context) {
        appContext = context.applicationContext
    }

    /** 请求是否必须携带实例身份；与后端 ClientIdentityFilter 的豁免清单保持一致。 */
    fun isProtectedRequest(request: HttpRequestBuilder): Boolean {
        val url = request.url.build()
        if (url.host != NEKO_BACKEND_HOST) return false
        val method = request.method.value.uppercase()
        if (method == "OPTIONS" || method == "HEAD") return false
        val path = canonicalPath(url.encodedPath)
        if (path in EXEMPT_IDENTITY_PATHS) return false
        if (EXEMPT_IDENTITY_PREFIXES.any { path.startsWith(it) }) return false
        if (path.endsWith("/pull")) return false
        return path.startsWith("/api/") || path.startsWith("/loser/")
    }

    /** 在领取防重放 nonce 前先准备实例身份，避免身份不可用时白做一轮解题。 */
    internal suspend fun prepare(client: HttpClient) {
        ensure(client)
    }

    /** 给请求附加时间戳、实例信息与 ECDSA 签名；nonce 必须已经由防重放层写入。 */
    internal suspend fun sign(client: HttpClient, request: HttpRequestBuilder): HttpRequestBuilder {
        if (!isProtectedRequest(request)) return request
        val identity = ensure(client)
        val copy = HttpRequestBuilder().takeFrom(request)
        val url = copy.url.build()
        val timestamp = System.currentTimeMillis() / 1000L
        val nonce = copy.headers[NEKO_NONCE_HEADER].orEmpty()
        val bodyHash = requestBodyHash(copy)
        val canonical = canonicalRequestForSigning(
            method = request.method.value,
            path = url.encodedPath,
            query = url.encodedQuery,
            instanceId = identity.instanceId,
            timestampSeconds = timestamp,
            bodyHash = bodyHash,
            nonce = nonce,
        )
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(identity.privateKey)
            update(canonical.toByteArray(Charsets.UTF_8))
            sign()
        }
        copy.headers.remove(ID_HEADER_CLIENT)
        copy.headers.remove(ID_HEADER_INSTANCE)
        copy.headers.remove(ID_HEADER_TOKEN)
        copy.headers.remove(ID_HEADER_TIMESTAMP)
        copy.headers.remove(ID_HEADER_SIGNATURE)
        copy.headers.append(ID_HEADER_CLIENT, CLIENT_ID)
        copy.headers.append(ID_HEADER_INSTANCE, identity.instanceId)
        copy.headers.append(ID_HEADER_TOKEN, identity.token)
        copy.headers.append(ID_HEADER_TIMESTAMP, timestamp.toString())
        copy.headers.append(ID_HEADER_SIGNATURE, base64Url(signature))
        return copy
    }

    /**
     * 收到 401 invalid_client 后恢复实例身份。
     *
     * 先续签；实例被吊销或本地登记损坏时，用现有密钥重新注册一个新实例。服务端拒绝时只进入
     * 指数退避并保留本地密钥，避免每次请求都重新生成硬件密钥、耗尽挑战额度。网络错误或服务端
     * 临时故障同样不会销毁本地密钥；只有 Keystore 中确实读不到可用密钥时才重新生成。
     */
    internal suspend fun recover(client: HttpClient): Boolean {
        if (isBackingOff()) return false
        return try {
            mutex.withLock {
                cached = null
                val prefs = prefs()
                val material = loadKeyMaterial()
                if (material == null) {
                    cached = registerNewInstance(client, prefs)
                    clearFailure()
                    return@withLock true
                }

                val instanceId = prefs.getString(PREF_INSTANCE, null).orEmpty()
                val storedSpki = prefs.getString(PREF_SPKI, null).orEmpty()
                val sameKey = storedSpki.isBlank() || storedSpki == material.publicKeySpki
                if (instanceId.isNotBlank() && sameKey) {
                    try {
                        cached = refresh(client, prefs, instanceId, material)
                        clearFailure()
                        return@withLock true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IdentityHttpException) {
                        if (!e.isInvalidIdentity()) throw e
                        Log.w(TAG, "实例续签被拒，尝试用现有密钥重新注册：HTTP ${e.status}")
                    }
                }

                try {
                    cached = registerWithExistingKey(client, prefs, material)
                    clearFailure()
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IdentityHttpException) {
                    if (!e.isInvalidIdentity()) throw e
                    // 不销毁本地密钥：服务端证明校验或临时配置问题不会因换一把密钥而消失，
                    // 反复生成还会耗尽挑战额度。保留密钥，等待退避后重试。
                    Log.w(TAG, "现有密钥重新注册被拒，保留本地密钥：HTTP ${e.status}")
                    throw e
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordFailure(e)
            false
        }
    }

    private suspend fun ensure(client: HttpClient): Identity {
        cached?.let { current ->
            if (current.expiresAtMillis > System.currentTimeMillis() + TOKEN_REFRESH_AHEAD_MS) {
                return current
            }
        }
        return mutex.withLock {
            cached?.let { current ->
                if (current.expiresAtMillis > System.currentTimeMillis() + TOKEN_REFRESH_AHEAD_MS) {
                    return@withLock current
                }
            }
            backoffException()?.let { throw it }
            try {
                val identity = ensureLocked(client)
                cached = identity
                clearFailure()
                identity
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordFailure(e)
                if (e is ClientIdentityUnavailableException) throw e
                throw ClientIdentityUnavailableException(
                    "客户端实例身份不可用：${e.message ?: e.javaClass.simpleName}",
                    e,
                )
            }
        }
    }

    private suspend fun ensureLocked(client: HttpClient): Identity {
        val prefs = prefs()
        val material = loadKeyMaterial()
        if (material == null) return registerNewInstance(client, prefs)

        val instanceId = prefs.getString(PREF_INSTANCE, null).orEmpty()
        val storedSpki = prefs.getString(PREF_SPKI, null).orEmpty()
        if (instanceId.isBlank() || (storedSpki.isNotBlank() && storedSpki != material.publicKeySpki)) {
            return registerWithExistingKey(client, prefs, material)
        }
        val token = prefs.getString(PREF_TOKEN, null).orEmpty()
        val expiresAt = prefs.getLong(PREF_EXPIRES_AT, 0L)
        if (token.isNotBlank() &&
            expiresAt > System.currentTimeMillis() + TOKEN_REFRESH_AHEAD_MS
        ) {
            return Identity(instanceId, token, expiresAt, material.privateKey, material.publicKeySpki)
        }
        return try {
            refresh(client, prefs, instanceId, material)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 401/400 表示令牌已失效，不能继续拿它发请求；网络或 5xx 才允许短暂沿用旧令牌。
            if (e is IdentityHttpException && e.isInvalidIdentity()) throw e
            if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + 5 * 60 * 1000L) {
                Log.w(TAG, "实例令牌续签失败，暂时沿用尚未过期的令牌", e)
                Identity(instanceId, token, expiresAt, material.privateKey, material.publicKeySpki)
            } else {
                throw e
            }
        }
    }

    private fun isBackingOff(): Boolean = retryAtMillis > System.currentTimeMillis()

    private fun backoffException(): ClientIdentityUnavailableException? {
        val remaining = retryAtMillis - System.currentTimeMillis()
        if (remaining <= 0L) return null
        val seconds = (remaining + 999L) / 1000L
        return ClientIdentityUnavailableException(
            "客户端实例身份暂不可用，请 $seconds 秒后重试",
            lastFailure,
        )
    }

    private fun recordFailure(error: Throwable) {
        failureCount = (failureCount + 1).coerceAtMost(6)
        val delay = (RETRY_BASE_MS shl (failureCount - 1)).coerceAtMost(RETRY_MAX_MS)
        retryAtMillis = System.currentTimeMillis() + delay
        lastFailure = error
        Log.w(TAG, "客户端实例身份暂不可用，${delay / 1000} 秒后允许重试", error)
    }

    private fun clearFailure() {
        failureCount = 0
        retryAtMillis = 0L
        lastFailure = null
    }

    private fun prefs() = requireNotNull(appContext) {
        "ClientIdentity.install(context) 尚未调用"
    }.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private suspend fun registerNewInstance(
        client: HttpClient,
        prefs: android.content.SharedPreferences,
    ): Identity {
        val challenge = requestChallenge(client, "register")
        val alias = KEY_ALIAS
        deleteKey(alias)
        generateAttestedKey(alias, challenge)
        val material = readKeyMaterial(alias) ?: error("Keystore 密钥生成后读取失败")
        return register(client, prefs, material, challenge)
    }

    private suspend fun registerWithExistingKey(
        client: HttpClient,
        prefs: android.content.SharedPreferences,
        material: KeyMaterial,
    ): Identity {
        val challenge = requestChallenge(client, "register")
        return register(client, prefs, material, challenge)
    }

    private suspend fun register(
        client: HttpClient,
        prefs: android.content.SharedPreferences,
        material: KeyMaterial,
        challenge: String,
    ): Identity {
        val instanceId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis() / 1000L
        val canonical = canonicalRegisterForSigning(
            instanceId = instanceId,
            publicKeySpki = material.publicKeySpki,
            challenge = challenge,
            timestampSeconds = timestamp,
        )
        val signature = signCanonical(material.privateKey, canonical)
        val body = JSONObject().apply {
            put("client_id", CLIENT_ID)
            put("platform", PLATFORM)
            put("instance_id", instanceId)
            put("public_key", material.publicKeySpki)
            put("challenge", challenge)
            put("timestamp", timestamp)
            put("signature", signature)
            put("attestation", JSONObject().put("chain", org.json.JSONArray(
                material.chain.map { base64Url(it.encoded) }
            )))
        }
        val data = postJson(client, "/api/client/register", body)
        val storedInstance = data.optString("instance_id")
        val token = data.optString("token")
        if (storedInstance != instanceId || token.isBlank()) {
            error("客户端注册响应格式不正确")
        }
        val expiresAt = data.optLong("expires_at", 0L) * 1000L
        prefs.edit()
            .putString(PREF_INSTANCE, instanceId)
            .putString(PREF_TOKEN, token)
            .putLong(PREF_EXPIRES_AT, expiresAt)
            .putString(PREF_SPKI, material.publicKeySpki)
            .apply()
        Log.i(TAG, "客户端实例注册成功，证明级别=${data.optString("attestation")}")
        return Identity(instanceId, token, expiresAt, material.privateKey, material.publicKeySpki)
    }

    private suspend fun refresh(
        client: HttpClient,
        prefs: android.content.SharedPreferences,
        instanceId: String,
        material: KeyMaterial,
    ): Identity {
        val challenge = requestChallenge(client, "token")
        val timestamp = System.currentTimeMillis() / 1000L
        val canonical = canonicalTokenForSigning(
            instanceId = instanceId,
            challenge = challenge,
            timestampSeconds = timestamp,
        )
        val body = JSONObject().apply {
            put("client_id", CLIENT_ID)
            put("platform", PLATFORM)
            put("instance_id", instanceId)
            put("challenge", challenge)
            put("timestamp", timestamp)
            put("signature", signCanonical(material.privateKey, canonical))
        }
        val data = postJson(client, "/api/client/token", body)
        val token = data.optString("token")
        if (token.isBlank()) error("客户端续签响应缺少 token")
        val expiresAt = data.optLong("expires_at", 0L) * 1000L
        prefs.edit()
            .putString(PREF_TOKEN, token)
            .putLong(PREF_EXPIRES_AT, expiresAt)
            .apply()
        return Identity(instanceId, token, expiresAt, material.privateKey, material.publicKeySpki)
    }

    private suspend fun requestChallenge(client: HttpClient, purpose: String): String {
        val response = client.get("${UrlConfig.getBaseUrl()}/api/client/challenge") {
            parameter("client_id", CLIENT_ID)
            parameter("platform", PLATFORM)
            parameter("purpose", purpose)
        }
        val text = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            throw IdentityHttpException(
                response.status.value,
                "客户端挑战接口失败：HTTP ${response.status.value}",
            )
        }
        val data = JSONObject(text).optJSONObject("data") ?: error("客户端挑战响应缺少 data")
        if (data.optString("protocol") != PROTOCOL) error("客户端挑战协议不匹配")
        return data.optString("challenge").takeIf { it.isNotBlank() }
            ?: error("客户端挑战响应缺少 challenge")
    }

    private suspend fun postJson(client: HttpClient, path: String, body: JSONObject): JSONObject {
        val response = client.post("${UrlConfig.getBaseUrl()}$path") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val text = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            throw IdentityHttpException(
                response.status.value,
                "客户端身份接口失败：HTTP ${response.status.value}",
            )
        }
        val root = JSONObject(text)
        if (!root.optBoolean("success", false)) error(root.optString("message").ifBlank { "客户端身份接口失败" })
        return root.optJSONObject("data") ?: error("客户端身份响应缺少 data")
    }

    private fun generateAttestedKey(alias: String, challenge: String): KeyPair {
        // TEE 已满足服务端的硬件证明要求；优先使用它，避免部分设备 StrongBox 依赖 RKP
        // 远程预置而阻塞数秒。只有 TEE 不可用时才尝试 StrongBox。
        try {
            Log.i(TAG, "尝试使用 TEE 生成客户端实例密钥")
            return generateKey(alias, challenge, strongBox = false)
        } catch (e: Exception) {
            Log.w(TAG, "TEE 生成实例密钥失败", e)
            deleteKey(alias)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                !appContext!!.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            ) {
                throw e
            }
        }
        try {
            Log.i(TAG, "尝试使用 StrongBox 生成客户端实例密钥")
            return generateKey(alias, challenge, strongBox = true)
        } catch (e: StrongBoxUnavailableException) {
            Log.w(TAG, "StrongBox 不可用", e)
            deleteKey(alias)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "StrongBox 生成实例密钥失败", e)
            deleteKey(alias)
            throw e
        }
    }

    private fun generateKey(alias: String, challenge: String, strongBox: Boolean): KeyPair {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        ).apply {
            setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            setDigests(KeyProperties.DIGEST_SHA256)
            setAttestationChallenge(challenge.toByteArray(Charsets.UTF_8))
            if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setIsStrongBoxBacked(true)
            }
        }
        val generator = KeyPairGenerator.getInstance("EC", "AndroidKeyStore")
        generator.initialize(builder.build())
        return generator.generateKeyPair()
    }

    private fun loadKeyMaterial(): KeyMaterial? = try {
        readKeyMaterial(KEY_ALIAS)
    } catch (e: Exception) {
        Log.w(TAG, "读取 Android Keystore 实例密钥失败", e)
        null
    }

    private fun readKeyMaterial(alias: String): KeyMaterial? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = store.getEntry(alias, null) as? KeyStore.PrivateKeyEntry ?: return null
        val chain = entry.certificateChain?.filterIsInstance<X509Certificate>().orEmpty()
        if (chain.isEmpty()) return null
        Log.d(TAG, "读取实例密钥证书链：${chain.size} 张")
        return KeyMaterial(
            privateKey = entry.privateKey,
            chain = chain,
            publicKeySpki = base64Url(entry.certificate.publicKey.encoded),
        )
    }

    private fun deleteKey(alias: String) {
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        } catch (e: Exception) {
            Log.w(TAG, "删除旧 Keystore 密钥失败", e)
        }
    }

    private fun signCanonical(privateKey: PrivateKey, canonical: String): String =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(canonical.toByteArray(Charsets.UTF_8))
            base64Url(sign())
        }

    private fun requestBodyHash(request: HttpRequestBuilder): String {
        if (request.method.value.equals("GET", ignoreCase = true) ||
            request.method.value.equals("HEAD", ignoreCase = true)
        ) {
            return EMPTY_BODY_HASH
        }
        val body = request.body as? OutgoingContent ?: return EMPTY_BODY_HASH
        val contentType = body.contentType?.toString()
            ?: request.headers[io.ktor.http.HttpHeaders.ContentType]
        if (contentType?.startsWith("multipart/", ignoreCase = true) == true) {
            return STREAMED_BODY_HASH
        }
        var content: OutgoingContent = body
        while (content is OutgoingContent.ContentWrapper) content = content.delegate()
        return when (content) {
            is OutgoingContent.NoContent -> EMPTY_BODY_HASH
            is OutgoingContent.ByteArrayContent -> sha256Hex(content.bytes())
            else -> {
                if (content.contentLength == 0L) return EMPTY_BODY_HASH
                Log.w(TAG, "无法计算流式请求体摘要：${content::class.java.name}")
                STREAMED_BODY_HASH
            }
        }
    }

    private fun IdentityHttpException.isInvalidIdentity(): Boolean = status == 400 || status == 401

    internal fun canonicalRequestForSigning(
        method: String,
        path: String,
        query: String,
        instanceId: String,
        timestampSeconds: Long,
        bodyHash: String,
        nonce: String,
        clientId: String = CLIENT_ID,
        platform: String = PLATFORM,
    ): String = listOf(
        PROTOCOL,
        method.uppercase(),
        canonicalPath(path),
        query,
        clientId,
        instanceId,
        platform,
        timestampSeconds.toString(),
        bodyHash,
        nonce,
    ).joinToString("\n")

    internal fun canonicalRegisterForSigning(
        instanceId: String,
        publicKeySpki: String,
        challenge: String,
        timestampSeconds: Long,
        clientId: String = CLIENT_ID,
        platform: String = PLATFORM,
    ): String = listOf(
        "neko-client-register-v1",
        clientId,
        platform,
        instanceId,
        publicKeySpki,
        challenge,
        timestampSeconds.toString(),
    ).joinToString("\n")

    internal fun canonicalTokenForSigning(
        instanceId: String,
        challenge: String,
        timestampSeconds: Long,
        clientId: String = CLIENT_ID,
        platform: String = PLATFORM,
    ): String = listOf(
        "neko-client-token-v1",
        clientId,
        platform,
        instanceId,
        challenge,
        timestampSeconds.toString(),
    ).joinToString("\n")

    internal fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun base64Url(bytes: ByteArray): String = Base64.encodeToString(
        bytes,
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    /** 与服务端 RequestPath 的规范化路径保持一致。 */
    private fun canonicalPath(encodedPath: String): String {
        val trailing = encodedPath.length > 1 && encodedPath.endsWith('/')
        val segments = ArrayDeque<String>()
        for (raw in encodedPath.split('/')) {
            val withoutParameters = raw.substringBefore(';')
            val segment = try {
                URLDecoder.decode(withoutParameters.replace("+", "%2B"), "UTF-8")
            } catch (_: Exception) {
                withoutParameters
            }
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeLast()
                else -> segments.addLast(segment)
            }
        }
        return "/" + segments.joinToString("/") + if (trailing && segments.isNotEmpty()) "/" else ""
    }

    private const val ID_HEADER_CLIENT = "X-Neko-Client-Id"
    private const val ID_HEADER_INSTANCE = "X-Neko-Client-Instance"
    private const val ID_HEADER_TOKEN = "X-Neko-Client-Token"
    private const val ID_HEADER_TIMESTAMP = "X-Neko-Client-Timestamp"
    private const val ID_HEADER_SIGNATURE = "X-Neko-Client-Signature"

    private val EXEMPT_IDENTITY_PATHS = setOf(
        "/api/client/challenge",
        "/api/client/register",
        "/api/client/token",
        "/api/replay/challenge",
        "/api/replay/nonce",
        "/api/music/latest",
        "/api/music/ranking",
        "/api/payment/zpay/notify",
        "/api/user/qrlogin/status",
        "/api/user/notifications/stream",
        "/api/admin/logs/access/stream",
        "/api/user/upload/preview",
    )
    private val EXEMPT_IDENTITY_PREFIXES = listOf("/api/music/cover/", "/api/user/avatar/")
}

