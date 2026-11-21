package com.neko.music.service

import com.neko.music.debug.NekoLog as Log
import com.neko.music.util.installNekoClientHeader
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class StemModelPhase { MISSING, CHECKING, DOWNLOADING, VERIFYING, READY, FAILED }

internal data class StemModelState(
    val phase: StemModelPhase = StemModelPhase.MISSING,
    val receivedBytes: Long = 0,
    val totalBytes: Long = StemModelRepository.MODEL_BYTES,
) {
    val progress: Float get() = (receivedBytes.toFloat() / totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f)
}

/** 模型资源独立于歌曲与播放开关，同一时刻只执行一次校验或下载。 */
internal class StemModelRepository(
    private val file: File,
    private val transfer: suspend (File, (Long) -> Unit) -> Unit = { destination, progress ->
        download(destination, progress)
    },
    private val expectedBytes: Long = MODEL_BYTES,
    private val expectedHash: String = MODEL_SHA256,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(StemModelState(
        phase = if (file.length() == expectedBytes) StemModelPhase.READY else StemModelPhase.MISSING,
        receivedBytes = if (file.length() == expectedBytes) expectedBytes else 0,
        totalBytes = expectedBytes,
    ))
    val state = _state.asStateFlow()
    private var verified = false

    suspend fun ensureAvailable(): File = mutex.withLock {
        if (verified && file.length() == expectedBytes) return@withLock file
        _state.value = StemModelState(StemModelPhase.CHECKING, totalBytes = expectedBytes)
        var temporary: File? = null
        try {
            if (!valid(file)) {
                currentCoroutineContext().ensureActive()
                check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
                val incoming = File(file.parentFile, "${UUID.randomUUID()}.part")
                temporary = incoming
                _state.value = StemModelState(StemModelPhase.DOWNLOADING, totalBytes = expectedBytes)
                transfer(incoming) { received ->
                    check(received in 0..expectedBytes)
                    _state.value = StemModelState(StemModelPhase.DOWNLOADING, received, expectedBytes)
                }
                currentCoroutineContext().ensureActive()
                _state.value = StemModelState(StemModelPhase.VERIFYING, expectedBytes, expectedBytes)
                check(valid(incoming)) { "AI 模型校验失败" }
                check(incoming.renameTo(file)) { "AI 模型保存失败" }
            }
            verified = true
            _state.value = StemModelState(StemModelPhase.READY, expectedBytes, expectedBytes)
            file
        } catch (cancelled: CancellationException) {
            _state.value = StemModelState(StemModelPhase.MISSING, totalBytes = expectedBytes)
            throw cancelled
        } catch (error: Exception) {
            _state.value = StemModelState(StemModelPhase.FAILED, totalBytes = expectedBytes)
            throw error
        } finally {
            temporary?.delete()
        }
    }

    private suspend fun valid(candidate: File): Boolean {
        if (candidate.length() != expectedBytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        candidate.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == expectedHash
    }

    companion object {
        const val MODEL_BYTES = 29_704_436L
        private const val MODEL_SHA256 = "229ad3bb96a037e89d8ed86732d6d3675856e6a07c3e3f02896eac01ec7ee4be"
        private val MODEL_URLS = listOf(
            "https://ghfast.top/https://github.com/TRvlvr/model_repo/releases/download/all_public_uvr_models/UVR_MDXNET_1_9703.onnx",
            "https://ghproxy.net/https://github.com/TRvlvr/model_repo/releases/download/all_public_uvr_models/UVR_MDXNET_1_9703.onnx",
        )

        private suspend fun download(destination: File, progress: (Long) -> Unit) {
            var lastFailure: Exception? = null
            HttpClient(OkHttp) {
                installNekoClientHeader()
                expectSuccess = true
                install(HttpTimeout) {
                    connectTimeoutMillis = 30_000
                    socketTimeoutMillis = 60_000
                    requestTimeoutMillis = 600_000
                }
            }.use { client ->
                for ((index, url) in MODEL_URLS.withIndex()) {
                    try {
                        client.prepareGet(url).execute { response ->
                            val channel = response.bodyAsChannel()
                            destination.outputStream().buffered().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var received = 0L
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = channel.readAvailable(buffer, 0, buffer.size)
                                    if (count < 0) break
                                    if (count == 0) continue
                                    received += count
                                    check(received <= MODEL_BYTES)
                                    output.write(buffer, 0, count)
                                    progress(received)
                                }
                                check(received == MODEL_BYTES) { "国内代理返回的 AI 模型不完整" }
                            }
                        }
                        return
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        lastFailure = error
                        destination.delete()
                        Log.w("StemModelRepository", "国内代理 ${index + 1} 下载失败，尝试备用代理", error)
                    }
                }
            }
            throw lastFailure ?: IllegalStateException("没有可用的 AI 模型国内代理")
        }
    }
}
