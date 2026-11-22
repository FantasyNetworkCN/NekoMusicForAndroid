package com.neko.music.service

import android.content.Context
import com.neko.music.debug.NekoLog as Log
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class StemSeparationState(
    val enabled: Boolean = false,
    val vocals: Float = 1f,
    val instrumental: Float = 1f,
    val roomPlaybackActive: Boolean = false,
)

internal class StemSeparationController(
    context: Context,
    private val refreshAudio: () -> Unit,
) : StemProcessingControl {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = appContext.getSharedPreferences("stem_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(StemSeparationState(
        enabled = prefs.getBoolean("enabled", false),
        vocals = validGain(prefs.getFloat("vocals", 1f)),
        instrumental = validGain(prefs.getFloat("instrumental", 1f)),
    ))
    override val state = _state.asStateFlow()
    private val _streamPhase = MutableStateFlow(StemStreamPhase.WAITING)
    val streamPhase = _streamPhase.asStateFlow()
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()
    private val api = ServerStemApi()
    private val chunkDirectory = File(appContext.cacheDir, "server-stem-chunks")
    @Volatile private var targetSampleRate = 44_100
    @Volatile private var currentIdentity: String? = null
    @Volatile private var currentMusicId: Int? = null
    @Volatile private var track: StemTrackCache? = null
    @Volatile private var source: StemTrackInference? = null
    private var streamJob: Job? = null

    override val stemSource: StemTrackInference?
        get() = source.takeIf { _state.value.enabled }

    init {
        // 彻底移除旧版本地 AI 模型和推理缓存。
        File(appContext.noBackupFilesDir, "audio-models").deleteRecursively()
        File(appContext.noBackupFilesDir, "ai-stem-cache").deleteRecursively()
        File(appContext.cacheDir, "separated-audio-v1").deleteRecursively()
        prefs.edit().remove("realtime").apply()
    }

    fun setRoomPlaybackActive(active: Boolean) {
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

    fun setEnabled(enabled: Boolean) {
        if (_state.value.enabled == enabled) return
        prefs.edit().putBoolean("enabled", enabled).apply()
        _state.update { it.copy(enabled = enabled) }
        if (enabled) {
            startStream()
        } else {
            stopStream()
            replaceTrack()
        }
        refreshAudio()
    }

    fun retry() {
        if (!_state.value.enabled) return
        stopStream()
        replaceTrack()
        startStream()
        refreshAudio()
    }

    /** 播放器准备音频管线前通知当前采样率；变化时重新准备分片。 */
    override fun setPlaybackSampleRate(sampleRate: Int) {
        if (sampleRate <= 0 || targetSampleRate == sampleRate) return
        targetSampleRate = sampleRate
        if (currentMusicId != null) {
            stopStream()
            replaceTrack()
            startStream()
        }
    }

    override fun reportStemPhase(source: StemChunkSource?, phase: StemStreamPhase) {
        if (_state.value.enabled && this.source === source) {
            // 登录 / VIP 是服务端鉴权终态，不能被播放线程随后的 WAITING / FALLBACK 覆盖。
            if (_streamPhase.value in setOf(
                    StemStreamPhase.LOGIN_REQUIRED,
                    StemStreamPhase.VIP_REQUIRED,
                ) && phase != _streamPhase.value) {
                return
            }
            if (_streamPhase.value != phase) {
                Log.d("StemSeparation", "服务端分离状态: ${_streamPhase.value} -> $phase")
            }
            _streamPhase.value = phase
        }
    }

    /** 切歌时取消旧 SSE 与解码任务，旧分片只保留到新曲目状态建立前。 */
    fun onTrackChanged(identity: String, musicId: Int?) {
        val changed = currentIdentity != identity || currentMusicId != musicId
        currentIdentity = identity
        currentMusicId = musicId
        if (changed) {
            stopStream()
            replaceTrack()
        }
        if (_state.value.enabled && musicId != null && musicId > 0) startStream()
    }

    private fun replaceTrack() {
        track?.release()
        _statusMessage.value = null
        val identity = currentIdentity
        if (identity == null) {
            track = null
            source = null
            return
        }
        val next = StemTrackCache(identity)
        next.ready.complete(Unit)
        track = next
        source = StemTrackInference(next)
        _streamPhase.value = StemStreamPhase.WAITING
    }

    private fun startStream() {
        val musicId = currentMusicId?.takeIf { it > 0 } ?: return
        val currentTrack = track ?: return
        if (streamJob?.isActive == true) return
        _statusMessage.value = null
        _streamPhase.value = StemStreamPhase.PROCESSING
        streamJob = scope.launch(Dispatchers.IO) {
            try {
                var done = false
                api.stream(musicId) { event ->
                    when (event) {
                        is ServerStemEvent.Accepted -> {
                            _streamPhase.value = StemStreamPhase.PROCESSING
                        }
                        is ServerStemEvent.Started -> Unit
                        is ServerStemEvent.Chunk -> {
                            if (track === currentTrack && !currentTrack.released) {
                                try {
                                    ingestChunk(musicId, currentTrack, event)
                                    if (currentTrack.chunkCount > 0 &&
                                        _streamPhase.value != StemStreamPhase.ACTIVE) {
                                        _streamPhase.value = StemStreamPhase.ACTIVE
                                    }
                                } catch (error: Exception) {
                                    _streamPhase.value = StemStreamPhase.FALLBACK
                                    Log.w("StemSeparation", "服务端分片 ${event.index} 处理失败，继续接收后续分片", error)
                                }
                            }
                        }
                        is ServerStemEvent.Done -> {
                            done = true
                            if (track === currentTrack && !currentTrack.released &&
                                currentTrack.chunkCount == 0 && event.accompanimentUrl.isNotBlank()) {
                                ingestFull(musicId, currentTrack, event.accompanimentUrl)
                            }
                        }
                        is ServerStemEvent.Failed -> throw IllegalStateException(event.message)
                    }
                }
                if (!done && track === currentTrack && !currentTrack.released) {
                    throw IllegalStateException("服务端分离连接提前结束")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (outOfMemory: OutOfMemoryError) {
                if (track === currentTrack) {
                    currentTrack.release()
                    track = null
                    source = null
                    _streamPhase.value = StemStreamPhase.FALLBACK
                }
                Log.e("StemSeparation", "处理服务端分离数据时内存不足，已释放当前曲目分片", outOfMemory)
            } catch (http: ServerStemHttpException) {
                if (track === currentTrack && _state.value.enabled) {
                    _statusMessage.value = http.message
                    _streamPhase.value = when (http.statusCode) {
                        401 -> StemStreamPhase.LOGIN_REQUIRED
                        403 -> StemStreamPhase.VIP_REQUIRED
                        else -> StemStreamPhase.FAILED
                    }
                }
            } catch (error: Exception) {
                if (track === currentTrack && _state.value.enabled) {
                    _statusMessage.value = error.message
                    _streamPhase.value = StemStreamPhase.FAILED
                    ServerStemApi.logFailure(error)
                }
            }
        }
    }

    private suspend fun ingestChunk(
        musicId: Int,
        currentTrack: StemTrackCache,
        event: ServerStemEvent.Chunk,
    ) {
        val directory = File(chunkDirectory, musicId.toString()).apply { mkdirs() }
        val file = File(directory, "accompaniment-${event.index}.mp3")
        if (!file.isFile || file.length() == 0L) {
            api.download(event.accompanimentUrl, file)
        }
        try {
            val samples = ServerStemDecoder.decode(
                file,
                event.startSec,
                event.endSec,
                targetSampleRate,
            )
            val startFrame = (event.startSec * targetSampleRate).toLong().coerceAtLeast(0L)
            currentTrack.put(targetSampleRate, StemCachedChunk(startFrame, samples))
        } finally {
            runCatching { file.delete() }
        }
    }

    private suspend fun ingestFull(
        musicId: Int,
        currentTrack: StemTrackCache,
        accompanimentUrl: String,
    ) {
        val directory = File(chunkDirectory, musicId.toString()).apply { mkdirs() }
        val file = File(directory, "full-accompaniment.mp3")
        try {
            api.download(accompanimentUrl, file)
            val chunks = ServerStemDecoder.decodeFullChunks(
                file,
                targetSampleRate,
                StemAudioProcessor.SERVER_STEM_CHUNK_SECONDS,
            )
            chunks.forEach { currentTrack.put(targetSampleRate, it) }
            if (chunks.isNotEmpty()) _streamPhase.value = StemStreamPhase.ACTIVE
        } finally {
            runCatching { file.delete() }
        }
    }

    private fun stopStream() {
        streamJob?.cancel()
        streamJob = null
        _statusMessage.value = null
        _streamPhase.value = StemStreamPhase.WAITING
    }

    private companion object {
        fun validGain(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 1f
    }
}
