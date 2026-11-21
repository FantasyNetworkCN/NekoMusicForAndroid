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
import kotlinx.coroutines.withContext

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
    private val models = StemModelRepository(File(appContext.noBackupFilesDir, "audio-models/vocals-mdx-9703.onnx"))
    val modelState = models.state
    private var modelRequest: Job? = null
    private var verifiedModel: File? = null
    private val chunkCache = StemChunkCache(File(appContext.noBackupFilesDir, "ai-stem-cache"))
    private var trackCache: StemTrackCache? = null
    @Volatile private var trackInference: StemTrackInference? = null
    private var prefetchJob: Job? = null
    private val worker = MutableStateFlow<StemInferenceWorker?>(null)
    override val aiInference: StemChunkInference?
        get() = trackInference.takeIf { _state.value.enabled }
    private val _streamPhase = MutableStateFlow(StemStreamPhase.WAITING)
    val streamPhase = _streamPhase.asStateFlow()

    init {
        // 清理旧版整曲分离模型，避免继续占用约 64 MB 私有空间。
        File(appContext.noBackupFilesDir, "audio-models/inst-hq3.onnx").delete()
        // 无 AI 方案已移除，清理旧版方式偏好。
        prefs.edit().remove("realtime").apply()
        if (_state.value.enabled) requestAiModel()
    }

    fun requestAiModel() {
        if (modelRequest?.isActive == true) return
        if (verifiedModel?.isFile == true) {
            updateWorker()
            return
        }
        modelRequest = scope.launch {
            try {
                verifiedModel = withContext(Dispatchers.IO) { models.ensureAvailable() }
                updateWorker()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("StemSeparation", "AI 模型下载或校验失败，可在设置页重试", error)
            }
        }
    }

    private fun updateWorker() {
        val state = _state.value
        val model = verifiedModel
        if (state.enabled && model != null) {
            if (worker.value == null) {
                // 已有整曲缓存无需创建推理会话，缺片首次提交时才加载模型。
                worker.value = StemInferenceWorker(
                    { preference -> MdxStemEngine(model, preference) },
                    chunkCache,
                )
            }
        } else {
            val previous = worker.value
            worker.value = null
            previous?.close()
            _streamPhase.value = StemStreamPhase.WAITING
        }
    }

    override fun reportAiPhase(inference: StemChunkInference?, phase: StemStreamPhase) {
        if (_state.value.enabled && aiInference === inference) {
            if (_streamPhase.value != phase) {
                Log.d("StemSeparation", "AI 分片状态: ${_streamPhase.value} -> $phase")
            }
            _streamPhase.value = phase
        }
    }

    /** 在设置媒体源之前立即启动整曲缓存准备；切歌释放上一首的全部内存引用。 */
    fun onTrackChanged(identity: String) {
        if (trackCache?.identity != identity) {
            prefetchJob?.cancel()
            prefetchJob = null
            trackCache?.release()
            val track = StemTrackCache(identity)
            trackCache = track
            trackInference = StemTrackInference(track) { worker.value }
        }
        startTrackPrefetch()
    }

    private fun startTrackPrefetch() {
        val track = trackCache ?: return
        if (!_state.value.enabled || track.ready.isDone || prefetchJob?.isActive == true) return
        Log.d("StemSeparation", "起播准备：开始读取整曲已有分离缓存，曲目=${track.identity}")
        prefetchJob = scope.launch(Dispatchers.IO) {
            val started = System.nanoTime()
            try {
                chunkCache.prepareTrack(track)
            } catch (error: Exception) {
                Log.w("StemSeparation", "整曲缓存读取失败，缺失范围将在后台补全", error)
            } finally {
                track.ready.complete(Unit)
                if (!track.released) {
                    val elapsedMs = (System.nanoTime() - started) / 1_000_000
                    Log.d("StemSeparation", "起播准备完成：整曲缓存已读取 ${track.chunkCount} 片，耗时=${elapsedMs}ms")
                }
            }
        }
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

    fun setEnabled(enabled: Boolean) {
        if (_state.value.enabled == enabled) return
        prefs.edit().putBoolean("enabled", enabled).apply()
        _state.update { it.copy(enabled = enabled) }
        updateWorker()
        if (enabled) requestAiModel()
        if (enabled) startTrackPrefetch()
        refreshAudio()
    }

    fun retry() {
        if (!_state.value.enabled) return
        val previous = worker.value
        worker.value = null
        previous?.close()
        _streamPhase.value = StemStreamPhase.WAITING
        requestAiModel()
        refreshAudio()
    }

    private companion object {
        fun validGain(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 1f
    }
}
