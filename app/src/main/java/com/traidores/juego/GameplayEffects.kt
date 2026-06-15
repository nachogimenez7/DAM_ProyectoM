package com.traidores.juego

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
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
        val effectsOn = AudioPreferences.areEffectsEnabled(prefs)
        val volume = (AudioPreferences.effectsVolume(prefs) * 100).toInt()
        if (effectsOn && volume > 0) {
            val tone = when (effect) {
                GameplayEffect.SELECT -> ToneGenerator.TONE_PROP_BEEP
                GameplayEffect.CONFIRM -> ToneGenerator.TONE_PROP_ACK
                GameplayEffect.ERROR -> ToneGenerator.TONE_PROP_NACK
                GameplayEffect.PANEL -> ToneGenerator.TONE_PROP_PROMPT
                GameplayEffect.REVEAL -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
                GameplayEffect.CHAT -> ToneGenerator.TONE_PROP_BEEP2
                GameplayEffect.COUNTDOWN -> ToneGenerator.TONE_CDMA_PIP
            }
            val duration = when (effect) {
                GameplayEffect.CONFIRM, GameplayEffect.REVEAL -> 110
                GameplayEffect.ERROR -> 140
                else -> 65
            }
            ToneGenerator(AudioManager.STREAM_MUSIC, volume.coerceIn(1, 100)).apply {
                startTone(tone, duration)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    { release() },
                    duration + 40L
                )
            }
        }
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
