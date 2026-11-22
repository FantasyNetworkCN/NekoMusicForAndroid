package com.neko.music.service

import android.content.Context
import com.neko.music.debug.NekoLog as Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

internal data class StemProcessingProgress(
    val queuePosition: Long = 0L,
    val completedChunks: Int = 0,
    val totalChunks: Int = 0,
    val processingChunk: Int = -1,
    val fullFallback: Boolean = false,
    val finished: Boolean = false,
) {
    val fraction: Float?
        get() = if (totalChunks > 0) {
            (completedChunks.toFloat() / totalChunks).coerceIn(0f, 1f)
        } else {
            null
        }
}

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
    private val _processingProgress = MutableStateFlow(StemProcessingProgress())
    val processingProgress = _processingProgress.asStateFlow()
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
        _processingProgress.value = StemProcessingProgress()
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
        _processingProgress.value = StemProcessingProgress()
        _streamPhase.value = StemStreamPhase.PROCESSING
        streamJob = scope.launch(Dispatchers.IO) {
            try {
                var done = false
                api.stream(musicId) { event ->
                    when (event) {
                        is ServerStemEvent.Accepted -> {
                            _streamPhase.value = StemStreamPhase.PROCESSING
                            _processingProgress.update {
                                it.copy(queuePosition = event.queuePosition.coerceAtLeast(0L))
                            }
                        }
                        is ServerStemEvent.Started -> {
                            _processingProgress.update {
                                it.copy(
                                    queuePosition = 0L,
                                    completedChunks = 0,
                                    totalChunks = event.totalChunks.coerceAtLeast(0),
                                    processingChunk = -1,
                                    fullFallback = false,
                                    finished = false,
                                )
                            }
                        }
                        is ServerStemEvent.Chunk -> {
                            if (track === currentTrack && !currentTrack.released) {
                                _processingProgress.update {
                                    it.copy(processingChunk = event.index)
                                }
                                try {
                                    ingestChunk(musicId, currentTrack, event)
                                    _processingProgress.update {
                                        it.copy(
                                            completedChunks = maxOf(
                                                it.completedChunks,
                                                event.index + 1,
                                            ),
                                            processingChunk = -1,
                                        )
                                    }
                                    if (currentTrack.chunkCount > 0 &&
                                        _streamPhase.value != StemStreamPhase.ACTIVE) {
                                        _streamPhase.value = StemStreamPhase.ACTIVE
                                    }
                                } catch (error: Exception) {
                                    _processingProgress.update { it.copy(processingChunk = -1) }
                                    _streamPhase.value = StemStreamPhase.FALLBACK
                                    Log.w("StemSeparation", "服务端分片 ${event.index} 处理失败，继续接收后续分片", error)
                                }
                            }
                        }
                        is ServerStemEvent.Done -> {
                            done = true
                            _processingProgress.update {
                                it.copy(
                                    queuePosition = 0L,
                                    completedChunks = if (it.totalChunks > 0) it.totalChunks else it.completedChunks,
                                    processingChunk = -1,
                                    finished = true,
                                )
                            }
                            if (track === currentTrack && !currentTrack.released &&
                                currentTrack.chunkCount == 0 &&
                                event.vocalsUrl.isNotBlank() &&
                                event.accompanimentUrl.isNotBlank()) {
                                ingestFull(
                                    musicId,
                                    currentTrack,
                                    event.vocalsUrl,
                                    event.accompanimentUrl,
                                )
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
                    _processingProgress.value = StemProcessingProgress()
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
        _processingProgress.update {
            it.copy(
                queuePosition = 0L,
                completedChunks = 0,
                totalChunks = 0,
                processingChunk = -1,
                fullFallback = true,
                finished = false,
            )
        }
        val directory = File(chunkDirectory, musicId.toString()).apply { mkdirs() }
        val vocalsFile = File(directory, "vocals-${event.index}.mp3")
        val accompanimentFile = File(directory, "accompaniment-${event.index}.mp3")
        if (!vocalsFile.isFile || vocalsFile.length() == 0L) {
            api.download(event.vocalsUrl, vocalsFile)
        }
        if (!accompanimentFile.isFile || accompanimentFile.length() == 0L) {
            api.download(event.accompanimentUrl, accompanimentFile)
        }
        try {
            val vocals = ServerStemDecoder.decode(
                vocalsFile,
                event.startSec,
                event.endSec,
                targetSampleRate,
            )
            val accompaniment = ServerStemDecoder.decode(
                accompanimentFile,
                event.startSec,
                event.endSec,
                targetSampleRate,
            )
            val startFrame = (event.startSec * targetSampleRate).toLong().coerceAtLeast(0L)
            currentTrack.put(
                targetSampleRate,
                StemCachedChunk(startFrame, vocals, accompaniment),
            )
        } finally {
            runCatching { vocalsFile.delete() }
            runCatching { accompanimentFile.delete() }
        }
    }

    private suspend fun ingestFull(
        musicId: Int,
        currentTrack: StemTrackCache,
        vocalsUrl: String,
        accompanimentUrl: String,
    ) {
        val directory = File(chunkDirectory, musicId.toString()).apply { mkdirs() }
        val vocalsFile = File(directory, "full-vocals.mp3")
        val accompanimentFile = File(directory, "full-accompaniment.mp3")
        try {
            val vocalsByStart = ConcurrentHashMap<Long, StemCachedSamples>()
            val accompanimentByStart = ConcurrentHashMap<Long, StemCachedSamples>()
            val completed = AtomicInteger(0)
            fun pairAvailableChunks(startFrame: Long) {
                val vocals = vocalsByStart.remove(startFrame) ?: return
                val accompaniment = accompanimentByStart.remove(startFrame) ?: return
                currentTrack.put(
                    targetSampleRate,
                    StemCachedChunk(startFrame, vocals, accompaniment),
                )
                val completedCount = completed.incrementAndGet()
                _processingProgress.update {
                    it.copy(
                        completedChunks = completedCount,
                        fullFallback = false,
                        finished = false,
                    )
                }
                _streamPhase.value = StemStreamPhase.ACTIVE
            }

            coroutineScope {
                val vocalsJob = async(Dispatchers.IO) {
                    api.download(vocalsUrl, vocalsFile)
                    ServerStemDecoder.decodeFullChunks(
                        vocalsFile,
                        targetSampleRate,
                        StemAudioProcessor.SERVER_STEM_CHUNK_SECONDS,
                    ) { chunk ->
                        vocalsByStart[chunk.startFrame] = chunk.samples
                        pairAvailableChunks(chunk.startFrame)
                    }
                }
                val accompanimentJob = async(Dispatchers.IO) {
                    api.download(accompanimentUrl, accompanimentFile)
                    ServerStemDecoder.decodeFullChunks(
                        accompanimentFile,
                        targetSampleRate,
                        StemAudioProcessor.SERVER_STEM_CHUNK_SECONDS,
                    ) { chunk ->
                        accompanimentByStart[chunk.startFrame] = chunk.samples
                        pairAvailableChunks(chunk.startFrame)
                    }
                }
                vocalsJob.await()
                accompanimentJob.await()
            }
            val completedCount = completed.get()
            if (completedCount > 0) {
                _processingProgress.value = StemProcessingProgress(
                    completedChunks = completedCount,
                    totalChunks = completedCount,
                    finished = true,
                )
                _streamPhase.value = StemStreamPhase.ACTIVE
            }
        } finally {
            runCatching { vocalsFile.delete() }
            runCatching { accompanimentFile.delete() }
        }
    }

    private fun stopStream() {
        streamJob?.cancel()
        streamJob = null
        _statusMessage.value = null
        _processingProgress.value = StemProcessingProgress()
        _streamPhase.value = StemStreamPhase.WAITING
    }

    private companion object {
        fun validGain(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 1f
    }
}
