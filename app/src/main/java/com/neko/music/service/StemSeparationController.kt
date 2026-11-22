package com.neko.music.service

import android.content.Context
import com.neko.music.debug.NekoLog as Log
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

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
        } else null
}

/** 原曲始终承担播放时钟，服务端双轨只在后台准备；失败不能阻塞或替换主播放源。 */
internal class StemSeparationController(
    context: Context,
) : StemProcessingControl {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
    private val disk = StemDiskCache(chunkDirectory)
    private var currentMusicId: Int? = null
    @Volatile private var track: StemTrackCache? = null
    @Volatile private var source: StemTrackInference? = null
    private var streamJob: Job? = null
    private var prefetchJob: Job? = null
    @Volatile private var playbackFrame = 0L
    private var lastPrefetchMs = 0L
    @Volatile private var prepared = false
    @Volatile private var generation = 0L

    override val stemSource: StemTrackInference?
        get() = source.takeIf { _state.value.enabled }

    init {
        scope.launch {
            // 清理不再使用的旧版模型，磁盘遍历不放在播放器主线程。
            File(appContext.noBackupFilesDir, "audio-models").deleteRecursively()
            File(appContext.noBackupFilesDir, "ai-stem-cache").deleteRecursively()
            File(appContext.cacheDir, "separated-audio-v1").deleteRecursively()
        }
        prefs.edit().remove("realtime").apply()
    }

    fun setRoomPlaybackActive(active: Boolean) {
        _state.update { it.copy(roomPlaybackActive = active) }
    }

    fun setVocals(value: Float) {
        if (!value.isFinite()) return
        val gain = validGain(value)
        prefs.edit().putFloat("vocals", gain).apply()
        _state.update { it.copy(vocals = gain) }
    }

    fun setInstrumental(value: Float) {
        if (!value.isFinite()) return
        val gain = validGain(value)
        prefs.edit().putFloat("instrumental", gain).apply()
        _state.update { it.copy(instrumental = gain) }
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        if (_state.value.enabled == enabled) return
        prefs.edit().putBoolean("enabled", enabled).apply()
        _state.update { it.copy(enabled = enabled) }
        if (enabled) startStream() else stopStream()
        // 音频处理器始终存在，开关不需要重新 setMediaItem 或 prepare。
    }

    @Synchronized
    fun retry() {
        if (!_state.value.enabled) return
        stopStream()
        prepared = false
        startStream()
    }

    // 分离采样固定为 44.1kHz，播放时按采样率视图读取；切音质不再清空整曲分片。
    override fun setPlaybackSampleRate(sampleRate: Int) = Unit

    override fun reportStemPhase(source: StemChunkSource?, phase: StemStreamPhase) {
        if (_state.value.enabled && this.source === source) {
            _streamPhase.update { previous ->
                if (previous == StemStreamPhase.LOGIN_REQUIRED || previous == StemStreamPhase.VIP_REQUIRED ||
                    previous == StemStreamPhase.FAILED) previous else phase
            }
        }
    }

    @Synchronized
    fun onTrackChanged(identity: String, musicId: Int?) {
        if (currentMusicId != musicId || track == null) {
            stopStream()
            track?.release()
            currentMusicId = musicId
            track = musicId?.takeIf { it > 0 }?.let { StemTrackCache("music:$it") }
            source = track?.let(::StemTrackInference)
            playbackFrame = 0
            prepared = false
        }
        if (_state.value.enabled) startStream()
    }

    private fun isCurrent(current: StemTrackCache) =
        track === current && !current.released && _state.value.enabled

    /** 跳转、循环和连续播放共用磁盘预读，只保留当前附近的双轨，避免整曲常驻挤爆内存。 */
    @Synchronized
    fun onPlaybackPosition(positionMs: Long) {
        playbackFrame = positionMs.coerceAtLeast(0) * STEM_SAMPLE_RATE / 1000
        val current = track ?: return
        if (!_state.value.enabled || prefetchJob?.isActive == true) return
        val now = System.nanoTime() / 1_000_000
        if (now - lastPrefetchMs < 500) return
        lastPrefetchMs = now
        val musicId = currentMusicId ?: return
        val session = generation
        prefetchJob = scope.launch {
            current.retainAround(playbackFrame)
            disk.loadNear(musicId, playbackFrame,
                needed = { current.chunkAt(STEM_SAMPLE_RATE, it) == null },
                publish = {
                    if (session != generation || !isCurrent(current)) throw CancellationException("预读已过期")
                    current.put(STEM_SAMPLE_RATE, it)
                })
        }
    }

    @Synchronized
    private fun startStream() {
        val musicId = currentMusicId?.takeIf { it > 0 } ?: return
        val current = track ?: return
        if (prepared || streamJob?.isActive == true) return
        _statusMessage.value = null
        _streamPhase.value = StemStreamPhase.PROCESSING
        val session = generation
        streamJob = scope.launch {
            fun active() = generation == session && isCurrent(current)
            val starts = sortedSetOf<Long>()
            fun publish(chunk: StemCachedChunk) {
                if (!active()) throw CancellationException("曲目已切换")
                current.put(STEM_SAMPLE_RATE, chunk)
                synchronized(starts) { starts += chunk.startFrame }
                current.retainAround(playbackFrame)
                _processingProgress.update {
                    it.copy(completedChunks = synchronized(starts) { starts.size },
                        processingChunk = -1, fullFallback = false)
                }
            }
            fun persist(chunk: StemCachedChunk) {
                publish(chunk)
                runCatching { disk.write(musicId, chunk) }
                    .onFailure { Log.w("StemSeparation", "分片已生效，保存本地缓存失败", it) }
            }
            try {
                if (disk.restore(musicId, ::publish)) {
                    if (active()) {
                        prepared = true
                        _processingProgress.value = StemProcessingProgress(
                            completedChunks = starts.size, totalChunks = starts.size, finished = true)
                    }
                    return@launch
                }
                // SSE 重连会重放已完成分片；已有本地 PCM 的位置直接跳过。
                var done = false
                for (attempt in 0..2) {
                    try {
                        api.stream(musicId) { event ->
                            currentCoroutineContext().ensureActive()
                            if (!active()) throw CancellationException("曲目已切换")
                            when (event) {
                                is ServerStemEvent.Accepted -> _processingProgress.update {
                                    it.copy(queuePosition = event.queuePosition.coerceAtLeast(0))
                                }
                                is ServerStemEvent.Started -> _processingProgress.update {
                                    it.copy(queuePosition = 0, totalChunks = maxOf(it.totalChunks, event.totalChunks))
                                }
                                is ServerStemEvent.Chunk -> {
                                    val start = (event.startSec * STEM_SAMPLE_RATE).roundToLong()
                                    if (synchronized(starts) { start !in starts }) {
                                        _processingProgress.update { it.copy(processingChunk = event.index) }
                                        ingestChunk(musicId, event, ::persist)
                                    }
                                }
                                is ServerStemEvent.Done -> {
                                    // 缓存任务可能只发 done；补齐全曲双轨，并保持已发布的分片可播放。
                                    val count = _processingProgress.value.totalChunks
                                    if (count == 0 || synchronized(starts) { starts.size < count }) {
                                        ingestFull(musicId, event, ::persist)
                                    }
                                    done = true
                                }
                                is ServerStemEvent.Failed -> throw IllegalStateException(event.message)
                            }
                        }
                        check(done) { "服务端分离连接提前结束" }
                        break
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (http: ServerStemHttpException) {
                        throw http
                    } catch (error: Exception) {
                        if (attempt == 2) throw error
                        Log.w("StemSeparation", "分离连接中断，保留已有双轨并重连", error)
                        delay(1_000L * (attempt + 1))
                    }
                }
                if (active() && done) {
                    disk.complete(musicId, synchronized(starts) { starts.toList() })
                    prepared = true
                    _processingProgress.value = StemProcessingProgress(completedChunks = starts.size,
                        totalChunks = starts.size, finished = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (http: ServerStemHttpException) {
                if (active()) {
                    _statusMessage.value = http.message
                    _streamPhase.value = when (http.statusCode) {
                        401 -> StemStreamPhase.LOGIN_REQUIRED
                        403 -> StemStreamPhase.VIP_REQUIRED
                        else -> StemStreamPhase.FAILED
                    }
                }
            } catch (error: Exception) {
                if (active()) {
                    _statusMessage.value = error.message
                    _streamPhase.value = StemStreamPhase.FAILED
                    ServerStemApi.logFailure(error)
                }
            } catch (error: OutOfMemoryError) {
                // 避免释放已经在播放的双轨：保持原曲时钟，缺片回退到原曲。
                Log.e("StemSeparation", "分离分片内存不足，停止后台准备", error)
                if (active()) _streamPhase.value = StemStreamPhase.FALLBACK
            }
        }
    }

    private suspend fun ingestChunk(musicId: Int, event: ServerStemEvent.Chunk,
                                    publish: (StemCachedChunk) -> Unit) = coroutineScope {
        val dir = File(chunkDirectory, musicId.toString()).apply { mkdirs() }
        suspend fun load(vocal: Boolean): StemCachedSamples {
            val stem = if (vocal) "vocals" else "accompaniment"
            val file = File(dir, "$stem-${event.index}.mp3")
            ensureCachedMedia(if (vocal) event.vocalsUrl else event.accompanimentUrl, file)
            val context = currentCoroutineContext()
            context.ensureActive()
            return ServerStemDecoder.decode(file, event.startSec, event.endSec, STEM_SAMPLE_RATE) {
                context.ensureActive()
            }
        }
        val vocals = async { load(true) }
        val accompaniment = async { load(false) }
        publish(StemCachedChunk((event.startSec * STEM_SAMPLE_RATE).roundToLong(),
            vocals.await(), accompaniment.await()))
    }

    private suspend fun ingestFull(musicId: Int, event: ServerStemEvent.Done,
                                   publish: (StemCachedChunk) -> Unit) = coroutineScope {
        _processingProgress.update { it.copy(fullFallback = true) }
        val dir = File(chunkDirectory, musicId.toString()).apply { mkdirs() }
        val vocalsPending = Semaphore(2)
        val accompanimentPending = Semaphore(2)
        val pairingDeadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(2)
        val pairing = StemPairAssembler {
            try {
                publish(it)
            } finally {
                vocalsPending.release()
                accompanimentPending.release()
            }
        }
        suspend fun load(vocal: Boolean) {
            val file = File(dir, if (vocal) "full-vocals.mp3" else "full-accompaniment.mp3")
            ensureCachedMedia(if (vocal) event.vocalsUrl else event.accompanimentUrl, file)
            val context = currentCoroutineContext()
            ServerStemDecoder.decodeFullChunks(file, STEM_SAMPLE_RATE,
                StemAudioProcessor.SERVER_STEM_CHUNK_SECONDS,
                onChunk = {
                    val slots = if (vocal) vocalsPending else accompanimentPending
                    // 单路过快时最多领先两片，不能把尚未配对的整轨全部堆进内存。
                    while (!slots.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                        context.ensureActive()
                        check(System.nanoTime() < pairingDeadline) { "双轨解码配对等待超时" }
                    }
                    context.ensureActive()
                    pairing.put(it.startFrame, it.samples, vocal)
                },
                checkActive = { context.ensureActive() })
        }
        val vocals = async { load(true) }
        val accompaniment = async { load(false) }
        vocals.await()
        accompaniment.await()
    }

    private suspend fun ensureCachedMedia(url: String, destination: File) {
        if (destination.isFile && destination.length() > 0L &&
            System.currentTimeMillis() - destination.lastModified() < CACHE_TTL_MS) return
        require(url.isNotBlank()) { "服务端未返回分离媒体地址" }
        api.download(url, destination)
    }

    private fun stopStream() {
        generation++
        streamJob?.cancel()
        streamJob = null
        prefetchJob?.cancel()
        prefetchJob = null
        lastPrefetchMs = 0
        _statusMessage.value = null
        _processingProgress.value = StemProcessingProgress()
        _streamPhase.value = StemStreamPhase.WAITING
    }

    private companion object {
        const val CACHE_TTL_MS = 180L * 24 * 60 * 60 * 1000
        fun validGain(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 1f
    }
}
