package com.traidores.juego

import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TraidoresMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.first_beta_notification_title)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: getString(R.string.first_beta_notification_body)
        TraidoresNotifications.show(this, title, body)
    }

    override fun onNewToken(token: String) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d(LOG_TAG, "Nuevo token de prueba FCM: $token")
        }
        if (NotificationPreferences.isEnabled(this)) {
            NotificationPreferences.restoreSubscription(this)
        }
    }

    private companion object {
        const val LOG_TAG = "TraidoresFCM"
    }
}
