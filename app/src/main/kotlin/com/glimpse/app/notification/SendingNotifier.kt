package com.glimpse.app.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.glimpse.app.MainActivity
import com.glimpse.app.R

// Transient "Sending message…" status, shown from SendMessageWorker while
// a send is in flight and cancelled as soon as it finishes (success or
// failure) — silent/low-priority since it's just visible progress, not
// something that needs to interrupt. Same notification ID as
// PhotoSendService's own foreground notification (they're never both
// showing at once in this single-compose-screen app) — that one needs to
// be built via Service.startForeground() directly rather than through
// this helper, so it doesn't route through here.
object SendingNotifier {
    private const val NOTIFICATION_ID = 5001

    // Distinct ID from the ongoing "sending" notification above — that one
    // disappears the moment the foreground service backing it stops, so
    // this is a separate, actually-persistent notification telling you what
    // happened, for whenever you weren't watching the app to see it live
    // (see PhotoSendService).
    private const val RESULT_NOTIFICATION_ID = 5002

    fun showSendingMessage(context: Context) = show(context, context.getString(R.string.sending_message_notification))

    private fun show(context: Context, title: String) {
        val notification = NotificationCompat.Builder(context, NotificationChannels.SENDING)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    fun showPhotoSent(context: Context) =
        showResult(context, context.getString(R.string.photo_sent_notification))

    fun showPhotoSendFailed(context: Context) =
        showResult(context, context.getString(R.string.photo_send_failed_notification))

    fun showDrawingSent(context: Context) =
        showResult(context, context.getString(R.string.drawing_sent_notification))

    fun showDrawingSendFailed(context: Context) =
        showResult(context, context.getString(R.string.drawing_send_failed_notification))

    private fun showResult(context: Context, title: String) {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            RESULT_NOTIFICATION_ID,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.SEND_RESULT)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(RESULT_NOTIFICATION_ID, notification)
    }
}
