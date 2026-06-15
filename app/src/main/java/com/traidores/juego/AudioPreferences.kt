package com.traidores.juego

import android.content.Context
import android.content.SharedPreferences

object AudioPreferences {
    const val PREFS_NAME = "TraidoresPrefs"
    const val MUSIC_ENABLED = "music_on"
    const val EFFECTS_ENABLED = "effects_on"
    const val MUSIC_VOLUME = "music_volume"
    const val EFFECTS_VOLUME = "voice_volume"

    private const val LEGACY_SOUND_ENABLED = "sound_on"
    private const val DEFAULT_VOLUME = 80

    fun preferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also(::migrate)
    }

    fun isMusicEnabled(preferences: SharedPreferences): Boolean {
        migrate(preferences)
        return preferences.getBoolean(MUSIC_ENABLED, true)
    }

    fun areEffectsEnabled(preferences: SharedPreferences): Boolean {
        migrate(preferences)
        return preferences.getBoolean(EFFECTS_ENABLED, true)
    }

    fun musicVolume(preferences: SharedPreferences): Float {
        return preferences.getInt(MUSIC_VOLUME, DEFAULT_VOLUME).coerceIn(0, 100) / 100f
    }

    fun effectsVolume(preferences: SharedPreferences): Float {
        return preferences.getInt(EFFECTS_VOLUME, DEFAULT_VOLUME).coerceIn(0, 100) / 100f
    }

    private fun migrate(preferences: SharedPreferences) {
        if (preferences.contains(MUSIC_ENABLED) && preferences.contains(EFFECTS_ENABLED)) return

        val legacyEnabled = preferences.getBoolean(LEGACY_SOUND_ENABLED, true)
        preferences.edit().apply {
            if (!preferences.contains(MUSIC_ENABLED)) {
                putBoolean(MUSIC_ENABLED, legacyEnabled)
            }
            if (!preferences.contains(EFFECTS_ENABLED)) {
                putBoolean(EFFECTS_ENABLED, legacyEnabled)
            }
        }.apply()
    }
}
