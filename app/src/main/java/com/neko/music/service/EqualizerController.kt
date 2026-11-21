package com.neko.music.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EqualizerState(
    val enabled: Boolean = false,
    val preampDb: Float = 0f,
    val bandGainsDb: List<Float> = List(EqualizerFrequencies.values.size) { 0f },
    val preset: String = EqualizerPreset.FLAT,
)

object EqualizerFrequencies {
    val values = listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)
    const val MIN_GAIN_DB = -12f
    const val MAX_GAIN_DB = 12f
    const val MIN_PREAMP_DB = -12f
    const val MAX_PREAMP_DB = 12f
}

object EqualizerPreset {
    const val FLAT = "flat"
    const val POP = "pop"
    const val ROCK = "rock"
    const val JAZZ = "jazz"
    const val CLASSICAL = "classical"
    const val VOCAL = "vocal"
    const val BASS = "bass"
    const val TREBLE = "treble"
    const val CUSTOM = "custom"

    val all = listOf(FLAT, POP, ROCK, JAZZ, CLASSICAL, VOCAL, BASS, TREBLE)
    val gains: Map<String, List<Float>> = mapOf(
        FLAT to List(10) { 0f },
        POP to listOf(-1f, 1f, 3f, 4f, 2f, 0f, -1f, -1f, 1f, 2f),
        ROCK to listOf(5f, 4f, 2f, -1f, -2f, 0f, 2f, 4f, 5f, 5f),
        JAZZ to listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f),
        CLASSICAL to listOf(4f, 3f, 2f, 0f, -1f, -1f, 0f, 2f, 3f, 4f),
        VOCAL to listOf(-3f, -2f, 0f, 3f, 5f, 5f, 3f, 1f, -1f, -2f),
        BASS to listOf(7f, 6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f),
        TREBLE to listOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 6f, 7f),
    )
}

internal interface EqualizerProcessingControl {
    val state: StateFlow<EqualizerState>
}

class EqualizerController(
    context: Context,
) : EqualizerProcessingControl {
    private val prefs = context.applicationContext.getSharedPreferences("equalizer_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(loadState())
    override val state = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        if (_state.value.enabled == enabled) return
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _state.update { it.copy(enabled = enabled) }
    }

    fun setPreamp(value: Float) {
        _state.update { it.copy(preampDb = value.coerceIn(EqualizerFrequencies.MIN_PREAMP_DB, EqualizerFrequencies.MAX_PREAMP_DB)) }
    }

    fun setBandGain(index: Int, value: Float) {
        if (index !in EqualizerFrequencies.values.indices) return
        _state.update {
            val gains = it.bandGainsDb.toMutableList()
            gains[index] = value.coerceIn(EqualizerFrequencies.MIN_GAIN_DB, EqualizerFrequencies.MAX_GAIN_DB)
            it.copy(bandGainsDb = gains, preset = EqualizerPreset.CUSTOM)
        }
    }

    fun setPreset(preset: String) {
        val gains = EqualizerPreset.gains[preset] ?: return
        _state.update { it.copy(bandGainsDb = gains, preset = preset) }
        persist()
    }

    fun reset() {
        _state.update { EqualizerState(enabled = it.enabled) }
        persist()
    }

    fun persist() {
        val state = _state.value
        prefs.edit()
            .putBoolean(KEY_ENABLED, state.enabled)
            .putFloat(KEY_PREAMP, state.preampDb)
            .putString(KEY_BANDS, state.bandGainsDb.joinToString(","))
            .putString(KEY_PRESET, state.preset)
            .apply()
    }

    private fun loadState(): EqualizerState {
        val gains = prefs.getString(KEY_BANDS, null)
            ?.split(',')
            ?.mapNotNull(String::toFloatOrNull)
            ?.takeIf { it.size == EqualizerFrequencies.values.size }
            ?.map { it.coerceIn(EqualizerFrequencies.MIN_GAIN_DB, EqualizerFrequencies.MAX_GAIN_DB) }
            ?: EqualizerPreset.gains.getValue(EqualizerPreset.FLAT)
        return EqualizerState(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            preampDb = prefs.getFloat(KEY_PREAMP, 0f)
                .coerceIn(EqualizerFrequencies.MIN_PREAMP_DB, EqualizerFrequencies.MAX_PREAMP_DB),
            bandGainsDb = gains,
            preset = prefs.getString(KEY_PRESET, EqualizerPreset.FLAT) ?: EqualizerPreset.FLAT,
        )
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_PREAMP = "preamp"
        const val KEY_BANDS = "bands"
        const val KEY_PRESET = "preset"
    }
}
