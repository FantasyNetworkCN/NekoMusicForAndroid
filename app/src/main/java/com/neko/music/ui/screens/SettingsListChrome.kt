package com.neko.music.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.music.R
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LiquidGlassSwitch
import com.neko.music.ui.icons.NekoIcon
import com.neko.music.ui.icons.NekoSymbol
import com.neko.music.ui.icons.NekoSymbolIcon
import com.neko.music.ui.theme.isAppDarkTheme

/** 分组列表：标题在卡片外，图标与文字对齐，分隔线从正文起始处开始。 */
@Composable
fun SettingSection(
    title: String,
    useDarkAppearance: Boolean? = null,
    collapsible: Boolean = false,
    forceExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(true) }
    val visible = !collapsible || expanded || forceExpanded
    val rotation by animateFloatAsState(if (visible) 90f else 0f, label = "settings_section")
    val state = stringResource(if (visible) R.string.settings_expanded else R.string.settings_collapsed)
    val tint = LiquidGlassDefaults.screenListCard
    val dark = useDarkAppearance ?: isAppDarkTheme()
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (collapsible) Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !forceExpanded, role = Role.Button) { expanded = !expanded }
                        .semantics(mergeDescendants = true) { stateDescription = state }
                    else Modifier,
                )
                .heightIn(min = if (collapsible) 48.dp else 40.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f).semantics { heading() },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (collapsible) {
                NekoSymbolIcon(
                    NekoSymbol.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp).rotate(rotation),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f),
                )
            }
        }
        if (visible) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                material3Container = MaterialTheme.colorScheme.surfaceContainerLow,
                backgroundAlpha = tint.background(dark),
                borderAlpha = tint.border(dark),
                highlightAlpha = tint.highlight(dark),
            ) {
                Column(Modifier.clip(RoundedCornerShape(22.dp)), content = content)
            }
        }
    }
}

@Composable
fun SettingItem(
    symbol: NekoSymbol,
    title: String,
    subtitle: String = "",
    showLoading: Boolean = false,
    showSeparator: Boolean = false,
    onClick: () -> Unit = {},
) {
    SettingsActionRow(title, subtitle, showLoading, showSeparator, onClick) {
        NekoSymbolIcon(symbol, null, Modifier.size(22.dp), MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    loading: Boolean,
    separator: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    SettingsListRow(
        title, subtitle, separator,
        modifier = Modifier.clickable(enabled = !loading, role = Role.Button) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        },
        leading = leading,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            NekoIcon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                Modifier.size(16.dp), MaterialTheme.colorScheme.primary.copy(alpha = .85f),
            )
        }
    }
}

@Composable
fun SettingSwitchItem(
    symbol: NekoSymbol,
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    useDarkAppearance: Boolean? = null,
    showSeparator: Boolean = false,
    enabled: Boolean = true,
) {
    SettingsToggleRow(title, subtitle, checked, onCheckedChange, useDarkAppearance, showSeparator, enabled) {
        NekoSymbolIcon(symbol, null, Modifier.size(22.dp), MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    dark: Boolean?,
    separator: Boolean,
    enabled: Boolean,
    leading: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val change: (Boolean) -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        onCheckedChange(it)
    }
    SettingsListRow(
        title, subtitle, separator,
        modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = change),
        leading = leading,
    ) {
        Box(
            Modifier.width(72.dp).heightIn(min = 48.dp).clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            // 保留液态开关的拖动手势；无障碍状态由整行提供，避免重复朗读。
            LiquidGlassSwitch(checked, change, isDarkChrome = dark ?: isAppDarkTheme(), enabled = enabled)
        }
    }
}

@Composable
private fun SettingsListRow(
    title: String,
    subtitle: String,
    separator: Boolean,
    modifier: Modifier,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val lineColor = scheme.outlineVariant.copy(alpha = .45f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (separator) {
                    drawLine(
                        lineColor,
                        Offset(if (isRtl) 16.dp.toPx() else 56.dp.toPx(), 0f),
                        Offset(size.width - if (isRtl) 56.dp.toPx() else 16.dp.toPx(), 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .then(modifier)
            .semantics(mergeDescendants = true) {}
            .heightIn(min = if (subtitle.isEmpty()) 58.dp else 74.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = scheme.onSurface, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = scheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
internal fun SettingsSearchField(value: String, onValueChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val placeholder = stringResource(R.string.settings_search_hint)
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        material3Container = scheme.surfaceContainerHigh,
        backgroundAlpha = .22f,
        borderAlpha = .10f,
        highlightAlpha = .06f,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NekoSymbolIcon(NekoSymbol.Search, null, Modifier.size(20.dp), scheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).semantics { contentDescription = placeholder },
                singleLine = true,
                textStyle = TextStyle(color = scheme.onSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                decorationBox = { field ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, color = scheme.onSurfaceVariant, fontSize = 16.sp)
                        field()
                    }
                },
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    NekoSymbolIcon(
                        NekoSymbol.Xmark, stringResource(R.string.settings_search_clear),
                        Modifier.size(16.dp), scheme.onSurfaceVariant,
                    )
                }
            } else Spacer(Modifier.width(12.dp))
        }
    }
}
