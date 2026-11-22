package com.neko.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.neko.music.R
import com.neko.music.service.StemStreamPhase

@Composable
internal fun stemPreparationDescription(stream: StemStreamPhase, serverMessage: String?): String {
    if (stream in setOf(
            StemStreamPhase.FAILED,
            StemStreamPhase.LOGIN_REQUIRED,
            StemStreamPhase.VIP_REQUIRED,
        ) && !serverMessage.isNullOrBlank()) {
        return serverMessage
    }
    return stringResource(when (stream) {
        StemStreamPhase.WAITING -> R.string.stem_track_waiting
        StemStreamPhase.PROCESSING -> R.string.stem_stream_processing
        StemStreamPhase.ACTIVE -> R.string.stem_stream_active
        StemStreamPhase.FALLBACK -> R.string.stem_stream_fallback
        StemStreamPhase.FAILED,
        StemStreamPhase.LOGIN_REQUIRED,
        StemStreamPhase.VIP_REQUIRED -> R.string.stem_stream_failed
        StemStreamPhase.UNSUPPORTED -> R.string.stem_format_unsupported
    })
}
