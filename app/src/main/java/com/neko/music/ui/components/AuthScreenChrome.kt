package com.neko.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.neko.music.R
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.isAppDarkTheme

/**
 * 认证流全屏页壳（登录 / 注册 / 忘记密码）。
 *
 * 结构对齐 [Kyant Backdrop 官方教程](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar)：
 * 壁纸先铺满并录屏（`layerBackdrop`），页内所有玻璃块都在录屏子树 **之外** 采样同一层纹理，
 * 于是标题保持通透、玻璃块各自折射背景，而不是「一张大实色卡片压在壁纸上」。
 *
 * 版式：顶部圆形玻璃返回按钮 → 品牌眉标 → 大标题 → 副标题 → 分段玻璃表单。
 */
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
    val isDark = isAppDarkTheme()
    val resolvedBackdrop = pageBackdrop ?: rememberLiquidPageBackdrop(scheme.background)
    val onSurface = if (isDark) Color(0xFFF4F4F8) else scheme.onSurface
    val mutedColor = if (isDark) Color(0xFFB9B9D2).copy(alpha = 0.9f) else scheme.onSurfaceVariant

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(resolvedBackdrop),
        ) {
            AppPageBackgroundImage(modifier = Modifier.fillMaxSize())
            // 可读性遮罩：垫在玻璃采样源里，玻璃因此直接吃到「压暗后的壁纸」，不会发灰。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = if (isDark) {
                                listOf(
                                    Color.Black.copy(alpha = 0.54f),
                                    Color.Black.copy(alpha = 0.20f),
                                    Color.Black.copy(alpha = 0.46f),
                                )
                            } else {
                                listOf(
                                    Color.White.copy(alpha = 0.80f),
                                    Color.White.copy(alpha = 0.32f),
                                    Color.White.copy(alpha = 0.68f),
                                )
                            }
                        )
                    )
            )
        }

        CompositionLocalProvider(LocalLiquidLayerBackdrop provides resolvedBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                // 返回按钮固定在顶部，不随表单滚动。
                Spacer(modifier = Modifier.height(6.dp))
                AuthGlassIconButton(
                    onClick = onBack,
                    pageBackdrop = resolvedBackdrop,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(modifier = Modifier.height(26.dp))

                    if (topBarTitle.isNotBlank() && topBarTitle != headline) {
                        Text(
                            text = topBarTitle,
                            color = RoseRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.6.sp,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = headline,
                        color = onSurface,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 40.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = subtitle,
                        color = mutedColor,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                    Spacer(modifier = Modifier.height(26.dp))

                    content(resolvedBackdrop)

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

/** 认证页顶部的圆形玻璃返回按钮。 */
@Composable
fun AuthGlassIconButton(
    onClick: () -> Unit,
    pageBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val isDark = isAppDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val panel = LiquidGlassDefaults.authIconButton
    GlassSurface(
        modifier = modifier
            .size(42.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        sampleBackdrop = pageBackdrop,
        backgroundAlpha = panel.tint.background(isDark),
        borderAlpha = panel.tint.border(isDark),
        highlightAlpha = panel.tint.highlight(isDark),
        borderColor = if (isDark) Color.White else scheme.outline,
        liquidBlur = panel.liquid.blur,
        liquidLensHeight = panel.liquid.lensHeight,
        liquidLensAmount = panel.liquid.lensAmount,
        kyantHighlight = Highlight.Default.copy(alpha = if (isDark) 0.46f else 0.60f),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(id = R.string.back),
                tint = if (isDark) Color(0xFFE9E9F4) else scheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 分段表单：一块玻璃包住整组输入行，行间用发丝线分隔（贴近系统分组列表的秩序感）。
 * 内部输入行保持透明，避免「玻璃套玻璃」触发 Kyant 递归采样 SIGSEGV。
 */
@Composable
fun AuthFieldGroup(
    pageBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = isAppDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val panel = LiquidGlassDefaults.authFieldGroup
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        sampleBackdrop = pageBackdrop,
        backgroundAlpha = panel.tint.background(isDark),
        borderAlpha = panel.tint.border(isDark),
        highlightAlpha = panel.tint.highlight(isDark),
        borderColor = if (isDark) Color.White else scheme.outline,
        liquidBlur = panel.liquid.blur,
        liquidLensHeight = panel.liquid.lensHeight,
        liquidLensAmount = panel.liquid.lensAmount,
        kyantHighlight = Highlight.Default.copy(alpha = if (isDark) 0.40f else 0.56f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        ) {
            content()
        }
    }
}

/** 输入行之间的发丝分隔线，左侧缩进与文字对齐。 */
@Composable
fun AuthFieldDivider(modifier: Modifier = Modifier) {
    val isDark = isAppDarkTheme()
    val alpha = if (isDark) {
        LiquidGlassDefaults.authFieldDividerDarkAlpha
    } else {
        LiquidGlassDefaults.authFieldDividerLightAlpha
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 50.dp, end = 18.dp)
            .height(0.5.dp)
            .background(
                if (isDark) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha)
            )
    )
}

/**
 * 认证页输入行：不再是 `OutlinedTextField` 的实色描边框，而是透明输入行 ——
 * 描边交给外层 [AuthFieldGroup] 的玻璃边缘，聚焦只用品牌色点亮图标与标签。
 */
@Composable
fun AuthGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    autofillType: ContentType? = null,
    singleLine: Boolean = true,
    isDark: Boolean = isAppDarkTheme(),
) {
    var focused by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val textColor = if (isDark) Color(0xFFF4F4F8) else scheme.onSurface
    val mutedColor = if (isDark) Color(0xFFB9B9D2).copy(alpha = 0.85f) else scheme.onSurfaceVariant
    val accent = if (focused) RoseRed else mutedColor

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                textStyle = TextStyle(color = textColor, fontSize = 16.sp),
                cursorBrush = SolidColor(RoseRed),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .then(autofillType?.let { Modifier.contentType(it) } ?: Modifier),
            )
        }
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingIcon()
        }
    }
}

@Composable
fun AuthErrorText(message: String, modifier: Modifier = Modifier) {
    if (message.isEmpty()) return
    val error = MaterialTheme.colorScheme.error
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(error.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = error,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message,
            color = error,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

/**
 * 主操作按钮：液态玻璃 + 品牌色表面着色（对齐教程里 `onDrawSurface` 给玻璃上色的做法），
 * 因此按钮本身就是一块有折射的玫瑰色玻璃，而不是实心色块。
 */
@Composable
fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    pageBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    isDark: Boolean = isAppDarkTheme(),
) {
    val panel = LiquidGlassDefaults.authPrimaryButton
    val tint = when {
        !enabled -> RoseRed.copy(alpha = 0.22f)
        isDark -> RoseRed.copy(alpha = 0.62f)
        else -> RoseRed.copy(alpha = 0.90f)
    }
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        shape = RoundedCornerShape(27.dp),
        sampleBackdrop = pageBackdrop,
        backgroundAlpha = panel.tint.background(isDark),
        borderAlpha = panel.tint.border(isDark),
        highlightAlpha = panel.tint.highlight(isDark),
        borderColor = if (isDark) Color.White else RoseRed,
        liquidBlur = panel.liquid.blur,
        liquidLensHeight = panel.liquid.lensHeight,
        liquidLensAmount = panel.liquid.lensAmount,
        surfaceTint = tint,
        kyantHighlight = Highlight.Default.copy(alpha = if (enabled) 0.76f else 0.30f),
        kyantShadow = Shadow.Default.copy(alpha = if (enabled) 0.32f else 0.12f),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.72f),
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
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = if (enabled) RoseRed else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
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
    val isDark = isAppDarkTheme()
    val mutedColor =
        if (isDark) Color(0xFFB9B9D2).copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = prompt, color = mutedColor, fontSize = 14.sp)
        AuthTextLink(text = link, onClick = onLinkClick)
    }
}
