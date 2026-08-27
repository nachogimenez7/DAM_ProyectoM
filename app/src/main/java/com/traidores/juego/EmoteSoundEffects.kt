package com.traidores.juego

import android.content.Context
import android.os.SystemClock

/** Canal de emotes precargado, con politica "el ultimo gana" y limite de frecuencia. */
object EmoteSoundEffects {
    private const val MIN_INTERVAL_MS = 300L

    private var lastPlayAtMs = 0L

    private val soundResources = listOf(
        R.raw.sfx_emote_happy,
        R.raw.sfx_emote_sad,
        R.raw.sfx_emote_suspicious,
        R.raw.sfx_emote_angry,
        R.raw.sfx_emote_premium_hermosa_manana,
        R.raw.sfx_emote_premium_mate,
        R.raw.sfx_emote_premium_dormida,
        R.raw.sfx_emote_premium_genio,
        R.raw.sfx_emote_premium_medico_timido,
        R.raw.sfx_emote_premium_desertor_lengua,
        R.raw.sfx_emote_premium_oraculo_mmm_nie,
        R.raw.sfx_emote_premium_six_seven
    )

    fun preload(context: Context) {
        ShortSoundPool.preload(context, soundResources)
    }

    fun play(context: Context, emotionKey: String, volumeScale: Float = 1f) {
        val soundRes = soundResFor(emotionKey) ?: return
        val preferences = AudioPreferences.preferences(context)
        if (!AudioPreferences.areEffectsEnabled(preferences)) return
        val volume = AudioPreferences.effectsVolume(preferences)
        if (volume <= 0f) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastPlayAtMs < MIN_INTERVAL_MS) return
        lastPlayAtMs = now

        ShortSoundPool.play(
            context = context,
            soundRes = soundRes,
            volume = (volume * volumeScale).coerceIn(0f, 1f),
            replaceChannel = "emote"
        )
    }

    private fun soundResFor(emotionKey: String): Int? = when (emotionKey) {
        "happy" -> R.raw.sfx_emote_happy
        "sad" -> R.raw.sfx_emote_sad
        "suspicious" -> R.raw.sfx_emote_suspicious
        "angry" -> R.raw.sfx_emote_angry
        "premium_hermosa_manana" -> R.raw.sfx_emote_premium_hermosa_manana
        "premium_mate" -> R.raw.sfx_emote_premium_mate
        "premium_dormida" -> R.raw.sfx_emote_premium_dormida
        "premium_genio" -> R.raw.sfx_emote_premium_genio
        "premium_medico_timido" -> R.raw.sfx_emote_premium_medico_timido
        "premium_desertor_lengua" -> R.raw.sfx_emote_premium_desertor_lengua
        "premium_oraculo_mmm_nie" -> R.raw.sfx_emote_premium_oraculo_mmm_nie
        "premium_six_seven" -> R.raw.sfx_emote_premium_six_seven
        else -> null
    }
}
