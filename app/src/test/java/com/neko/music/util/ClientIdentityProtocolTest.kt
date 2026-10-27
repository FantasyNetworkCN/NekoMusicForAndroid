package com.neko.music.util

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Android 端签名串必须与后端 ClientIdentityProtocol 逐字节一致。 */
class ClientIdentityProtocolTest {

    private val instanceId = "550e8400-e29b-41d4-a716-446655440000"
    private val bodyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private val nonce = "nonce-value"

    @Test
    fun requestCanonicalMatchesServerContract() {
        val actual = ClientIdentity.canonicalRequestForSigning(
            method = "get",
            path = "/api/music/file/77",
            query = "quality=flac",
            instanceId = instanceId,
            timestampSeconds = 1_790_000_000L,
            bodyHash = bodyHash,
            nonce = nonce,
        )

        assertEquals(
            listOf(
                "neko-client-v1",
                "GET",
                "/api/music/file/77",
                "quality=flac",
                "nkc_pub_android_prod",
                instanceId,
                "android",
                "1790000000",
                bodyHash,
                nonce,
            ).joinToString("\n"),
            actual,
        )
    }

    @Test
    fun registrationCanonicalMatchesServerContract() {
        val actual = ClientIdentity.canonicalRegisterForSigning(
            instanceId = instanceId,
            publicKeySpki = "spki-value",
            challenge = "register-challenge",
            timestampSeconds = 1_790_000_001L,
        )

        assertEquals(
            listOf(
                "neko-client-register-v1",
                "nkc_pub_android_prod",
                "android",
                instanceId,
                "spki-value",
                "register-challenge",
                "1790000001",
            ).joinToString("\n"),
            actual,
        )
    }

    @Test
    fun tokenCanonicalMatchesServerContract() {
        val actual = ClientIdentity.canonicalTokenForSigning(
            instanceId = instanceId,
            challenge = "token-challenge",
            timestampSeconds = 1_790_000_002L,
        )

        assertEquals(
            listOf(
                "neko-client-token-v1",
                "nkc_pub_android_prod",
                "android",
                instanceId,
                "token-challenge",
                "1790000002",
            ).joinToString("\n"),
            actual,
        )
    }

    @Test
    fun pathIsCanonicalizedLikeServerRequestPath() {
        val actual = ClientIdentity.canonicalRequestForSigning(
            method = "GET",
            path = "/api/./music/%66ile/77;v=1/",
            query = "",
            instanceId = instanceId,
            timestampSeconds = 1_790_000_000L,
            bodyHash = bodyHash,
            nonce = nonce,
        )

        assertTrue(actual.contains("\n/api/music/file/77/\n"))
    }

    @Test
    fun emptyBodyUsesKnownSha256() {
        assertEquals(bodyHash, ClientIdentity.sha256Hex(ByteArray(0)))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ClientIdentity.sha256Hex("abc".toByteArray(Charsets.UTF_8)),
        )
    }

    @Test
    fun renderDownloadRequiresClientIdentity() {
        val request = HttpRequestBuilder().apply {
            method = HttpMethod.Get
            url("${UrlConfig.getBaseUrl()}/api/video/render/$instanceId/download")
        }
        assertTrue(ClientIdentity.isProtectedRequest(request))
    }

    @Test
    fun streamingAndPreviewEndpointsDoNotRequireClientIdentity() {
        listOf(
            "/api/user/notifications/stream",
            "/api/admin/logs/access/stream",
            "/api/user/upload/preview",
        ).forEach { path ->
            val request = HttpRequestBuilder().apply {
                method = HttpMethod.Get
                url("${UrlConfig.getBaseUrl()}$path")
            }
            assertEquals(path, false, ClientIdentity.isProtectedRequest(request))
        }
    }
}
