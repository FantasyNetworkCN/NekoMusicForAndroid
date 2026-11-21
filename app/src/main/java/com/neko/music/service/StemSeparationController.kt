package com.neko.music.service

import android.content.Context
import android.widget.Toast
import com.neko.music.R
import com.neko.music.debug.NekoLog as Log
import com.neko.music.util.installNekoClientHeader
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext

internal data class StemSeparationState(
    val enabled: Boolean = false,
    val preparing: Boolean = false,
    val downloadProgress: Float? = null,
    val vocals: Float = 1f,
    val instrumental: Float = 1f,
    val errorRes: Int? = null,
    val roomPlaybackActive: Boolean = false,
)

internal interface StemProcessingControl {
    val engine: StemInference?
    val state: StateFlow<StemSeparationState>
    val playbackSpeed: Float get() = 1f
    fun fail(expectedEngine: StemInference, messageRes: Int, error: Exception? = null)
}

internal class StemSeparationController(
    context: Context,
    private val restartAudio: () -> Unit,
) : StemProcessingControl {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(StemSeparationState())
    override val state = _state.asStateFlow()
    @Volatile override var engine: StemInference? = null
        private set
    @Volatile override var playbackSpeed: Float = 1f
    private var preparation: Job? = null
    private val modelFile = File(appContext.noBackupFilesDir, "audio-models/inst-hq3.onnx")
    val modelDownloaded: Boolean get() = modelFile.length() == MODEL_BYTES

    fun setRoomPlaybackActive(active: Boolean) {
        if (active) setEnabled(false)
        _state.update { it.copy(roomPlaybackActive = active) }
    }

    fun setVocals(value: Float) {
        if (value.isFinite()) _state.update { it.copy(vocals = value.coerceIn(0f, 1f)) }
    }

    fun setInstrumental(value: Float) {
        if (value.isFinite()) _state.update { it.copy(instrumental = value.coerceIn(0f, 1f)) }
    }

    fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            preparation?.cancel()
            preparation = null
            val oldEngine = engine
            engine = null
            val wasEnabled = _state.value.enabled
            _state.update { it.copy(enabled = false, preparing = false, downloadProgress = null, errorRes = null) }
            if (wasEnabled) restartAudio()
            if (oldEngine != null) scope.launch(Dispatchers.IO) { oldEngine.close() }
            return
        }
        if (_state.value.enabled || _state.value.preparing || _state.value.roomPlaybackActive) return
        _state.update { it.copy(preparing = true, errorRes = null) }
        preparation = scope.launch {
            var loaded: MdxStemEngine? = null
            try {
                withContext(Dispatchers.IO) {
                    if (!isValidModel(modelFile)) downloadModel()
                    coroutineContext.ensureActive()
                    loaded = MdxStemEngine(modelFile)
                }
                coroutineContext.ensureActive()
                engine = loaded
                loaded = null
                _state.update { it.copy(enabled = true, preparing = false, downloadProgress = null) }
                restartAudio()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("StemSeparation", "准备音源分离失败", error)
                _state.update {
                    it.copy(enabled = false, preparing = false, downloadProgress = null, errorRes = R.string.stem_prepare_failed)
                }
            } finally {
                loaded?.let { unused -> scope.launch(Dispatchers.IO) { unused.close() } }
            }
        }
    }

    override fun fail(expectedEngine: StemInference, messageRes: Int, error: Exception?) {
        scope.launch {
            if (engine !== expectedEngine) return@launch
            Log.w("StemSeparation", "音源分离已停止，恢复原曲", error)
            setEnabled(false)
            _state.update { it.copy(errorRes = messageRes) }
            Toast.makeText(appContext, messageRes, Toast.LENGTH_LONG).show()
        }
    }

    private fun isValidModel(file: File): Boolean {
        if (file.length() != MODEL_BYTES) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == MODEL_SHA256
    }

    private suspend fun downloadModel() {
        check(modelFile.parentFile!!.isDirectory || modelFile.parentFile!!.mkdirs())
        val temporary = File(modelFile.parentFile, "${UUID.randomUUID()}.part")
        try {
            HttpClient(OkHttp) {
                installNekoClientHeader()
                expectSuccess = true
                install(HttpTimeout) {
                    connectTimeoutMillis = 30_000
                    socketTimeoutMillis = 60_000
                    requestTimeoutMillis = 600_000
                }
            }.use { client ->
                client.prepareGet(MODEL_URL).execute { response ->
                    val channel = response.bodyAsChannel()
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var received = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = channel.readAvailable(buffer, 0, buffer.size)
                            if (count < 0) break
                            if (count == 0) continue
                            received += count
                            check(received <= MODEL_BYTES)
                            output.write(buffer, 0, count)
                            _state.update { it.copy(downloadProgress = received.toFloat() / MODEL_BYTES) }
                        }
                    }
                }
            }
            check(isValidModel(temporary))
            coroutineContext.ensureActive()
            check(temporary.renameTo(modelFile))
        } finally {
            temporary.delete()
        }
    }

    private companion object {
        const val MODEL_BYTES = 66_759_214L
        const val MODEL_SHA256 = "317554b07fe1ea5279a77f2b1520a41ea4b93432560c4ffd08792c30fddf9adc"
        const val MODEL_URL = "https://github.com/TRvlvr/model_repo/releases/download/all_public_uvr_models/UVR-MDX-NET-Inst_HQ_3.onnx"
    }
}
