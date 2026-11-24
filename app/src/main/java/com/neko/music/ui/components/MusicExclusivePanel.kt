package com.neko.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.neko.music.R
import com.neko.music.service.MusicPlayerManager

/** 播放页音频设置中的 USB DAC 独占开关。 */
@Composable
internal fun MusicExclusivePanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val playerManager = remember(context) { MusicPlayerManager.getInstance(context) }
    val enabled by playerManager.musicExclusiveEnabled.collectAsState()
    val usbDacOutputs by playerManager.usbDacOutputs.collectAsState()
    val usbDac = usbDacOutputs.firstOrNull()
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val canToggle = usbDac != null || enabled
    val change: (Boolean) -> Unit = { checked ->
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        playerManager.setMusicExclusiveEnabled(checked)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(
                    value = enabled,
                    enabled = canToggle,
                    role = Role.Switch,
                    onValueChange = change,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.player_exclusive_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                )
                Text(
                    text = if (usbDac != null) {
                        stringResource(R.string.player_exclusive_subtitle_usb, usbDac.name)
                    } else {
                        stringResource(R.string.player_exclusive_usb_required)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier.width(72.dp).heightIn(min = 48.dp).clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                LiquidGlassSwitch(checked = enabled, onCheckedChange = change, enabled = canToggle)
            }
        }
        Text(
            text = stringResource(R.string.player_exclusive_hint),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.player_exclusive_compatibility),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }
}
