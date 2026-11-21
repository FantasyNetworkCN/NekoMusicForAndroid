package com.neko.music.ui.components

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.neko.music.R
import com.neko.music.service.MusicPlayerManager
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class MediaVolume(val current: Int = 0, val minimum: Int = 0, val maximum: Int = 0, val fixed: Boolean = true)

@Composable
internal fun PlayerVolumeSection() {
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val separation = remember(context) { MusicPlayerManager.getInstance(context).stemSeparation }
    val state by separation.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current
    var volume by remember { mutableStateOf(MediaVolume()) }
    var confirmDownload by remember { mutableStateOf(false) }
    val failureText = stringResource(R.string.player_volume_change_failed)

    fun refreshVolume() {
        volume = try {
            MediaVolume(
                current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
                minimum = if (Build.VERSION.SDK_INT >= 28) audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0,
                maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                fixed = audioManager.isVolumeFixed,
            )
        } catch (_: SecurityException) {
            MediaVolume()
        }
    }

    // 仅面板可见且页面在前台时读取，实体按键、蓝牙设备和系统面板都以系统实际音量为准。
    LaunchedEffect(audioManager, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                refreshVolume()
                delay(250)
            }
        }
    }

    Text(
        text = stringResource(R.string.player_volume_settings),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
    )
    ShareSheetLiquidSection(modifier = Modifier.padding(horizontal = 4.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val available = !volume.fixed && volume.maximum > volume.minimum
            val fraction = if (volume.maximum > volume.minimum) {
                (volume.current - volume.minimum).toFloat() / (volume.maximum - volume.minimum)
            } else 0f
            VolumeSlider(
                label = stringResource(R.string.player_system_media_volume),
                value = fraction.coerceIn(0f, 1f),
                enabled = available,
                onValueChange = { value ->
                    val target = volume.minimum + (value * (volume.maximum - volume.minimum)).roundToInt()
                    try {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target.coerceIn(volume.minimum, volume.maximum), 0)
                    } catch (_: SecurityException) {
                        Toast.makeText(context, failureText, Toast.LENGTH_SHORT).show()
                    }
                    refreshVolume()
                },
            )
            Text(
                text = stringResource(if (available) R.string.player_system_volume_hint else R.string.player_system_volume_fixed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val separationLabel = stringResource(R.string.stem_separation)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(separationLabel, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(if (state.roomPlaybackActive) R.string.stem_room_unavailable else R.string.stem_separation_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.enabled || state.preparing,
                    enabled = !state.roomPlaybackActive,
                    onCheckedChange = { enabled ->
                        if (enabled && !separation.modelDownloaded) confirmDownload = true
                        else separation.setEnabled(enabled)
                    },
                    modifier = Modifier.semantics { contentDescription = separationLabel },
                )
            }
            if (state.preparing) {
                val progress = state.downloadProgress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.stem_downloading, (progress * 100).roundToInt()), style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.stem_preparing), style = MaterialTheme.typography.bodySmall)
                }
            }
            state.errorRes?.let {
                Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (state.enabled) {
                VolumeSlider(stringResource(R.string.stem_vocals), state.vocals, onValueChange = separation::setVocals)
                VolumeSlider(stringResource(R.string.stem_instrumental), state.instrumental, onValueChange = separation::setInstrumental)
                Text(
                    stringResource(R.string.stem_buffer_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    separation.setVocals(1f)
                    separation.setInstrumental(1f)
                }) { Text(stringResource(R.string.stem_reset_mix)) }
            }
        }
    }
    if (confirmDownload) {
        AlertDialog(
            onDismissRequest = { confirmDownload = false },
            title = { Text(stringResource(R.string.stem_separation)) },
            text = { Text(stringResource(R.string.stem_download_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDownload = false
                    separation.setEnabled(true)
                }) { Text(stringResource(R.string.stem_enable_download)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDownload = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun VolumeSlider(
    label: String,
    value: Float,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
) {
    val backdrop = LocalLiquidLayerBackdrop.current
    val percent = stringResource(R.string.player_volume_percent, (value * 100).roundToInt())
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(percent, style = MaterialTheme.typography.bodyMedium)
        }
        val semantics = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
            contentDescription = label
            stateDescription = percent
        }
        if (backdrop != null && isLiquidGlassEffectActive(backdrop)) {
            LiquidGlassSlider(
                value = value,
                onValueChange = onValueChange,
                backdrop = backdrop,
                enabled = enabled,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = semantics.semantics {
                    if (enabled) setProgress { target ->
                        onValueChange(target.coerceIn(0f, 1f))
                        true
                    }
                },
            )
        } else {
            val sliderState = remember { SliderState(value = value) }
            LaunchedEffect(value) { sliderState.value = value }
            Slider(state = sliderState, onValueChange = onValueChange, enabled = enabled, modifier = semantics)
        }
    }
}
