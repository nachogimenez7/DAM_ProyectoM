package com.traidores.juego

import android.content.Context

/**
 * Estado mínimo que debe sobrevivir al borrado de `TraidoresPrefs`.
 *
 * Play Games puede seguir autenticado en el dispositivo aunque la cuenta Firebase ya no
 * exista. Sin esta marca, el enlace automático del menú recrearía esa cuenta al volver.
 */
object AccountDeletionPreferences {
    private const val PREFS_NAME = "TraidoresAccountState"
    private const val PREF_AUTO_LINK_SUPPRESSED = "play_games_auto_link_suppressed"

    fun isAutoLinkSuppressed(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_AUTO_LINK_SUPPRESSED, false)
    }

    fun suppressAutoLink(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_AUTO_LINK_SUPPRESSED, true)
            .apply()
    }

    fun allowAutoLink(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_AUTO_LINK_SUPPRESSED)
            .apply()
    }
}
