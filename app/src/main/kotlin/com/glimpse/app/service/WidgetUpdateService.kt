package com.glimpse.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.glimpse.app.R
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import com.glimpse.app.widgets.CurrentMessageWidget
import com.glimpse.app.widgets.ReactionActionBinder
import com.google.firebase.database.ValueEventListener

class WidgetUpdateService : Service() {

    private var listener: ValueEventListener? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always tear down and re-attach: if the previous listener was
        // dropped by Firebase (e.g. a permission-denied error, which the SDK
        // treats as terminal and never retries), `listener` here would still
        // be a non-null reference to a dead listener, so a null-check guard
        // would permanently skip re-attaching even after the underlying
        // problem (like a missing allowedUsers entry) is fixed.
        listener?.let { FirebaseSync.removeCurrentMessageListener(it) }
        listener = FirebaseSync.listenToCurrentMessage(::updateWidgets)
        return START_STICKY
    }

    override fun onDestroy() {
        listener?.let { FirebaseSync.removeCurrentMessageListener(it) }
        listener = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateWidgets(message: Message?) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(this, CurrentMessageWidget::class.java)
        )
        if (appWidgetIds.isEmpty()) return

        appWidgetIds.forEach { appWidgetId ->
            val remoteViews = RemoteViews(packageName, R.layout.widget_current_message)
            ReactionActionBinder.bindReactionButtons(this, remoteViews, appWidgetId)
            renderMessage(remoteViews, message)
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }

    private fun renderMessage(remoteViews: RemoteViews, message: Message?) {
        if (message == null) {
            remoteViews.setTextViewText(R.id.author_name, "")
            remoteViews.setTextViewText(R.id.message_content, getString(R.string.widget_no_message))
            remoteViews.setViewVisibility(R.id.message_photo, View.GONE)
            remoteViews.setViewVisibility(R.id.photo_caption, View.GONE)
            remoteViews.removeAllViews(R.id.reactions_container)
            return
        }

        remoteViews.setTextViewText(R.id.author_name, message.authorName)
        remoteViews.setTextViewText(R.id.message_content, message.content)

        if (message.type == "photo" && message.photoUrl.isNotBlank()) {
            remoteViews.setImageViewUri(R.id.message_photo, message.photoUrl.toUri())
            remoteViews.setViewVisibility(R.id.message_photo, View.VISIBLE)
            remoteViews.setTextViewText(R.id.photo_caption, message.caption)
            remoteViews.setViewVisibility(
                R.id.photo_caption,
                if (message.caption.isNotBlank()) View.VISIBLE else View.GONE
            )
        } else {
            remoteViews.setViewVisibility(R.id.message_photo, View.GONE)
            remoteViews.setViewVisibility(R.id.photo_caption, View.GONE)
        }

        remoteViews.removeAllViews(R.id.reactions_container)
        message.reactions.filterValues { it.isNotEmpty() }.forEach { (emoji, userIds) ->
            val chip = RemoteViews(packageName, R.layout.reaction_chip)
            chip.setTextViewText(R.id.reaction_text, "$emoji ${userIds.size}")
            remoteViews.addView(R.id.reactions_container, chip)
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "widget_sync"
        private const val NOTIFICATION_ID = 1001
    }
}
