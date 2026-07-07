package com.traidores.juego

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID

object OnlineTempIdentity {
    private const val PREFS_NAME = "TraidoresPrefs"
    private const val PREF_ONLINE_TEMP_UID = "online_temp_uid"

    fun getOrCreate(context: Context): String {
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            persist(context, uid)
            return uid
        }
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = preferences.getString(PREF_ONLINE_TEMP_UID, null)
            ?.takeIf { (it.startsWith("local_") && it.length >= 22) || it.length >= 20 }
        if (existing != null) return existing

        val generated = "local_${UUID.randomUUID().toString().replace("-", "").take(24)}"
        persist(context, generated)
        return generated
    }

    fun ensureAuthenticated(context: Context): Task<String> {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.uid?.let { uid ->
            persist(context, uid)
            return Tasks.forResult(uid)
        }
        return auth.signInAnonymously().continueWith { task ->
            val uid = task.result?.user?.uid
                ?: throw task.exception
                ?: IllegalStateException("No se pudo iniciar sesion anonima.")
            persist(context, uid)
            uid
        }
    }

    private fun persist(context: Context, uid: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_ONLINE_TEMP_UID, uid)
            .apply()
    }
}
