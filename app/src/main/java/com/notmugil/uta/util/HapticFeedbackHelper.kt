package com.notmugil.uta.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticFeedbackHelper {

    enum class HapticType {
        LIGHT,
        CLICK,
        HEAVY,
        SUCCESS,
        WARNING
    }

    fun perform(context: Context?, type: HapticType = HapticType.LIGHT) {
        val targetContext = context ?: return
        try {
            val isEnabled = targetContext
                .getSharedPreferences("uta_app_preferences", Context.MODE_PRIVATE)
                .getBoolean("haptic_feedback", true)
            if (!isEnabled) return

            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = targetContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                targetContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val effectId = when (type) {
                        HapticType.LIGHT -> VibrationEffect.EFFECT_TICK
                        HapticType.CLICK -> VibrationEffect.EFFECT_CLICK
                        HapticType.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
                        HapticType.SUCCESS -> VibrationEffect.EFFECT_CLICK
                        HapticType.WARNING -> VibrationEffect.EFFECT_HEAVY_CLICK
                    }
                    vibrator.vibrate(VibrationEffect.createPredefined(effectId))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val (duration, amplitude) = when (type) {
                        HapticType.LIGHT -> 8L to 40
                        HapticType.CLICK -> 18L to 90
                        HapticType.HEAVY -> 30L to 160
                        HapticType.SUCCESS -> 20L to 100
                        HapticType.WARNING -> 35L to 180
                    }
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(15L)
                }
            }
        } catch (_: Exception) {}
    }
}

