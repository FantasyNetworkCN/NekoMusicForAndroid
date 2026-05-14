package com.neko.music.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.text.format.Formatter
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.neko.music.R
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.SakuraPink

/**
 * 弹窗不在底栏等 `LocalLiquidLayerBackdrop` 子树内，须显式采样 [LocalNavHostRecordingBackdrop]（MainActivity Nav 录屏），
 * 否则 [GlassSurface] 一直走 CPU 磨砂、看不出 Kyant 液态。
 */
@Composable
private fun appDialogSampleBackdrop(): LayerBackdrop? =
    LocalNavHostRecordingBackdrop.current ?: LocalLiquidLayerBackdrop.current

@Composable
fun AppUpdatePromptDialog(
    versionName: String,
    versionCode: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val appUpdateGlass = LiquidGlassDefaults.appUpdateDialog
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            sampleBackdrop = appDialogSampleBackdrop(),
            backgroundAlpha = appUpdateGlass.tint.background(isDark),
            borderAlpha = appUpdateGlass.tint.border(isDark),
            highlightAlpha = appUpdateGlass.tint.highlight(isDark),
            borderColor = if (isDark) SakuraPink.copy(alpha = LiquidGlassDefaults.appUpdateDialogDarkBorderSakuraAlpha) else scheme.outline,
            liquidBlur = appUpdateGlass.liquid.blur,
            liquidLensHeight = appUpdateGlass.liquid.lensHeight,
            liquidLensAmount = appUpdateGlass.liquid.lensAmount
        ) {
            Column(modifier = Modifier.padding(32.dp)) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    RoseRed.copy(alpha = 0.15f),
                                    SakuraPink.copy(alpha = 0.15f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.update),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.new_version_found),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseRed,
                    letterSpacing = 0.3.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.new_version, versionName),
                    fontSize = 17.sp,
                    color = scheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.version_code, versionCode),
                    fontSize = 17.sp,
                    color = scheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.later),
                            fontSize = 17.sp,
                            color = scheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = RoseRed),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.update_now),
                            fontSize = 17.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun AppUpdateDownloadProgressDialog(
    transferredBytes: Long,
    contentLengthBytes: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val appUpdateGlass = LiquidGlassDefaults.appUpdateDialog
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            sampleBackdrop = appDialogSampleBackdrop(),
            backgroundAlpha = appUpdateGlass.tint.background(isDark),
            borderAlpha = appUpdateGlass.tint.border(isDark),
            highlightAlpha = appUpdateGlass.tint.highlight(isDark),
            borderColor = if (isDark) SakuraPink.copy(alpha = LiquidGlassDefaults.appUpdateDialogDarkBorderSakuraAlpha) else scheme.outline,
            liquidBlur = appUpdateGlass.liquid.blur,
            liquidLensHeight = appUpdateGlass.liquid.lensHeight,
            liquidLensAmount = appUpdateGlass.liquid.lensAmount
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    RoseRed.copy(alpha = 0.15f),
                                    SakuraPink.copy(alpha = 0.15f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.download),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.downloading_update),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseRed,
                    letterSpacing = 0.3.sp
                )
                Spacer(modifier = Modifier.height(28.dp))
                key(contentLengthBytes, transferredBytes) {
                    val receivedStr = Formatter.formatFileSize(context, transferredBytes)
                    val totalStr = if (contentLengthBytes > 0L) {
                        Formatter.formatFileSize(context, contentLengthBytes)
                    } else {
                        ""
                    }
                    val hasKnownTotal = contentLengthBytes > 0L
                    val progressFraction = if (hasKnownTotal) {
                        (transferredBytes.toFloat() / contentLengthBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    if (hasKnownTotal) {
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp),
                            color = RoseRed,
                            trackColor = if (isDark) scheme.surfaceVariant.copy(alpha = 0.5f)
                            else Color.Gray.copy(alpha = 0.25f)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp),
                            color = RoseRed,
                            trackColor = if (isDark) scheme.surfaceVariant.copy(alpha = 0.5f)
                            else Color.Gray.copy(alpha = 0.25f)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (hasKnownTotal) {
                            stringResource(R.string.update_download_bytes_pair, receivedStr, totalStr)
                        } else {
                            stringResource(R.string.update_download_bytes_only, receivedStr)
                        },
                        fontSize = 16.sp,
                        color = scheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    if (hasKnownTotal) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.update_download_percent,
                                (progressFraction * 100).toInt()
                            ),
                            fontSize = 17.sp,
                            color = scheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppUpdateSuccessDialog(
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val green = Color(0xFF4CAF50)
    val appUpdateGlass = LiquidGlassDefaults.appUpdateDialog
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            sampleBackdrop = appDialogSampleBackdrop(),
            backgroundAlpha = appUpdateGlass.tint.background(isDark),
            borderAlpha = appUpdateGlass.tint.border(isDark),
            highlightAlpha = appUpdateGlass.tint.highlight(isDark),
            borderColor = if (isDark) green.copy(alpha = LiquidGlassDefaults.appUpdateSuccessDarkBorderGreenAlpha) else scheme.outline,
            liquidBlur = appUpdateGlass.liquid.blur,
            liquidLensHeight = appUpdateGlass.liquid.lensHeight,
            liquidLensAmount = appUpdateGlass.liquid.lensAmount
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    green.copy(alpha = 0.15f),
                                    Color(0xFF66BB6A).copy(alpha = 0.15f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(green, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "√",
                            fontSize = 28.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.download_complete),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    letterSpacing = 0.3.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.installing_update),
                    fontSize = 17.sp,
                    color = scheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AppUpdateErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val red = Color(0xFFF44336)
    val appUpdateGlass = LiquidGlassDefaults.appUpdateDialog
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            sampleBackdrop = appDialogSampleBackdrop(),
            backgroundAlpha = appUpdateGlass.tint.background(isDark),
            borderAlpha = appUpdateGlass.tint.border(isDark),
            highlightAlpha = appUpdateGlass.tint.highlight(isDark),
            borderColor = if (isDark) red.copy(alpha = LiquidGlassDefaults.appUpdateErrorDarkBorderRedAlpha) else scheme.outline,
            liquidBlur = appUpdateGlass.liquid.blur,
            liquidLensHeight = appUpdateGlass.liquid.lensHeight,
            liquidLensAmount = appUpdateGlass.liquid.lensAmount
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    red.copy(alpha = 0.15f),
                                    Color(0xFFEF5350).copy(alpha = 0.15f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(red, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "×",
                            fontSize = 32.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.update_failed),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = red,
                    letterSpacing = 0.3.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    fontSize = 17.sp,
                    color = scheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.confirm),
                            fontSize = 17.sp,
                            color = RoseRed,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
