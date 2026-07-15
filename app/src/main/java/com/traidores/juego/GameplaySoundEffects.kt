package com.traidores.juego

import android.content.Context

object GameplaySoundEffects {
    fun preload(context: Context) {
        ShortSoundPool.preload(context, GameSound.values().map { it.res })
    }

    fun play(context: Context, soundRes: Int, volumeScale: Float = 1f) {
        val preferences = AudioPreferences.preferences(context)
        if (!AudioPreferences.areEffectsEnabled(preferences)) return

        val volume = AudioPreferences.effectsVolume(preferences)
        if (volume <= 0f) return

        val effectiveVolume = volume * volumeScale.coerceIn(0f, 1f)
        ShortSoundPool.play(context, soundRes, effectiveVolume)
    }
}
