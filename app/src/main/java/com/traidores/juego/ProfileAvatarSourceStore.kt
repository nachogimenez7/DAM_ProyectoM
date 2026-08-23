package com.traidores.juego

import android.content.Context

enum class ProfileAvatarSource(val storedValue: String) {
    AUTOMATIC("automatic"),
    PLAY_GAMES("play_games"),
    ILLUSTRATED("illustrated"),
    LOCAL_PHOTO("local_photo")
}

/**
 * Recuerda si el jugador eligió explícitamente su avatar.
 *
 * Sin esta marca, consultar Play Juegos en cada inicio volvería a imponer la foto de Google
 * incluso después de que el usuario eligiera un personaje ilustrado.
 */
object ProfileAvatarSourceStore {
    private const val PREF_SOURCE = "profile_avatar_source"

    fun selection(context: Context): ProfileAvatarSource {
        val value = context.getSharedPreferences(
            ProfileActivity.PREFS_NAME,
            Context.MODE_PRIVATE
        ).getString(PREF_SOURCE, null)
        return ProfileAvatarSource.entries.firstOrNull { it.storedValue == value }
            ?: ProfileAvatarSource.AUTOMATIC
    }

    fun allowsAutomaticPlayGamesPhoto(context: Context): Boolean {
        val selected = selection(context)
        if (selected == ProfileAvatarSource.AUTOMATIC) {
            val alreadyUsesLocalPhoto = context.getSharedPreferences(
                ProfileActivity.PREFS_NAME,
                Context.MODE_PRIVATE
            ).getBoolean(ProfileActivity.PREF_LOCAL_PHOTO_ENABLED, false)
            if (alreadyUsesLocalPhoto) {
                saveSelection(context, ProfileAvatarSource.LOCAL_PHOTO)
                return false
            }
        }
        return selected in setOf(ProfileAvatarSource.AUTOMATIC, ProfileAvatarSource.PLAY_GAMES)
    }

    fun saveSelection(context: Context, source: ProfileAvatarSource) {
        context.getSharedPreferences(ProfileActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_SOURCE, source.storedValue)
            .apply()
    }
}
