package com.neko.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.neko.music.R
import com.neko.music.data.api.VideoRenderApi
import com.neko.music.data.model.Music
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.SakuraPink
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun VideoRenderDialog(
    music: Music,
    trackDurationSec: Int,
    isVip: Boolean,
    busy: Boolean,
    sampleBackdrop: LayerBackdrop,
    onDismiss: () -> Unit,
    onConfirm: (startSec: Double, watermarked: Boolean) -> Unit
) {
    val effectiveDuration = max(trackDurationSec, 1)
    val clipSec = if (isVip) {
        effectiveDuration.toFloat()
    } else {
        VideoRenderApi.NON_VIP_CLIP_SEC.toFloat()
    }
    val maxStartSec = max(0f, effectiveDuration - clipSec)

    var startSec by remember(music.id, isVip) {
        mutableFloatStateOf(0f.coerceAtMost(maxStartSec))
    }
    var watermarked by remember(music.id, isVip) {
        mutableStateOf(!isVip)
    }

    val endSec = min(effectiveDuration.toFloat(), startSec + clipSec)

    val scheme = MaterialTheme.colorScheme
    val isDarkTheme = scheme.background.luminance() < 0.5f
    val dialogGlass = LiquidGlassDefaults.videoRenderDialog
    val confirmGlass = LiquidGlassDefaults.myPlaylistsDialogPrimaryButton
    val titleColor = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface
    val mutedColor = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else scheme.onSurfaceVariant
    val bodyColor = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.9f) else scheme.onSurface

    // 与分享面板一致：同层浮层 + 显式 pageBackdrop，避免 Dialog 独立窗口无法 Kyant 采样
    BackHandler(enabled = !busy) {
        if (!busy) onDismiss()
    }

    Box(modifier = Modifier.fillMaxSize().zIndex(45f)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    enabled = !busy,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!busy) onDismiss() },
                ),
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(24.dp),
                sampleBackdrop = sampleBackdrop,
                backgroundAlpha = dialogGlass.tint.background(isDarkTheme),
                borderAlpha = dialogGlass.tint.border(isDarkTheme),
                highlightAlpha = dialogGlass.tint.highlight(isDarkTheme),
                borderColor = if (isDarkTheme) {
                    SakuraPink.copy(alpha = LiquidGlassDefaults.appUpdateDialogDarkBorderSakuraAlpha)
                } else {
                    scheme.outline
                },
                liquidBlur = dialogGlass.liquid.blur,
                liquidLensHeight = dialogGlass.liquid.lensHeight,
                liquidLensAmount = dialogGlass.liquid.lensAmount,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                ) {
                    Text(
                        text = stringResource(R.string.video_render_dialog_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${music.title} · ${music.artist}",
                        fontSize = 13.sp,
                        color = mutedColor,
                        maxLines = 2,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isVip) {
                            stringResource(R.string.video_render_vip_hint)
                        } else {
                            stringResource(R.string.video_render_free_hint)
                        },
                        fontSize = 12.sp,
                        color = mutedColor,
                        lineHeight = 17.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.video_render_start_label),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = bodyColor,
                        )
                        Text(
                            text = stringResource(
                                R.string.video_render_range_value,
                                formatClipTime(startSec.roundToInt()),
                                formatClipTime(endSec.roundToInt()),
                            ),
                            fontSize = 13.sp,
                            color = RoseRed,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Slider(
                        value = startSec,
                        onValueChange = { startSec = it.coerceIn(0f, maxStartSec) },
                        valueRange = 0f..maxStartSec.coerceAtLeast(0f),
                        enabled = maxStartSec > 0f && !busy,
                        colors = SliderDefaults.colors(
                            thumbColor = RoseRed,
                            activeTrackColor = RoseRed,
                            inactiveTrackColor = if (isDarkTheme) {
                                Color.White.copy(alpha = 0.18f)
                            } else {
                                scheme.outline.copy(alpha = 0.35f)
                            },
                        ),
                    )
                    Text(
                        text = if (isVip) {
                            stringResource(
                                R.string.video_render_vip_duration_hint,
                                formatClipTime((endSec - startSec).roundToInt().coerceAtLeast(1)),
                            )
                        } else {
                            stringResource(
                                R.string.video_render_free_duration_hint,
                                VideoRenderApi.NON_VIP_CLIP_SEC,
                            )
                        },
                        fontSize = 12.sp,
                        color = mutedColor,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = watermarked,
                            onCheckedChange = { if (isVip) watermarked = it },
                            enabled = isVip && !busy,
                            colors = CheckboxDefaults.colors(
                                checkedColor = RoseRed,
                                uncheckedColor = if (isDarkTheme) {
                                    Color(0xFFB8B8D1).copy(alpha = 0.7f)
                                } else {
                                    scheme.onSurfaceVariant
                                },
                            ),
                        )
                        Text(
                            text = stringResource(R.string.video_render_watermark),
                            fontSize = 14.sp,
                            color = if (isVip) bodyColor else mutedColor,
                        )
                    }
                    if (!isVip) {
                        Text(
                            text = stringResource(R.string.video_render_non_vip_watermark_required),
                            fontSize = 11.sp,
                            color = mutedColor,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.video_render_email_notice),
                        fontSize = 11.sp,
                        color = mutedColor,
                        lineHeight = 16.sp,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(R.string.cancel),
                                color = mutedColor,
                            )
                        }
                        GlassSurface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clickable(
                                    enabled = !busy && (endSec - startSec) >= 0.5f,
                                    onClick = {
                                        val wm = if (isVip) watermarked else true
                                        onConfirm(startSec.toDouble(), wm)
                                    },
                                ),
                            shape = RoundedCornerShape(14.dp),
                            sampleBackdrop = sampleBackdrop,
                            backgroundAlpha = confirmGlass.background(isDarkTheme),
                            borderAlpha = confirmGlass.border(isDarkTheme),
                            highlightAlpha = confirmGlass.highlight(isDarkTheme),
                            liquidBlur = dialogGlass.liquid.blur,
                            liquidLensHeight = dialogGlass.liquid.lensHeight,
                            liquidLensAmount = dialogGlass.liquid.lensAmount,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (busy) {
                                        stringResource(R.string.video_render_submitting)
                                    } else {
                                        stringResource(R.string.video_render_submit)
                                    },
                                    color = if (!busy && (endSec - startSec) >= 0.5f) {
                                        RoseRed
                                    } else {
                                        mutedColor.copy(alpha = 0.5f)
                                    },
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoRenderPlayerEntry(
    isBusy: Boolean,
    jobStatus: String?,
    remainingToday: Int?,
    errorMessage: String? = null,
    onOpenDialog: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val labelColor = if (isDark) Color.White.copy(alpha = 0.88f) else MaterialTheme.colorScheme.onSurface
    val subColor = if (isDark) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (jobStatus) {
            "done" -> {
                Text(
                    text = stringResource(R.string.video_render_status_done),
                    fontSize = 12.sp,
                    color = subColor
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onDownload,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoseRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseRed)
                ) {
                    Text(stringResource(R.string.video_render_download))
                }
            }
            "pending", "processing" -> {
                Text(
                    text = stringResource(R.string.video_render_status_processing),
                    fontSize = 12.sp,
                    color = RoseRed
                )
                if (remainingToday != null) {
                    Text(
                        text = stringResource(R.string.video_render_remaining_today, remainingToday),
                        fontSize = 11.sp,
                        color = subColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            "failed" -> {
                Text(
                    text = stringResource(
                        R.string.video_render_failed,
                        errorMessage?.takeIf { it.isNotBlank() } ?: "—"
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = RoseRed.copy(alpha = if (isDark) 0.55f else 0.45f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable(onClick = onOpenDialog)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = RoseRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.video_render_button),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = labelColor
                    )
                }
            }
            else -> {
                Row(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = RoseRed.copy(alpha = if (isDark) 0.55f else 0.45f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable(enabled = !isBusy, onClick = onOpenDialog)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = RoseRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isBusy) {
                            stringResource(R.string.video_render_button_busy)
                        } else {
                            stringResource(R.string.video_render_button)
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = labelColor
                    )
                }
            }
        }
    }
}

private fun formatClipTime(sec: Int): String {
    val s = sec.coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    return if (m > 0) "%d:%02d".format(m, r) else "0:%02d".format(r)
}
