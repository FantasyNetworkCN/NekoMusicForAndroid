package com.neko.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.neko.music.R
import com.neko.music.service.StemPreparationPhase
import com.neko.music.service.StemSeparationState
import kotlin.math.roundToInt

@Composable
internal fun stemPreparationDescription(state: StemSeparationState): String {
    state.errorRes?.let { return stringResource(it) }
    if (state.realtime) return stringResource(R.string.stem_realtime_limit)
    return when (state.phase) {
        StemPreparationPhase.CHECKING_MODEL -> stringResource(R.string.stem_checking_model)
        StemPreparationPhase.DOWNLOADING_MODEL -> stringResource(R.string.stem_downloading, ((state.downloadProgress ?: 0f) * 100).roundToInt())
        StemPreparationPhase.LOADING_MODEL -> stringResource(R.string.stem_loading_model)
        StemPreparationPhase.READING_TRACK -> stringResource(R.string.stem_reading_local)
        StemPreparationPhase.DOWNLOADING_TRACK -> {
            val received = (state.receivedBytes / 1024).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val total = state.totalBytes
            if (total != null) stringResource(R.string.stem_downloading_track, received, (total / 1024).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            else stringResource(R.string.stem_downloading_track_unknown, received)
        }
        StemPreparationPhase.DECODING -> stringResource(R.string.stem_decoding_track)
        StemPreparationPhase.SEPARATING -> stringResource(R.string.stem_track_preparing, ((state.trackProgress ?: 0f) * 100).roundToInt())
        StemPreparationPhase.READY -> stringResource(R.string.stem_cache_ready)
        StemPreparationPhase.IDLE -> stringResource(R.string.stem_separation_hint)
    }
}
