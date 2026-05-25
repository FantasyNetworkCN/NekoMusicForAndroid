package com.neko.music.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.R
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.SakuraPink

/**
 * 认证流全屏页壳：壁纸 + 页内 [pageBackdrop] 录屏 + 顶栏 + 玻璃表单区。
 * 作为 NavHost 子路由时独占一层，不叠在「我的」等页面之上。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthPageShell(
    topBarTitle: String,
    headline: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pageBackdrop: LayerBackdrop? = null,
    content: @Composable ColumnScope.(pageBackdrop: LayerBackdrop) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme() || scheme.background.luminance() < 0.5f
    val resolvedBackdrop = pageBackdrop ?: rememberLiquidPageBackdrop(scheme.background)
    val glassTint = LiquidGlassDefaults.screenListCard
    val glassBg = glassTint.background(isDark)
    val glassBorder = glassTint.border(isDark)
    val glassHighlight = glassTint.highlight(isDark)
    val mutedColor = if (isDark) Color(0xFFB8B8D1).copy(alpha = 0.85f) else scheme.onSurfaceVariant
    val onSurface = if (isDark) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(resolvedBackdrop),
        ) {
            Image(
                painter = painterResource(id = R.drawable.playlist_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        CompositionLocalProvider(LocalLiquidLayerBackdrop provides resolvedBackdrop) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentColor = onSurface,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = topBarTitle,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = onSurface,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = stringResource(id = R.string.back),
                                    tint = if (isDark) Color(0xFFB8B8D1).copy(alpha = 0.9f) else scheme.onSurface,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
                    )
                },
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp),
                ) {
                    Text(
                        text = headline,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = RoseRed,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        fontSize = 15.sp,
                        color = mutedColor,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        sampleBackdrop = resolvedBackdrop,
                        backgroundAlpha = glassBg,
                        borderAlpha = glassBorder,
                        highlightAlpha = glassHighlight,
                        borderColor = if (isDark) {
                            SakuraPink.copy(alpha = LiquidGlassDefaults.appUpdateDialogDarkBorderSakuraAlpha)
                        } else {
                            scheme.outline
                        },
                        liquidBlur = LiquidGlassDefaults.appUpdateDialog.liquid.blur,
                        liquidLensHeight = LiquidGlassDefaults.appUpdateDialog.liquid.lensHeight,
                        liquidLensAmount = LiquidGlassDefaults.appUpdateDialog.liquid.lensAmount,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 24.dp),
                        ) {
                            content(resolvedBackdrop)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun authTextFieldColors(isDark: Boolean = isSystemInDarkTheme()): androidx.compose.material3.TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    val titleColor = if (isDark) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface
    val mutedColor = if (isDark) Color(0xFFB8B8D1).copy(alpha = 0.85f) else scheme.onSurfaceVariant
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = titleColor,
        unfocusedTextColor = titleColor,
        focusedLabelColor = RoseRed,
        unfocusedLabelColor = mutedColor,
        focusedBorderColor = RoseRed.copy(alpha = 0.85f),
        unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.22f) else scheme.outline,
        cursorColor = RoseRed,
        focusedLeadingIconColor = RoseRed,
        unfocusedLeadingIconColor = mutedColor,
        focusedTrailingIconColor = mutedColor,
        unfocusedTrailingIconColor = mutedColor,
    )
}

@Composable
fun AuthGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = leadingIcon?.let { icon ->
            { Icon(imageVector = icon, contentDescription = label) }
        },
        trailingIcon = trailingIcon,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(14.dp),
        colors = authTextFieldColors(isDark),
    )
}

@Composable
fun AuthErrorText(message: String, modifier: Modifier = Modifier) {
    if (message.isEmpty()) return
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        fontSize = 14.sp,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

@Composable
fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    pageBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    val scheme = MaterialTheme.colorScheme
    val confirmGlass = LiquidGlassDefaults.myPlaylistsDialogPrimaryButton
    val dialogLiquid = LiquidGlassDefaults.appUpdateDialog.liquid

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(
                enabled = enabled && !loading,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(14.dp),
        sampleBackdrop = pageBackdrop,
        backgroundAlpha = if (enabled) confirmGlass.background(isDark) else confirmGlass.background(isDark) * 0.6f,
        borderAlpha = confirmGlass.border(isDark),
        highlightAlpha = confirmGlass.highlight(isDark),
        liquidBlur = dialogLiquid.blur,
        liquidLensHeight = dialogLiquid.lensHeight,
        liquidLensAmount = dialogLiquid.lensAmount,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = if (isDark) Color.White else scheme.onSurface,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color.White.copy(alpha = 0.95f) else scheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun AuthTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(
            text = text,
            color = RoseRed,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun AuthFooterPrompt(
    prompt: String,
    link: String,
    onLinkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val mutedColor = if (isDark) Color(0xFFB8B8D1).copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = prompt, color = mutedColor, fontSize = 14.sp)
        AuthTextLink(text = link, onClick = onLinkClick)
    }
}
