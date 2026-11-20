package com.neko.music.util

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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

    fun supportsStrength(context: Context): Boolean = hasVibrator(context)

    @Suppress("DEPRECATION") // Android 13 以下需要 AudioAttributes 重载。
    fun pulse(context: Context, longPress: Boolean = false, force: Boolean = false) {
        if (!force && !isEnabled(context)) return

        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        val strength = strength(context)
        val duration = durationFor(strength, longPress)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = if (vibrator.hasAmplitudeControl()) {
                    amplitudeFor(strength)
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
        64 + (strength.coerceIn(MIN_STRENGTH, MAX_STRENGTH) - MIN_STRENGTH) * 191 /
            (MAX_STRENGTH - MIN_STRENGTH)

    internal fun durationFor(strength: Int, longPress: Boolean = false): Long {
        val normalized = strength.coerceIn(MIN_STRENGTH, MAX_STRENGTH) - MIN_STRENGTH
        val regularDuration = 22L + normalized * 38L / (MAX_STRENGTH - MIN_STRENGTH)
        return regularDuration + if (longPress) 16L else 0L
    }

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
