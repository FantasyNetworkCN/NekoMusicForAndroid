package com.neko.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neko.music.R
import com.neko.music.service.StemModelPhase
import com.neko.music.service.StemModelState
import kotlin.math.roundToInt

/** 设置页展示 AI 分离说明与模型下载状态。 */
@Composable
internal fun StemAiModelSection(modelState: StemModelState, onRetryDownload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.stem_ai_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StemAiModelProgress(modelState, onRetryDownload)
    }
}

@Composable
private fun StemAiModelProgress(state: StemModelState, onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    when (state.phase) {
        StemModelPhase.DOWNLOADING -> {
            Text(
                stringResource(
                    R.string.stem_ai_download_progress,
                    (state.progress * 100).roundToInt(),
                    state.receivedBytes / 1048576.0,
                    state.totalBytes / 1048576.0,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.primary,
            )
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        }
        StemModelPhase.CHECKING, StemModelPhase.VERIFYING -> {
            Text(stringResource(R.string.stem_ai_model_verifying), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        StemModelPhase.READY -> {
            Text(
                stringResource(R.string.stem_ai_model_ready),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.primary,
            )
        }
        StemModelPhase.MISSING, StemModelPhase.FAILED -> {
            Text(
                stringResource(
                    if (state.phase == StemModelPhase.FAILED) R.string.stem_ai_model_failed else R.string.stem_ai_model_missing,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.phase == StemModelPhase.FAILED) scheme.error else scheme.onSurfaceVariant,
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.stem_ai_download_retry)) }
        }
    }
}
