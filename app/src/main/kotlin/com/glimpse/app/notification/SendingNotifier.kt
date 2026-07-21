package com.glimpse.app.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.glimpse.app.R

// Transient "Sending message…"/"Sending photo…" status, shown from
// SendMessageWorker and ComposeMessageViewModel.sendPhotoMessage while a
// send is in flight and cancelled as soon as it finishes (success or
// failure) — silent/low-priority since it's just visible progress, not
// something that needs to interrupt.
object SendingNotifier {
    private const val NOTIFICATION_ID = 5001

    fun showSendingMessage(context: Context) = show(context, context.getString(R.string.sending_message_notification))

    fun showSendingPhoto(context: Context) = show(context, context.getString(R.string.sending_photo_notification))

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
}
