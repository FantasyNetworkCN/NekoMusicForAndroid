package com.neko.music.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.music.R
import com.neko.music.service.EqualizerController
import com.neko.music.service.EqualizerFrequencies
import com.neko.music.service.EqualizerPreset
import com.neko.music.service.MusicPlayerManager
import com.neko.music.ui.theme.isAppDarkTheme
import kotlin.math.roundToInt

@Composable
internal fun EqualizerPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val equalizer = remember(context) { MusicPlayerManager.getInstance(context).equalizer }
    val state by equalizer.state.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val backdrop = LocalLiquidLayerBackdrop.current
    val useLiquid = backdrop != null && isLiquidGlassEffectActive(backdrop)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.eq_enable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                )
                Text(
                    stringResource(R.string.eq_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            LiquidGlassSwitch(
                checked = state.enabled,
                onCheckedChange = equalizer::setEnabled,
            )
        }

        Text(
            stringResource(R.string.eq_presets),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val presets = EqualizerPreset.all + EqualizerPreset.CUSTOM
            items(presets, key = { it }) { preset ->
                FilterChip(
                    selected = state.preset == preset,
                    onClick = {
                        if (preset != EqualizerPreset.CUSTOM) equalizer.setPreset(preset)
                    },
                    enabled = state.enabled && preset != EqualizerPreset.CUSTOM,
                    label = { Text(presetLabel(preset), color = scheme.onSurface) },
                )
            }
        }

        EqualizerPreamp(
            value = state.preampDb,
            enabled = state.enabled,
            useLiquid = useLiquid,
            backdrop = backdrop,
            onValueChange = equalizer::setPreamp,
            onFinished = equalizer::persist,
        )

        Text(
            stringResource(R.string.eq_bands),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(232.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EqualizerFrequencies.values.forEachIndexed { index, frequency ->
                VerticalEqBand(
                    frequency = frequency,
                    gain = state.bandGainsDb[index],
                    enabled = state.enabled,
                    onValueChange = { equalizer.setBandGain(index, it) },
                    onFinished = equalizer::persist,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val resetLabel = stringResource(R.string.eq_reset)
        val tint = LiquidGlassDefaults.shareSheetSection
        val isDark = isAppDarkTheme()
        GlassSurface(
            modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
                .clickable(enabled = state.enabled, onClick = equalizer::reset),
            shape = RoundedCornerShape(20.dp),
            backgroundAlpha = tint.tint.background(isDark),
            borderAlpha = tint.tint.border(isDark),
            highlightAlpha = tint.tint.highlight(isDark),
            borderColor = MaterialTheme.colorScheme.outline,
            material3Container = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(
                    resetLabel,
                    color = if (state.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun EqualizerPreamp(
    value: Float,
    enabled: Boolean,
    useLiquid: Boolean,
    backdrop: com.kyant.backdrop.Backdrop?,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    val label = stringResource(R.string.eq_preamp)
    var local by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { local = value }
    val range = EqualizerFrequencies.MAX_PREAMP_DB - EqualizerFrequencies.MIN_PREAMP_DB
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            Modifier.width(72.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val gainLabel = stringResource(R.string.eq_db_value, local)
        val sliderModifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
            contentDescription = label
            stateDescription = gainLabel
        }
        if (useLiquid && backdrop != null) {
            LiquidGlassSlider(
                value = (local - EqualizerFrequencies.MIN_PREAMP_DB) / range,
                onValueChange = {
                    local = EqualizerFrequencies.MIN_PREAMP_DB + it * range
                    onValueChange(local)
                },
                backdrop = backdrop,
                enabled = enabled,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = sliderModifier,
                onValueChangeFinished = onFinished,
            )
        } else {
            val sliderState = remember {
                SliderState(
                    value = local,
                    trackRange = EqualizerFrequencies.MIN_PREAMP_DB..EqualizerFrequencies.MAX_PREAMP_DB,
                )
            }
            LaunchedEffect(local) { sliderState.value = local }
            Slider(
                state = sliderState,
                onValueChange = {
                    local = it
                    onValueChange(it)
                },
                onValueChangeFinished = onFinished,
                enabled = enabled,
                modifier = sliderModifier,
            )
        }
        Text(
            stringResource(R.string.eq_db_value, local),
            Modifier.width(64.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VerticalEqBand(
    frequency: Int,
    gain: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val frequencyLabel = if (frequency >= 1000) "${frequency / 1000}k" else "$frequency"
    val gainLabel = stringResource(R.string.eq_db_value, gain)
    val contentDescription = "${frequencyLabel}Hz $gainLabel"
    val min = EqualizerFrequencies.MIN_GAIN_DB
    val range = EqualizerFrequencies.MAX_GAIN_DB - min
    Column(
        modifier = modifier.height(232.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(gainLabel, fontSize = 9.sp, color = scheme.onSurfaceVariant, maxLines = 1)
        Box(
            modifier = Modifier.widthIn(min = 40.dp).weight(1f).heightIn(min = 140.dp)
                .semantics {
                    this.contentDescription = contentDescription
                    stateDescription = gainLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(gain, min..EqualizerFrequencies.MAX_GAIN_DB)
                    setProgress { target ->
                        val clamped = target.coerceIn(min, EqualizerFrequencies.MAX_GAIN_DB)
                        onValueChange(clamped)
                        true
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun update(y: Float) {
                            val fraction = 1f - (y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                            onValueChange(min + fraction * range)
                        }
                        update(down.position.y)
                        down.consume()
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            update(change.position.y)
                            change.consume()
                            pressed = change.pressed
                        }
                        onFinished()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val fraction = ((gain - min) / range).coerceIn(0f, 1f)
                val thumbY = size.height * (1f - fraction)
                drawLine(
                    color = scheme.outlineVariant.copy(alpha = 0.7f),
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = 4.dp.toPx(),
                )
                drawLine(
                    color = if (enabled) scheme.primary else scheme.outline,
                    start = Offset(centerX, centerY),
                    end = Offset(centerX, thumbY),
                    strokeWidth = 4.dp.toPx(),
                )
                drawCircle(
                    color = if (enabled) scheme.primary else scheme.outline,
                    radius = 7.dp.toPx(),
                    center = Offset(centerX, thumbY),
                )
                drawCircle(
                    color = scheme.onSurface.copy(alpha = 0.28f),
                    radius = 1.dp.toPx(),
                    center = Offset(centerX, centerY),
                )
            }
        }
        Text(frequencyLabel, fontSize = 9.sp, color = scheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun presetLabel(preset: String): String = stringResource(
    when (preset) {
        EqualizerPreset.FLAT -> R.string.eq_preset_flat
        EqualizerPreset.POP -> R.string.eq_preset_pop
        EqualizerPreset.ROCK -> R.string.eq_preset_rock
        EqualizerPreset.JAZZ -> R.string.eq_preset_jazz
        EqualizerPreset.CLASSICAL -> R.string.eq_preset_classical
        EqualizerPreset.VOCAL -> R.string.eq_preset_vocal
        EqualizerPreset.BASS -> R.string.eq_preset_bass
        EqualizerPreset.TREBLE -> R.string.eq_preset_treble
        else -> R.string.eq_preset_custom
    }
)
