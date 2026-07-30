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
    const val SENDING = "message_sending"
    const val SEND_RESULT = "send_result"
    const val THINKING_OF_YOU = "thinking_of_you"

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
        manager.createNotificationChannel(
            NotificationChannel(SENDING, context.getString(R.string.sending_channel_name), NotificationManager.IMPORTANCE_LOW)
        )
        // Default (not low, like SENDING above) — this is the actual outcome
        // the sending notification was a placeholder for, and it needs to
        // still be noticeable if the app was closed for the whole send.
        manager.createNotificationChannel(
            NotificationChannel(SEND_RESULT, context.getString(R.string.send_result_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        )
        // Its own channel (not MESSAGES) purely so it can carry a distinct
        // vibration pattern — on Android O+ a per-notification setVibrate()
        // is ignored in favor of whatever the channel itself was created
        // with, so a genuinely different feel for this one requires a
        // separate channel, not just different Builder calls. This generic
        // one is the fallback for a nudge whose sender name didn't come
        // through; the named Morse channels below are the normal path.
        manager.createNotificationChannel(
            NotificationChannel(THINKING_OF_YOU, context.getString(R.string.thinking_of_you_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 100, 80, 100, 80, 200)
            }
        )
    }

    // The same "pattern is fixed at channel-creation time" constraint above
    // is exactly why this can't be one channel with a swapped pattern: a
    // nudge from Komal and a nudge from Aman have to arrive on two
    // different channels to feel different. The ID is derived from the
    // name, so a given ID's pattern is always the one it was created with
    // and there's no stale-pattern problem to migrate around.
    fun thinkingOfYouChannelFor(context: Context, senderName: String): String {
        val letters = MorseVibration.normalize(senderName)
        val pattern = MorseVibration.patternFor(senderName)
        if (letters.isEmpty() || pattern == null) return THINKING_OF_YOU

        val channelId = "${THINKING_OF_YOU}_morse_$letters"
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                context.getString(R.string.thinking_of_you_morse_channel_name, senderName),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                vibrationPattern = pattern
            }
        )

        // Without this, every display-name change leaves its old channel
        // behind forever in Settings → Notifications. Only one partner ever
        // nudges this device, so any *other* Morse channel is by definition
        // a previous spelling of the same person.
        manager.notificationChannels
            .map { it.id }
            .filter { it.startsWith("${THINKING_OF_YOU}_morse_") && it != channelId }
            .forEach { manager.deleteNotificationChannel(it) }

        return channelId
    }
}
