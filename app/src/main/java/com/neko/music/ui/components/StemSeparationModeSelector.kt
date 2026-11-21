package com.neko.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.neko.music.R
import com.neko.music.service.StemModelPhase
import com.neko.music.service.StemModelState
import kotlin.math.roundToInt

/** 分离方式在设置页内直接展示，整行可选，读屏只朗读一份单选状态。 */
@Composable
internal fun StemSeparationModeSelector(
    realtime: Boolean,
    modelState: StemModelState,
    onSelect: (Boolean) -> Unit,
    onRetryDownload: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.stem_mode),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp).semantics { heading() },
        )
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StemModeOption(
                title = stringResource(R.string.stem_mode_realtime),
                description = stringResource(R.string.stem_realtime_limit),
                selected = realtime,
                onClick = { onSelect(true) },
            )
            StemModeOption(
                title = stringResource(R.string.stem_mode_quality),
                description = stringResource(R.string.stem_mode_quality_hint),
                selected = !realtime,
                onClick = { onSelect(false) },
            ) {
                if (!realtime) StemAiModelProgress(modelState, onRetryDownload)
            }
        }
    }
}

@Composable
private fun StemModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) scheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            extra()
        }
    }
}

@Composable
private fun StemAiModelProgress(state: StemModelState, onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state.phase) {
            StemModelPhase.DOWNLOADING -> {
                Text(
                    stringResource(R.string.stem_ai_download_progress, (state.progress * 100).roundToInt(),
                        state.receivedBytes / 1048576.0, state.totalBytes / 1048576.0),
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
                Text(stringResource(R.string.stem_ai_model_ready), style = MaterialTheme.typography.bodySmall, color = scheme.primary)
            }
            StemModelPhase.MISSING, StemModelPhase.FAILED -> {
                Text(
                    stringResource(if (state.phase == StemModelPhase.FAILED) R.string.stem_ai_model_failed else R.string.stem_ai_model_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.phase == StemModelPhase.FAILED) scheme.error else scheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.stem_ai_download_retry)) }
            }
        }
    }
}
