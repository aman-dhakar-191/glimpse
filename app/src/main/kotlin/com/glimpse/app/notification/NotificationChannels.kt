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

    // Channel IDs are still per-name, but the channel no longer carries the
    // Morse pattern — ThinkingOfYouNotifier vibrates explicitly instead.
    //
    // Letting the channel do it looked right (it respects Do Not Disturb and
    // the per-channel vibration toggle for free) and failed on a real phone:
    // in Sound ringer mode most devices gate notification vibration behind a
    // separate system "Vibrate for notifications" setting that's commonly
    // off, so the nudge played its sound and buzzed nothing. A feature whose
    // entire point is being identifiable by touch cannot be at the mercy of
    // that toggle, so the buzz is driven directly and the channel is created
    // with vibration OFF to avoid a second, non-Morse buzz on top of it.
    //
    // The per-name ID stays because the channel is still the user-visible
    // row in system settings, and naming it after the person keeps that
    // legible.
    //
    // A channel's settings are frozen the moment it is first created —
    // createNotificationChannel on an existing ID cannot change its sound or
    // vibration afterward. So this version marker MUST be bumped whenever
    // any of the settings below change, or devices that already created the
    // old channel keep the old behaviour forever with no way to migrate
    // them. Superseded channels are deleted rather than left as debris.
    private const val MORSE_CHANNEL_PREFIX = "${THINKING_OF_YOU}_v3_"

    // Every prefix this feature has ever shipped, so superseded channels get
    // cleaned up instead of accumulating in system settings. Listed
    // explicitly rather than matched by a broad "thinking_of_you_" prefix:
    // that would also swallow any future sibling channel that happened to
    // share the stem, deleting it on the next nudge.
    private val SUPERSEDED_CHANNEL_PREFIXES = listOf(
        "${THINKING_OF_YOU}_morse_",
        "${THINKING_OF_YOU}_v2_"
    )

    fun thinkingOfYouChannelFor(context: Context, senderName: String): String {
        val letters = MorseVibration.normalize(senderName)
        if (letters.isEmpty()) return THINKING_OF_YOU

        val channelId = "$MORSE_CHANNEL_PREFIX$letters"
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                context.getString(R.string.thinking_of_you_morse_channel_name, senderName),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                // Silent by design. The Morse buzz IS the message — a chime
                // on top just announces to the room what is meant to be a
                // private thing only the person holding the phone notices.
                setSound(null, null)
                // The buzz comes from ThinkingOfYouNotifier, not from here,
                // so leaving this on would add a second non-Morse vibration
                // on top of the real one.
                enableVibration(false)
            }
        )

        // Without this, every nickname change leaves its old channel behind
        // forever in Settings → Notifications, alongside every superseded
        // version of it. Only one partner ever nudges this device, so any
        // other per-name channel is a previous spelling, or an older
        // version, of the same person's.
        manager.notificationChannels
            .map { it.id }
            .filter { id ->
                id != channelId &&
                    (id.startsWith(MORSE_CHANNEL_PREFIX) ||
                        SUPERSEDED_CHANNEL_PREFIXES.any { id.startsWith(it) })
            }
            .forEach { manager.deleteNotificationChannel(it) }

        return channelId
    }
}
