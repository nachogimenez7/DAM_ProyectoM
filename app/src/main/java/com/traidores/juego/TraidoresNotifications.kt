package com.traidores.juego

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object TraidoresNotifications {
    private const val NOTIFICATION_ID_NEWS = 4101

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            context.getString(R.string.notification_channel_beta_id),
            context.getString(R.string.notification_channel_beta_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_beta_description)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun show(context: Context, title: String, message: String) {
        if (!NotificationPreferences.isEnabled(context) ||
            !NotificationPreferences.canPostNotifications(context)
        ) return

        createChannel(context)
        val openGameIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPENED_FROM_NOTIFICATION, true)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            openGameIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(
            context,
            context.getString(R.string.notification_channel_beta_id)
        )
            .setSmallIcon(R.drawable.ic_notification_traidores)
            .setColor(context.getColor(R.color.accent_gold))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_NEWS, notification)
    }

    const val EXTRA_OPENED_FROM_NOTIFICATION = "opened_from_notification"
}
