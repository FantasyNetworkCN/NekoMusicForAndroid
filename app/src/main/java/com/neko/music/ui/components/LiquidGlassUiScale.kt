package com.neko.music.ui.components

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neko.music.config.AppConfig

/**
 * 用户可调液态玻璃强度（乘在 [GlassSurface] 与各页传入的 alpha / blur / lens 上）。
 * 默认 1f 与集中配置 [LiquidGlassDefaults] 一致。
 */
@Immutable
data class LiquidGlassUiScale(
    val tintStrength: Float = 1f,
    val blurStrength: Float = 1f,
    val lensHeightStrength: Float = 1f,
    val lensAmountStrength: Float = 1f,
) {
    companion object {
        const val StrengthMin = 0.35f
        const val StrengthMax = 1.85f
    }
}

val LocalLiquidGlassUiScale = compositionLocalOf { LiquidGlassUiScale() }

/**
 * 为 false 时 [GlassSurface]、[NavigationGlassSlider] 不使用 Kyant 硬件录屏折射（磨砂卡片 / 拇指占位）。
 * 由 [AppConfig.PrefConfig.KEY_LIQUID_GLASS_HARDWARE_EFFECTS] 驱动，默认 true。
 */
val LocalLiquidGlassHardwareEffectsEnabled = compositionLocalOf { true }

fun SharedPreferences.readLiquidGlassUiScale(): LiquidGlassUiScale {
    fun f(key: String) =
        getFloat(key, AppConfig.PrefConfig.DEFAULT_LIQUID_GLASS_STRENGTH)
            .coerceIn(LiquidGlassUiScale.StrengthMin, LiquidGlassUiScale.StrengthMax)
    return LiquidGlassUiScale(
        tintStrength = f(AppConfig.PrefConfig.KEY_LIQUID_GLASS_TINT),
        blurStrength = f(AppConfig.PrefConfig.KEY_LIQUID_GLASS_BLUR),
        lensHeightStrength = f(AppConfig.PrefConfig.KEY_LIQUID_GLASS_LENS_HEIGHT),
        lensAmountStrength = f(AppConfig.PrefConfig.KEY_LIQUID_GLASS_LENS_AMOUNT),
    )
}

fun Dp.scaledBy(strength: Float): Dp =
    (this.value * strength).dp.coerceIn(0.5.dp, 56.dp)
