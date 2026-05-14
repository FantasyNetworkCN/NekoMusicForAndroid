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
    val sectionGlass = LiquidGlassDefaults.shareSheetSection
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundAlpha = sectionGlass.tint.background(isDark),
        borderAlpha = sectionGlass.tint.border(isDark),
        highlightAlpha = sectionGlass.tint.highlight(isDark),
        borderColor = if (isDark) {
            SakuraPink.copy(alpha = LiquidGlassDefaults.shareSheetSectionDarkBorderSakuraAlpha)
        } else {
            scheme.outline
        },
        liquidBlur = sectionGlass.liquid.blur,
        liquidLensHeight = sectionGlass.liquid.lensHeight,
        liquidLensAmount = sectionGlass.liquid.lensAmount,
    ) {
        content()
    }
}
