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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.neko.music.R
import com.neko.music.service.MusicPlayerManager
import com.neko.music.service.StemStreamPhase
import com.neko.music.ui.icons.NekoSymbol
import com.neko.music.ui.icons.NekoSymbolIcon
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class MediaVolume(val current: Int = 0, val minimum: Int = 0, val maximum: Int = 0, val fixed: Boolean = true)

@Composable
internal fun PlayerVolumeSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val separation = remember(context) { MusicPlayerManager.getInstance(context).stemSeparation }
    val state by separation.state.collectAsState()
    val streamPhase by separation.streamPhase.collectAsState()
    val statusMessage by separation.statusMessage.collectAsState()
    val lifecycle = LocalLifecycleOwner.current
    var volume by remember { mutableStateOf(MediaVolume()) }
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

    // 仅播放控件可见且页面在前台时读取，实体按键和系统面板都以系统实际音量为准。
    LaunchedEffect(audioManager, lifecycle, state.enabled) {
        if (state.enabled) return@LaunchedEffect
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                refreshVolume()
                delay(250)
            }
        }
    }

    Column(modifier = modifier) {
        if (state.enabled) {
            VolumeSlider(stringResource(R.string.stem_vocals), state.vocals, onValueChange = separation::setVocals)
            VolumeSlider(stringResource(R.string.stem_instrumental), state.instrumental, onValueChange = separation::setInstrumental)
            if (streamPhase != StemStreamPhase.ACTIVE) {
                Text(
                    text = stemPreparationDescription(streamPhase, statusMessage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val available = !volume.fixed && volume.maximum > volume.minimum
            val fraction = if (volume.maximum > volume.minimum) {
                (volume.current - volume.minimum).toFloat() / (volume.maximum - volume.minimum)
            } else 0f
            VolumeSlider(
                label = stringResource(R.string.player_system_media_volume),
                value = fraction.coerceIn(0f, 1f),
                enabled = available,
                showSpeaker = true,
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
        }
    }
}

@Composable
private fun VolumeSlider(
    label: String,
    value: Float,
    enabled: Boolean = true,
    showSpeaker: Boolean = false,
    onValueChange: (Float) -> Unit,
) {
    val backdrop = LocalLiquidLayerBackdrop.current
    val percent = stringResource(R.string.player_volume_percent, (value * 100).roundToInt())
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSpeaker) {
            NekoSymbolIcon(
                symbol = NekoSymbol.Volume,
                contentDescription = null,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(72.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        val semantics = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
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
            Slider(state = sliderState, onValueChange = {
                sliderState.value = it
                onValueChange(it)
            }, enabled = enabled, modifier = semantics)
        }
        Text(
            percent, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp), textAlign = TextAlign.End,
        )
    }
}
