package com.neko.music.util

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.neko.music.debug.NekoLog as Log

internal object AppHaptics {
    private const val ENABLED_KEY = "haptics_enabled"
    private const val STRENGTH_KEY = "haptics_strength"
    const val MIN_STRENGTH = 10
    const val MAX_STRENGTH = 100
    private const val DEFAULT_STRENGTH = 50

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = preferences(context).getBoolean(ENABLED_KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(ENABLED_KEY, enabled).apply()
    }

    fun strength(context: Context): Int = preferences(context).getInt(STRENGTH_KEY, DEFAULT_STRENGTH)
        .coerceIn(MIN_STRENGTH, MAX_STRENGTH)

    fun setStrength(context: Context, strength: Int) {
        preferences(context).edit()
            .putInt(STRENGTH_KEY, strength.coerceIn(MIN_STRENGTH, MAX_STRENGTH)).apply()
    }

    fun hasVibrator(context: Context): Boolean = vibrator(context)?.hasVibrator() == true

    fun supportsStrength(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        vibrator(context)?.let { it.hasVibrator() && it.hasAmplitudeControl() } == true

    @Suppress("DEPRECATION") // 兼容 API 24–32；Android 13 起优先由系统按触摸用途处理。
    fun pulse(context: Context, longPress: Boolean = false) {
        if (!isEnabled(context) || Settings.System.getInt(
                context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1,
            ) == 0
        ) return

        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        val duration = if (longPress) 20L else 12L
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = if (vibrator.hasAmplitudeControl()) {
                    amplitudeFor(strength(context))
                } else {
                    VibrationEffect.DEFAULT_AMPLITUDE
                }
                val effect = VibrationEffect.createOneShot(duration, amplitude)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
                } else {
                    vibrator.vibrate(effect, touchAudioAttributes)
                }
            } else {
                vibrator.vibrate(duration, touchAudioAttributes)
            }
        } catch (e: SecurityException) {
            Log.w("AppHaptics", "触感反馈不可用", e)
        }
    }

    internal fun amplitudeFor(strength: Int): Int =
        (strength.coerceIn(MIN_STRENGTH, MAX_STRENGTH) * 255 + 50) / 100

    private val touchAudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    @Suppress("DEPRECATION") // Android 12 以前使用 Vibrator 服务。
    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
}
