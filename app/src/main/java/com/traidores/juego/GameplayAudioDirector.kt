package com.traidores.juego

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class HapticLevel {
    NONE,
    LIGHT,
    MEDIUM,
    STRONG
}

enum class GameSound(
    val res: Int,
    val haptic: HapticLevel,
    val relativeVolume: Float = 1f
) {
    NIGHT_FALL(R.raw.sfx_night_fall, HapticLevel.LIGHT, 0.85f),
    DAWN(R.raw.sfx_dawn, HapticLevel.LIGHT, 0.9f),
    ELIMINATION(R.raw.sfx_death_elevenlabs, HapticLevel.STRONG, 0.78f),
    EXPULSION(R.raw.sfx_expulsion, HapticLevel.STRONG, 0.82f),
    SILENCE(R.raw.sfx_elimination, HapticLevel.MEDIUM, 0.9f),
    NO_DEATH(R.raw.sfx_no_death, HapticLevel.LIGHT, 0.9f),
    VOTE_CAST(R.raw.sfx_vote_cast, HapticLevel.LIGHT, 0.72f),
    TIE_BREAK(R.raw.sfx_tie_break, HapticLevel.MEDIUM, 0.82f),
    CARD_DEAL(R.raw.sfx_card_deal, HapticLevel.LIGHT),
    ORACLE(R.raw.oracle_ability, HapticLevel.MEDIUM, 0.86f),
    PAYADOR(R.raw.payador_ability, HapticLevel.MEDIUM, 0.86f),
    JESTER(R.raw.jester_victory, HapticLevel.MEDIUM, 0.85f)
}

enum class GameplayFeedbackCue(val haptic: HapticLevel) {
    SELECT(HapticLevel.LIGHT),
    CONFIRM(HapticLevel.MEDIUM),
    ERROR(HapticLevel.STRONG),
    PANEL(HapticLevel.LIGHT),
    REVEAL(HapticLevel.MEDIUM),
    CHAT(HapticLevel.LIGHT),
    COUNTDOWN(HapticLevel.MEDIUM),
    EMOTE(HapticLevel.LIGHT)
}

object GameplayAudioDirector {
    private const val PREF_VIBRATION_ON = "vibration_on"

    fun play(context: Context, sound: GameSound) {
        GameplaySoundEffects.play(context, sound.res, sound.relativeVolume)
        vibrate(context, sound.haptic)
    }

    fun feedback(context: Context, cue: GameplayFeedbackCue) {
        vibrate(context, cue.haptic)
    }

    private fun vibrate(context: Context, level: HapticLevel) {
        if (level == HapticLevel.NONE) return
        val preferences = AudioPreferences.preferences(context)
        if (!preferences.getBoolean(PREF_VIBRATION_ON, false)) return

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (level) {
                HapticLevel.NONE -> return
                HapticLevel.LIGHT -> VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE)
                HapticLevel.MEDIUM -> VibrationEffect.createOneShot(42L, VibrationEffect.DEFAULT_AMPLITUDE)
                HapticLevel.STRONG -> VibrationEffect.createWaveform(
                    longArrayOf(0L, 60L, 40L, 80L),
                    intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE),
                    -1
                )
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            when (level) {
                HapticLevel.NONE -> return
                HapticLevel.LIGHT -> vibrator.vibrate(20L)
                HapticLevel.MEDIUM -> vibrator.vibrate(42L)
                HapticLevel.STRONG -> vibrator.vibrate(longArrayOf(0L, 60L, 40L, 80L), -1)
            }
        }
    }
}
