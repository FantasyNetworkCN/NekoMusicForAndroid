package com.neko.music.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.neko.music.R
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.SakuraPink
import kotlinx.coroutines.launch

/**
 * 与 [com.neko.music.ui.screens.PlaylistDialog] 一致：页内全屏 overlay + 本页 [LayerBackdrop]，
 * 避免 [androidx.compose.ui.window.Dialog] 与 NavHost 录屏坐标系不一致导致取色错位。
 */
@Composable
fun GlassDialogOverlay(
    sampleBackdrop: LayerBackdrop,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                content()
            }
        }
    }
}

@Composable
fun ChangeAvatarGlassDialog(
    sampleBackdrop: LayerBackdrop,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val dialogGlass = LiquidGlassDefaults.appUpdateDialog
    val mutedColor = if (isDark) Color(0xFFB8B8D1).copy(alpha = 0.85f) else scheme.onSurfaceVariant

    GlassDialogOverlay(sampleBackdrop = sampleBackdrop, onDismiss = onDismiss) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            sampleBackdrop = sampleBackdrop,
            backgroundAlpha = dialogGlass.tint.background(isDark),
            borderAlpha = dialogGlass.tint.border(isDark),
            highlightAlpha = dialogGlass.tint.highlight(isDark),
            borderColor = if (isDark) {
                SakuraPink.copy(alpha = LiquidGlassDefaults.appUpdateDialogDarkBorderSakuraAlpha)
            } else {
                scheme.outline
            },
            liquidBlur = dialogGlass.liquid.blur,
            liquidLensHeight = dialogGlass.liquid.lensHeight,
            liquidLensAmount = dialogGlass.liquid.lensAmount,
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text(
                    text = stringResource(id = R.string.change_avatar),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseRed,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(id = R.string.change_avatar_confirm),
                    fontSize = 16.sp,
                    color = mutedColor,
                    lineHeight = 22.sp,
                )
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(id = R.string.cancel),
                            fontSize = 16.sp,
                            color = mutedColor,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassSurface(
                        modifier = Modifier
                            .height(44.dp)
                            .clickable(onClick = onConfirm),
                        shape = RoundedCornerShape(14.dp),
                        sampleBackdrop = sampleBackdrop,
                        backgroundAlpha = LiquidGlassDefaults.myPlaylistsDialogPrimaryButton.background(isDark),
                        borderAlpha = LiquidGlassDefaults.myPlaylistsDialogPrimaryButton.border(isDark),
                        highlightAlpha = LiquidGlassDefaults.myPlaylistsDialogPrimaryButton.highlight(isDark),
                        liquidBlur = dialogGlass.liquid.blur,
                        liquidLensHeight = dialogGlass.liquid.lensHeight,
                        liquidLensAmount = dialogGlass.liquid.lensAmount,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(id = R.string.confirm),
                                fontSize = 16.sp,
                                color = if (isDark) Color.White.copy(alpha = 0.95f) else scheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChangePasswordGlassDialog(
    sampleBackdrop: LayerBackdrop,
    onDismiss: () -> Unit,
    onConfirm: suspend (oldPassword: String, newPassword: String) -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val dialogGlass = LiquidGlassDefaults.appUpdateDialog
    val titleColor = if (isDark) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface
    val mutedColor = if (isDark) Color(0xFFB8B8D1).copy(alpha = 0.85f) else scheme.onSurfaceVariant
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = titleColor,
        unfocusedTextColor = titleColor,
        focusedLabelColor = RoseRed,
        unfocusedLabelColor = mutedColor,
        focusedBorderColor = RoseRed.copy(alpha = 0.85f),
        unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.22f) else scheme.outline,
        cursorColor = RoseRed,
        focusedTrailingIconColor = mutedColor,
        unfocusedTrailingIconColor = mutedColor,
    )

    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showOldPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isUpdating by remember { mutableStateOf(false) }

    suspend fun validateAndConfirm() {
        when {
            oldPassword.isEmpty() -> errorMessage = context.getString(R.string.please_enter_old_password)
            newPassword.isEmpty() -> errorMessage = context.getString(R.string.please_enter_new_password)
            confirmPassword.isEmpty() -> errorMessage = context.getString(R.string.please_confirm_new_password)
            newPassword != confirmPassword -> errorMessage = context.getString(R.string.password_mismatch)
            newPassword.length < 6 -> errorMessage = context.getString(R.string.new_password_length_error)
            else -> {
                isUpdating = true
                val success = onConfirm(oldPassword, newPassword)
                isUpdating = false
                if (success) onDismiss()
            }
        }
    }

    GlassDialogOverlay(sampleBackdrop = sampleBackdrop, onDismiss = onDismiss) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            sampleBackdrop = sampleBackdrop,
            backgroundAlpha = dialogGlass.tint.background(isDark),
            borderAlpha = dialogGlass.tint.border(isDark),
            highlightAlpha = dialogGlass.tint.highlight(isDark),
            borderColor = if (isDark) {
                SakuraPink.copy(alpha = LiquidGlassDefaults.appUpdateDialogDarkBorderSakuraAlpha)
            } else {
                scheme.outline
            },
            liquidBlur = dialogGlass.liquid.blur,
            liquidLensHeight = dialogGlass.liquid.lensHeight,
            liquidLensAmount = dialogGlass.liquid.lensAmount,
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text(
                    text = stringResource(id = R.string.modify_password),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseRed,
                )
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = {
                        oldPassword = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(id = R.string.old_password)) },
                    singleLine = true,
                    visualTransformation = if (showOldPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showOldPassword = !showOldPassword }) {
                            Icon(
                                imageVector = if (showOldPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showOldPassword) {
                                    stringResource(id = R.string.hide_password)
                                } else {
                                    stringResource(id = R.string.show_password)
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    shape = RoundedCornerShape(14.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(id = R.string.new_password)) },
                    singleLine = true,
                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showNewPassword = !showNewPassword }) {
                            Icon(
                                imageVector = if (showNewPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showNewPassword) {
                                    stringResource(id = R.string.hide_password)
                                } else {
                                    stringResource(id = R.string.show_password)
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    shape = RoundedCornerShape(14.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(id = R.string.confirm_new_password)) },
                    singleLine = true,
                    visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                            Icon(
                                imageVector = if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showConfirmPassword) {
                                    stringResource(id = R.string.hide_password)
                                } else {
                                    stringResource(id = R.string.show_password)
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword,
                    colors = fieldColors,
                    shape = RoundedCornerShape(14.dp),
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, enabled = !isUpdating) {
                        Text(
                            text = stringResource(id = R.string.cancel),
                            fontSize = 16.sp,
                            color = mutedColor,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassSurface(
                        modifier = Modifier
                            .height(44.dp)
                            .clickable(enabled = !isUpdating) {
                                scope.launch { validateAndConfirm() }
                            },
                        shape = RoundedCornerShape(14.dp),
                        sampleBackdrop = sampleBackdrop,
                        backgroundAlpha = LiquidGlassDefaults.myPlaylistsDialogPrimaryButton.background(isDark),
                        borderAlpha = LiquidGlassDefaults.myPlaylistsDialogPrimaryButton.border(isDark),
                        highlightAlpha = LiquidGlassDefaults.myPlaylistsDialogPrimaryButton.highlight(isDark),
                        liquidBlur = dialogGlass.liquid.blur,
                        liquidLensHeight = dialogGlass.liquid.lensHeight,
                        liquidLensAmount = dialogGlass.liquid.lensAmount,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (isUpdating) {
                                    stringResource(id = R.string.modifying)
                                } else {
                                    stringResource(id = R.string.confirm)
                                },
                                fontSize = 16.sp,
                                color = if (isDark) Color.White.copy(alpha = 0.95f) else scheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
