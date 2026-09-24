package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_MESSAGES_ID = "chatmesh_p2p_messages"
        const val CHANNEL_CALLS_ID = "chatmesh_p2p_calls"
        private const val NOTIFICATION_ID_BASE_MESSAGES = 1000
        private const val NOTIFICATION_ID_CALLS = 2000
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val messageChannel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                "Mensajes ChatMesh P2P",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de mensajes de texto, fotos y audios en la red P2P"
                enableVibration(true)
                enableLights(true)
            }

            val callChannel = NotificationChannel(
                CHANNEL_CALLS_ID,
                "Llamadas ChatMesh P2P",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de llamadas entrantes de voz y video en WiFi Direct"
                enableVibration(true)
                enableLights(true)
                val defaultRingtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                setSound(defaultRingtone, null)
            }

            notificationManager.createNotificationChannel(messageChannel)
            notificationManager.createNotificationChannel(callChannel)
        }
    }

    fun showIncomingMessageNotification(
        senderPhone: String,
        senderName: String,
        messageText: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_CONTACT_PHONE", senderPhone)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            senderPhone.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentTitle(senderName.ifEmpty { senderPhone })
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = NOTIFICATION_ID_BASE_MESSAGES + (senderPhone.hashCode() and 0x7FFF)
        notificationManager.notify(notificationId, notification)
    }

    fun showIncomingCallNotification(
        callerPhone: String,
        callerName: String,
        isVideo: Boolean
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_CONTACT_PHONE", callerPhone)
            putExtra("EXTRA_INCOMING_CALL", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_CALLS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentTitle("📞 Llamada entrante P2P")
            .setContentText("${callerName.ifEmpty { callerPhone }} te está llamando por WiFi Direct")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setSound(ringtoneUri)
            .setVibrate(longArrayOf(0, 500, 500, 500, 500))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_CALLS, notification)
    }

    fun cancelCallNotification() {
        notificationManager.cancel(NOTIFICATION_ID_CALLS)
    }
}
