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
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.glimpse.app.R
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import com.glimpse.app.widgets.CurrentMessageWidget
import com.glimpse.app.widgets.LargeMessageWidget
import com.glimpse.app.widgets.LatestMessageWidget
import com.glimpse.app.widgets.SquareMessageWidget
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
        listener?.let { FirebaseSync.removeHistoryListener(WidgetRenderer.CAROUSEL_LIMIT, it) }
        listener = FirebaseSync.listenToHistory(WidgetRenderer.CAROUSEL_LIMIT, ::updateWidgets)
        return START_STICKY
    }

    override fun onDestroy() {
        listener?.let { FirebaseSync.removeHistoryListener(WidgetRenderer.CAROUSEL_LIMIT, it) }
        listener = null
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateWidgets(messages: List<Message>) {
        serviceScope.launch {
            val appWidgetManager = AppWidgetManager.getInstance(this@WidgetUpdateService)
            val hasCarouselWidget = updateProvider(appWidgetManager, CurrentMessageWidget::class.java) { id ->
                WidgetRenderer.render(this@WidgetUpdateService, id, messages)
            } or updateProvider(appWidgetManager, SquareMessageWidget::class.java) { id ->
                WidgetRenderer.renderSquare(this@WidgetUpdateService, id, messages)
            } or updateProvider(appWidgetManager, LargeMessageWidget::class.java) { id ->
                WidgetRenderer.render(this@WidgetUpdateService, id, messages)
            }
            val hasLatestOnlyWidget = updateProvider(appWidgetManager, LatestMessageWidget::class.java) { id ->
                WidgetRenderer.render(this@WidgetUpdateService, id, messages, latestOnly = true)
            }

            // Mark seen based on whatever's actually on screen: if any
            // carousel-capable widget is present, the whole catch-up window
            // is visible to the user (there), so it's fair to mark all of
            // it seen; only if the sole widget present is a "latest only"
            // one do we fall back to marking just the newest message, since
            // that's genuinely all it ever shows.
            if (hasCarouselWidget) {
                WidgetRenderer.markSeenForRender(messages)
            } else if (hasLatestOnlyWidget) {
                WidgetRenderer.markSeenForRender(messages, latestOnly = true)
            }
        }
    }

    // Returns whether this provider actually has any widget instances —
    // callers use that to decide how "seen" should be interpreted overall.
    private suspend fun updateProvider(
        appWidgetManager: AppWidgetManager,
        providerClass: Class<*>,
        render: suspend (appWidgetId: Int) -> RemoteViews
    ): Boolean {
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(this@WidgetUpdateService, providerClass)
        )
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, render(appWidgetId))
        }
        return appWidgetIds.isNotEmpty()
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
