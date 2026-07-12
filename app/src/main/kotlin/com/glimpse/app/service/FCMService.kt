package com.glimpse.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.glimpse.app.MainActivity
import com.glimpse.app.R
import com.glimpse.app.data.QuietHoursStore
import com.glimpse.app.data.repository.AuthRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        AuthRepository().registerFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // The Current Message widget refreshes itself via
        // WidgetUpdateService's live Firebase listener; this just makes sure
        // that listener is running when a push arrives while the app/widget
        // service isn't already active.
        WidgetSyncTrigger.requestSync(this)

        // The Cloud Function (functions/index.js) sends a data-only message
        // (no top-level "notification" key), so onMessageReceived always
        // runs here instead of the system silently auto-displaying it —
        // this builds the visible notification ourselves.
        val title = message.data["title"] ?: return
        val body = message.data["body"].orEmpty()
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        // The widget already refreshed unconditionally above — this only
        // suppresses the visible popup/sound/vibration during this device's
        // configured quiet hours, not the underlying content update.
        if (QuietHoursStore.isQuietNow(this)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "glimpse_messages"
        private const val NOTIFICATION_ID = 2001
    }
}
