package com.neko.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.neko.music.R
import com.neko.music.service.StemModelPhase
import com.neko.music.service.StemModelState
import com.neko.music.service.StemStreamPhase
import kotlin.math.roundToInt

@Composable
internal fun stemPreparationDescription(
    model: StemModelState,
    stream: StemStreamPhase,
): String {
    return when (model.phase) {
        StemModelPhase.MISSING -> stringResource(R.string.stem_ai_model_waiting)
        StemModelPhase.CHECKING, StemModelPhase.VERIFYING -> stringResource(R.string.stem_ai_model_verifying)
        StemModelPhase.DOWNLOADING -> stringResource(
            R.string.stem_ai_download_progress,
            (model.progress * 100).roundToInt(), model.receivedBytes / 1048576.0, model.totalBytes / 1048576.0,
        )
        StemModelPhase.FAILED -> stringResource(R.string.stem_ai_model_failed)
        StemModelPhase.READY -> stringResource(when (stream) {
            StemStreamPhase.WAITING -> R.string.stem_track_waiting
            StemStreamPhase.PROCESSING -> R.string.stem_stream_processing
            StemStreamPhase.ACTIVE -> R.string.stem_stream_active
            StemStreamPhase.FALLBACK -> R.string.stem_stream_fallback
            StemStreamPhase.FAILED -> R.string.stem_stream_failed
            StemStreamPhase.UNSUPPORTED -> R.string.stem_format_unsupported
        })
    }
}
