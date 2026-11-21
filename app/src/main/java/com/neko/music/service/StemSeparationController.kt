package com.neko.music.service

import android.content.Context
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext

internal data class StemSeparationState(
    val enabled: Boolean = false,
    val preparing: Boolean = false,
    val downloadProgress: Float? = null,
    val trackProgress: Float? = null,
    val phase: StemPreparationPhase = StemPreparationPhase.IDLE,
    val receivedBytes: Long = 0,
    val totalBytes: Long? = null,
    val ready: Boolean = false,
    val vocals: Float = 1f,
    val instrumental: Float = 1f,
    val errorRes: Int? = null,
    val roomPlaybackActive: Boolean = false,
    val realtime: Boolean = true,
)

internal class StemSeparationController(
    context: Context,
    private val switchSource: (String) -> Unit,
) : StemProcessingControl {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = appContext.getSharedPreferences("stem_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(StemSeparationState(
        enabled = prefs.getBoolean("enabled", false),
        vocals = validGain(prefs.getFloat("vocals", 1f)),
        instrumental = validGain(prefs.getFloat("instrumental", 1f)),
        realtime = prefs.getBoolean("realtime", true),
    ))
    override val state = _state.asStateFlow()
    @Volatile override var cachedPlayback = false
        private set
    private var preparation: Job? = null
    private val workMutex = Mutex()
    @Volatile private var generation = 0
    private var originalSource: String? = null
    private var sourceKey: String? = null
    private val cacheDirectory = File(appContext.cacheDir, "separated-audio-v1")
    private val modelFile = File(appContext.noBackupFilesDir, "audio-models/inst-hq3.onnx")
    val modelDownloaded: Boolean get() = modelFile.length() == MODEL_BYTES

    /** 主线程先更新源标记，再设置播放器，防止旧任务把刚切换的歌曲覆盖。 */
    fun sourceForPlayback(source: String, identity: String = source): String {
        originalSource = source
        if (identity != sourceKey) {
            sourceKey = identity
            generation++
            preparation?.cancel()
            preparation = null
            cachedPlayback = false
            _state.update { it.copy(ready = false, preparing = false, trackProgress = null, errorRes = null, phase = StemPreparationPhase.IDLE) }
        }
        if (!_state.value.enabled) return source
        if (_state.value.realtime) {
            cachedPlayback = false
            _state.update { it.copy(ready = true, phase = StemPreparationPhase.READY) }
            return source
        }
        val cached = File(cacheDirectory, "${StemCacheFile.key(identity)}.wav")
        if (StemCacheFile.isComplete(cached)) {
            cachedPlayback = true
            cached.setLastModified(System.currentTimeMillis())
            _state.update { it.copy(ready = true, preparing = false, trackProgress = null, phase = StemPreparationPhase.READY) }
            return cached.absolutePath
        }
        if (preparation?.isActive != true) prepare(source)
        return source
    }

    fun setRoomPlaybackActive(active: Boolean) {
        // 房间状态仅供界面说明，不中断本地分离任务，也不触发换源或房间控制。
        _state.update { it.copy(roomPlaybackActive = active) }
    }

    fun setVocals(value: Float) {
        if (value.isFinite()) {
            val gain = value.coerceIn(0f, 1f)
            prefs.edit().putFloat("vocals", gain).apply()
            _state.update { it.copy(vocals = gain) }
        }
    }

    fun setInstrumental(value: Float) {
        if (value.isFinite()) {
            val gain = value.coerceIn(0f, 1f)
            prefs.edit().putFloat("instrumental", gain).apply()
            _state.update { it.copy(instrumental = gain) }
        }
    }

    fun setRealtime(realtime: Boolean) {
        if (realtime == _state.value.realtime) return
        generation++
        preparation?.cancel()
        preparation = null
        cachedPlayback = false
        prefs.edit().putBoolean("realtime", realtime).apply()
        _state.update { it.copy(realtime = realtime, ready = false, preparing = false, errorRes = null) }
        originalSource?.let { switchSource(sourceForPlayback(it, sourceKey ?: it)) }
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enabled", enabled).apply()
        if (!enabled) {
            generation++
            preparation?.cancel()
            preparation = null
            val wasActive = cachedPlayback || _state.value.realtime
            cachedPlayback = false
            _state.update { it.copy(enabled = false, ready = false, preparing = false, trackProgress = null, downloadProgress = null, errorRes = null, phase = StemPreparationPhase.IDLE) }
            if (wasActive) originalSource?.let(switchSource)
        } else {
            _state.update { it.copy(enabled = true, errorRes = null) }
            originalSource?.let { source ->
                val target = sourceForPlayback(source, sourceKey ?: source)
                if (target != source || _state.value.realtime) switchSource(target)
            }
        }
    }

    fun retry() {
        if (_state.value.enabled && !_state.value.realtime && !_state.value.preparing) originalSource?.let(::prepare)
    }

    /** 缓存读取失败只回到原曲，保留用户开启意愿。 */
    fun recoverOriginal(): Boolean {
        if (!cachedPlayback) return false
        cachedPlayback = false
        _state.update { it.copy(ready = false, errorRes = R.string.stem_processing_failed) }
        originalSource?.let(switchSource)
        return true
    }

    private fun prepare(source: String) {
        preparation?.cancel()
        val token = ++generation
        val identity = sourceKey ?: source
        _state.update { it.copy(preparing = true, trackProgress = null, downloadProgress = null, errorRes = null, phase = StemPreparationPhase.CHECKING_MODEL, receivedBytes = 0, totalBytes = null) }
        preparation = scope.launch {
            try {
                val cached = withContext(Dispatchers.IO) {
                    workMutex.withLock {
                        coroutineContext.ensureActive()
                        check(cacheDirectory.isDirectory || cacheDirectory.mkdirs())
                        // 保留最近三首完整缓存与当前准备进度，关闭开关后重开可接着处理。
                        val key = StemCacheFile.key(identity)
                        val retained = cacheDirectory.listFiles()?.filter { it.extension == "wav" }
                            ?.sortedByDescending { it.lastModified() }?.take(2)?.map { it.nameWithoutExtension }.orEmpty().toSet() + key
                        cacheDirectory.listFiles()?.filter { it.name.substringBefore('.') !in retained }?.forEach { it.delete() }
                        if (!isValidModel(modelFile)) downloadModel()
                        coroutineContext.ensureActive()
                        val target = File(cacheDirectory, "$key.wav")
                        val temporary = File(cacheDirectory, "$key.partial")
                        if (token == generation) _state.update { it.copy(phase = StemPreparationPhase.LOADING_MODEL, downloadProgress = null) }
                        MdxStemEngine(modelFile).use { engine ->
                            val parts = identity.split(':')
                            val local = if (parts.size == 3 && parts[0] == "music") {
                                parts[1].toIntOrNull()?.takeIf { it > 0 }?.let { id ->
                                    com.neko.music.data.cache.MusicCacheManager.getInstance(appContext)
                                        .getCachedMusicFile(id, parts[2])?.absolutePath
                                }
                            } else null
                            StemTrackPreparer(appContext).prepare(local ?: source, engine, temporary) { progress ->
                                if (token == generation) _state.update {
                                    it.copy(phase = progress.phase, trackProgress = progress.fraction,
                                        receivedBytes = progress.receivedBytes, totalBytes = progress.totalBytes, downloadProgress = null)
                                }
                            }
                        }
                        coroutineContext.ensureActive()
                        check(temporary.renameTo(target))
                        target
                    }
                }
                coroutineContext.ensureActive()
                if (token != generation || identity != sourceKey || !_state.value.enabled) return@launch
                cachedPlayback = true
                _state.update { it.copy(ready = true, preparing = false, trackProgress = null, downloadProgress = null, phase = StemPreparationPhase.READY) }
                switchSource(cached.absolutePath)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: OutOfMemoryError) {
                if (token != generation) return@launch
                Log.e("StemSeparation", "当前内存不足，保留开关并继续播放原曲", error)
                _state.update { it.copy(ready = false, preparing = false, trackProgress = null, downloadProgress = null, errorRes = R.string.stem_memory_unavailable) }
            } catch (error: Exception) {
                if (token != generation) return@launch
                Log.e("StemSeparation", "后台音源分离失败，保留开关并继续播放原曲", error)
                _state.update { it.copy(ready = false, preparing = false, trackProgress = null, downloadProgress = null, errorRes = R.string.stem_prepare_failed) }
            }
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
        _state.update { it.copy(phase = StemPreparationPhase.DOWNLOADING_MODEL) }
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
        fun validGain(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 1f
        const val MODEL_BYTES = 66_759_214L
        const val MODEL_SHA256 = "317554b07fe1ea5279a77f2b1520a41ea4b93432560c4ffd08792c30fddf9adc"
        const val MODEL_URL = "https://github.com/TRvlvr/model_repo/releases/download/all_public_uvr_models/UVR-MDX-NET-Inst_HQ_3.onnx"
    }
}
