package com.glimpse.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.glimpse.app.R

// Registered once from App.onCreate() so FCMService/WidgetUpdateService/
// StreakCheckWorker/UpdateCheckWorker don't each need their own lazy
// createNotificationChannel() call right before posting. Channel IDs are
// stable identifiers Android persists the user's per-channel notification
// settings against — don't change existing ones without good reason.
object NotificationChannels {
    const val MESSAGES = "glimpse_messages"
    const val STREAK_REMINDER = "streak_reminder"
    const val WIDGET_SYNC = "widget_sync"
    const val UPDATE_AVAILABLE = "update_available"

    fun registerAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        // Distinct names so Android Settings → Apps → Glimpse → Notifications
        // shows 4 identifiable rows instead of three indistinguishable
        // "Glimpse" entries (all had literally used the app name) plus
        // "App updates" — re-registering with the same channel ID just
        // updates the existing channel's name, no migration needed.
        manager.createNotificationChannel(
            NotificationChannel(MESSAGES, context.getString(R.string.messages_channel_name), NotificationManager.IMPORTANCE_HIGH)
        )
        manager.createNotificationChannel(
            NotificationChannel(STREAK_REMINDER, context.getString(R.string.streak_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(WIDGET_SYNC, context.getString(R.string.widget_sync_channel_name), NotificationManager.IMPORTANCE_MIN)
        )
        manager.createNotificationChannel(
            NotificationChannel(
                UPDATE_AVAILABLE,
                context.getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }
}
