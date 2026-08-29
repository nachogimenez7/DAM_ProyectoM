package com.traidores.juego

import android.content.Context

object VisualEffectsPreferences {
    const val KEY_REDUCED_EFFECTS = "reduced_visual_effects"

    fun isReduced(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REDUCED_EFFECTS, false)

    fun setReduced(context: Context, reduced: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REDUCED_EFFECTS, reduced)
            .apply()
    }

    private const val PREFS_NAME = "TraidoresPrefs"
}
