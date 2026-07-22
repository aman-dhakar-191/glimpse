package com.glimpse.app.service

import android.app.Notification
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.glimpse.app.R
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import com.glimpse.app.notification.NotificationChannels
import com.glimpse.app.widgets.ShapedMessageWidget
import com.glimpse.app.widgets.ShapedWidgetRenderer
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
        listener?.let { FirebaseSync.removeHistoryListener(HISTORY_LIMIT, it) }
        listener = FirebaseSync.listenToHistory(HISTORY_LIMIT, ::updateWidgets)
        return START_STICKY
    }

    override fun onDestroy() {
        listener?.let { FirebaseSync.removeHistoryListener(HISTORY_LIMIT, it) }
        listener = null
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateWidgets(messages: List<Message>) {
        serviceScope.launch {
            val appWidgetManager = AppWidgetManager.getInstance(this@WidgetUpdateService)
            updateProvider(appWidgetManager, ShapedMessageWidget::class.java) { id ->
                ShapedWidgetRenderer.render(this@WidgetUpdateService, id, messages)
            }
        }
    }

    // Each widget instance's render+push is isolated in its own try/catch:
    // one instance throwing (e.g. from an oversized RemoteViews payload)
    // shouldn't abort every other instance's update.
    private suspend fun updateProvider(
        appWidgetManager: AppWidgetManager,
        providerClass: Class<*>,
        render: suspend (appWidgetId: Int) -> RemoteViews
    ) {
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(this@WidgetUpdateService, providerClass)
        )
        appWidgetIds.forEach { appWidgetId ->
            try {
                appWidgetManager.updateAppWidget(appWidgetId, render(appWidgetId))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update $providerClass widget $appWidgetId", e)
            }
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NotificationChannels.WIDGET_SYNC)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.widget_sync_notification_body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

    companion object {
        private const val TAG = "WidgetUpdateService"
        private const val NOTIFICATION_ID = 1001

        // Matches ShapedWidgetRenderer's carousel fetch cap — listenToHistory
        // needs to keep at least this many recent messages around for the
        // carousel to have a full window to page through, regardless of
        // what CarouselSettingsStore's user-facing display size is set to.
        private const val HISTORY_LIMIT = ShapedWidgetRenderer.CAROUSEL_LIMIT
    }
}
