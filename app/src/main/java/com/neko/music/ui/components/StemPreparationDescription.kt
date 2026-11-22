package com.neko.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.neko.music.R
import com.neko.music.service.StemStreamPhase

@Composable
internal fun stemPreparationDescription(stream: StemStreamPhase): String = stringResource(when (stream) {
    StemStreamPhase.WAITING -> R.string.stem_track_waiting
    StemStreamPhase.PROCESSING -> R.string.stem_stream_processing
    StemStreamPhase.ACTIVE -> R.string.stem_stream_active
    StemStreamPhase.FALLBACK -> R.string.stem_stream_fallback
    StemStreamPhase.FAILED -> R.string.stem_stream_failed
    StemStreamPhase.UNSUPPORTED -> R.string.stem_format_unsupported
})
