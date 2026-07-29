package com.traidores.juego

import android.content.Context

/**
 * Silencio exclusivamente local. Guarda dos claves cuando puede (uid de Firebase y # publico)
 * para que siga funcionando en la sala actual y tambien al reencontrar una cuenta conocida.
 */
object LocalMuteStore {
    private const val PREFS_NAME = "TraidoresPrefs"
    private const val PREF_MUTED = "silenciados_locales"

    fun isMuted(context: Context, publicId: String, uid: String): Boolean {
        val saved = values(context)
        return keys(publicId, uid).any(saved::contains)
    }

    /** Devuelve el estado nuevo. */
    fun toggle(context: Context, publicId: String, uid: String): Boolean {
        val current = values(context).toMutableSet()
        val targetKeys = keys(publicId, uid)
        val willMute = targetKeys.none(current::contains)
        if (willMute) current += targetKeys else current -= targetKeys.toSet()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(PREF_MUTED, current)
            .apply()
        return willMute
    }

    private fun values(context: Context): Set<String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(PREF_MUTED, emptySet())
            ?.toSet()
            .orEmpty()

    private fun keys(publicId: String, uid: String): List<String> = buildList {
        publicId.takeIf(PlayerPublicIdentity::isValidPublicId)?.let { add("public:$it") }
        uid.takeIf(String::isNotBlank)?.let { add("uid:$it") }
    }
}
