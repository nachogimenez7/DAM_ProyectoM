package com.traidores.juego

import android.content.Context
import java.util.UUID

object OnlineTempIdentity {
    private const val PREFS_NAME = "TraidoresPrefs"
    private const val PREF_ONLINE_TEMP_UID = "online_temp_uid"

    fun getOrCreate(context: Context): String {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = preferences.getString(PREF_ONLINE_TEMP_UID, null)
            ?.takeIf { it.startsWith("local_") && it.length >= 14 }
        if (existing != null) return existing

        val generated = "local_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        preferences.edit()
            .putString(PREF_ONLINE_TEMP_UID, generated)
            .apply()
        return generated
    }
}
