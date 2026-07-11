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
import androidx.core.app.NotificationCompat
import com.glimpse.app.R
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import com.glimpse.app.widgets.CurrentMessageWidget
import com.glimpse.app.widgets.WidgetRenderer
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WidgetUpdateService : Service() {

    private var listener: ValueEventListener? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

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
        listener?.let { FirebaseSync.removeLatestMessageListener(it) }
        listener = FirebaseSync.listenToLatestMessage(::updateWidgets)
        return START_STICKY
    }

    override fun onDestroy() {
        listener?.let { FirebaseSync.removeLatestMessageListener(it) }
        listener = null
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateWidgets(message: Message?) {
        serviceScope.launch {
            val appWidgetManager = AppWidgetManager.getInstance(this@WidgetUpdateService)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(this@WidgetUpdateService, CurrentMessageWidget::class.java)
            )
            appWidgetIds.forEach { appWidgetId ->
                val remoteViews = WidgetRenderer.render(this@WidgetUpdateService, appWidgetId, message)
                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            }
            FirebaseSync.markSeenIfNeeded(message)
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
