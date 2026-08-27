package com.traidores.juego

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging

object NotificationPreferences {
    const val TOPIC_BETA_TESTERS = "traidores_beta_testers"

    private const val PREFS_NAME = "TraidoresPrefs"
    private const val PREF_ENABLED = "notifications_enabled"
    private const val PREF_INVITATION_SEEN = "notifications_invitation_seen_v1"
    private const val LOG_TAG = "TraidoresFCM"

    fun isEnabled(context: Context): Boolean =
        preferences(context).getBoolean(PREF_ENABLED, false)

    fun wasInvitationSeen(context: Context): Boolean =
        preferences(context).getBoolean(PREF_INVITATION_SEEN, false)

    fun markInvitationSeen(context: Context) {
        preferences(context).edit().putBoolean(PREF_INVITATION_SEEN, true).apply()
    }

    fun setEnabled(
        context: Context,
        enabled: Boolean,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        preferences(context).edit().putBoolean(PREF_ENABLED, enabled).apply()
        val operation = if (enabled) {
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_BETA_TESTERS)
        } else {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC_BETA_TESTERS)
        }
        operation
            .addOnSuccessListener {
                if (enabled) logRegistrationTokenForTesting(context)
                onComplete?.invoke(true)
            }
            .addOnFailureListener { error ->
                Log.w(LOG_TAG, "No se pudo actualizar la suscripción de novedades.", error)
                onComplete?.invoke(false)
            }
    }

    fun restoreSubscription(context: Context) {
        if (!isEnabled(context) || !canPostNotifications(context)) return
        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_BETA_TESTERS)
    }

    fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun logRegistrationTokenForTesting(context: Context) {
        val isDebuggable = context.applicationInfo.flags and
            ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!isDebuggable) return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> Log.d(LOG_TAG, "Token de prueba FCM: $token") }
            .addOnFailureListener { error ->
                Log.w(LOG_TAG, "No se pudo obtener el token de prueba FCM.", error)
            }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
