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
    val realtime: Boolean = true,
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
        realtime = prefs.getBoolean("realtime", true),
    ))
    override val state = _state.asStateFlow()
    private val models = StemModelRepository(File(appContext.noBackupFilesDir, "audio-models/vocals-mdx-9703.onnx"))
    val modelState = models.state
    private var modelRequest: Job? = null
    private var verifiedModel: File? = null
    private val worker = MutableStateFlow<StemInferenceWorker?>(null)
    override val aiInference: StemChunkInference? get() = worker.value
    private val _streamPhase = MutableStateFlow(StemStreamPhase.WAITING)
    val streamPhase = _streamPhase.asStateFlow()

    init {
        // 清理旧版整曲分离模型，避免继续占用约 64 MB 私有空间。
        File(appContext.noBackupFilesDir, "audio-models/inst-hq3.onnx").delete()
        if (!_state.value.realtime) requestAiModel()
    }

    fun requestAiModel() {
        if (_state.value.realtime || modelRequest?.isActive == true) return
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
        if (state.enabled && !state.realtime && model != null) {
            if (worker.value == null) worker.value = StemInferenceWorker { MdxStemEngine(model) }
        } else {
            val previous = worker.value
            worker.value = null
            previous?.close()
            _streamPhase.value = StemStreamPhase.WAITING
        }
    }

    override fun reportAiPhase(inference: StemChunkInference?, phase: StemStreamPhase) {
        if (_state.value.enabled && !_state.value.realtime && worker.value === inference) {
            _streamPhase.value = phase
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

    fun setRealtime(realtime: Boolean) {
        if (realtime == _state.value.realtime) {
            if (!realtime) requestAiModel()
            return
        }
        prefs.edit().putBoolean("realtime", realtime).apply()
        _state.update { it.copy(realtime = realtime) }
        if (realtime) {
            modelRequest?.cancel()
            modelRequest = null
        }
        updateWorker()
        if (!realtime) requestAiModel()
        if (_state.value.enabled) refreshAudio()
    }

    fun setEnabled(enabled: Boolean) {
        if (_state.value.enabled == enabled) return
        prefs.edit().putBoolean("enabled", enabled).apply()
        _state.update { it.copy(enabled = enabled) }
        updateWorker()
        if (enabled && !_state.value.realtime) requestAiModel()
        refreshAudio()
    }

    fun retry() {
        if (!_state.value.enabled || _state.value.realtime) return
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
