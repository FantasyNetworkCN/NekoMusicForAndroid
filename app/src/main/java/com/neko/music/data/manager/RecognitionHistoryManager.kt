package com.neko.music.data.manager

import android.content.Context
import com.neko.music.data.api.MusicApi
import com.neko.music.data.model.Music
import com.neko.music.debug.NekoLog as Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RecognitionHistoryEntry(
    val music: Music,
    val confidence: Double,
    val recognizedAt: Long,
) {
    fun toMatch() = MusicApi.RecognitionMatch(music, confidence, 0.0, 0.0)
}

internal fun mergeRecognitionHistory(
    history: List<RecognitionHistoryEntry>,
    entry: RecognitionHistoryEntry,
): List<RecognitionHistoryEntry> =
    (listOf(entry) + history.filterNot { it.music.id == entry.music.id })
        .sortedByDescending { it.recognizedAt }
        .take(100)

/** 前台和后台共用同一个历史列表，只保存成功识别的歌曲。 */
class RecognitionHistoryManager private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("recognition_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableEntries = MutableStateFlow(
        runCatching {
            json.decodeFromString<List<RecognitionHistoryEntry>>(prefs.getString("entries", "[]").orEmpty())
        }.onFailure { Log.w("RecognitionHistory", "读取识曲历史失败", it) }.getOrDefault(emptyList()),
    )
    val entries = mutableEntries.asStateFlow()

    @Synchronized
    fun record(match: MusicApi.RecognitionMatch) {
        if (match.music.id <= 0) return
        val confidence = match.confidence.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.0
        val entry = RecognitionHistoryEntry(match.music, confidence, System.currentTimeMillis())
        val updated = mergeRecognitionHistory(mutableEntries.value, entry)
        runCatching {
            prefs.edit().putString("entries", json.encodeToString(updated)).apply()
        }.onFailure { Log.w("RecognitionHistory", "保存识曲历史失败", it) }
        mutableEntries.value = updated
    }

    companion object {
        @Volatile private var instance: RecognitionHistoryManager? = null

        fun getInstance(context: Context): RecognitionHistoryManager =
            instance ?: synchronized(this) {
                instance ?: RecognitionHistoryManager(context).also { instance = it }
            }
    }
}
