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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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

/** 分离方式在设置页内直接展示，整行可选，读屏只朗读一份单选状态。 */
@Composable
internal fun StemSeparationModeSelector(realtime: Boolean, onSelect: (Boolean) -> Unit) {
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
            )
        }
    }
}

@Composable
private fun StemModeOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
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
        }
    }
}
