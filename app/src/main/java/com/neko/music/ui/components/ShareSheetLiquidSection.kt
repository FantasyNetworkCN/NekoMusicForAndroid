package com.neko.music.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neko.music.ui.theme.SakuraPink

/** 分享面板内分区：叠一层真液态，与播放页 [ShareDialog] 一致。 */
@Composable
fun ShareSheetLiquidSection(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundAlpha = if (isDark) 0.17f else 0.22f,
        borderAlpha = if (isDark) 0.22f else 0.17f,
        highlightAlpha = if (isDark) 0.07f else 0.09f,
        borderColor = if (isDark) SakuraPink.copy(alpha = 0.42f) else scheme.outline,
        liquidBlur = 9.dp,
        liquidLensHeight = 12.dp,
        liquidLensAmount = 22.dp,
    ) {
        content()
    }
}
