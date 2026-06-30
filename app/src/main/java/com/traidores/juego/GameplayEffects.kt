package com.traidores.juego

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class GameplayEffect {
    SELECT,
    CONFIRM,
    ERROR,
    PANEL,
    REVEAL,
    CHAT,
    COUNTDOWN
}

object GameplayEffects {
    fun play(context: Context, effect: GameplayEffect) {
        val prefs = AudioPreferences.preferences(context)
        vibrate(context, effect, prefs.getBoolean("vibration_on", false))
    }

    private fun vibrate(context: Context, effect: GameplayEffect, enabled: Boolean) {
        if (!enabled) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return

        val duration = when (effect) {
            GameplayEffect.SELECT, GameplayEffect.PANEL, GameplayEffect.CHAT -> 18L
            GameplayEffect.COUNTDOWN -> 28L
            GameplayEffect.CONFIRM, GameplayEffect.REVEAL -> 42L
            GameplayEffect.ERROR -> 70L
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    duration,
                    if (effect == GameplayEffect.ERROR) 150 else VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}
