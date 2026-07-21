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
import com.glimpse.app.widgets.CurrentMessageWidget
import com.glimpse.app.widgets.LargeMessageWidget
import com.glimpse.app.widgets.LatestMessageWidget
import com.glimpse.app.widgets.ShapedMessageWidget
import com.glimpse.app.widgets.ShapedWidgetRenderer
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
            // The carousel (catch-up scrolling through unseen messages, tap
            // dots to jump pages) is temporarily disabled for all widgets —
            // every provider renders latestOnly = true, i.e. just the single
            // newest message, matching how these widgets behaved before the
            // carousel existed. This sidesteps the whole class of
            // TransactionTooLargeException issues the carousel's multi-page
            // photo payloads kept running into; re-enable by reverting these
            // latestOnly flags to false once that's solved properly.
            val hasAnyWidget = updateProvider(appWidgetManager, CurrentMessageWidget::class.java) { id ->
                WidgetRenderer.render(this@WidgetUpdateService, id, messages, latestOnly = true)
            } or updateProvider(appWidgetManager, SquareMessageWidget::class.java) { id ->
                WidgetRenderer.renderSquare(this@WidgetUpdateService, id, messages, latestOnly = true)
            } or updateProvider(appWidgetManager, LargeMessageWidget::class.java) { id ->
                WidgetRenderer.render(this@WidgetUpdateService, id, messages, latestOnly = true)
            } or updateProvider(appWidgetManager, LatestMessageWidget::class.java) { id ->
                WidgetRenderer.render(this@WidgetUpdateService, id, messages, latestOnly = true)
            } or updateProvider(appWidgetManager, ShapedMessageWidget::class.java) { id ->
                ShapedWidgetRenderer.render(this@WidgetUpdateService, id, messages.lastOrNull())
            }

            if (hasAnyWidget) {
                WidgetRenderer.markSeenForRender(messages, latestOnly = true)
            }
        }
    }

    // Returns whether this provider actually has any widget instances —
    // callers use that to decide how "seen" should be interpreted overall.
    //
    // Each widget instance's render+push is isolated in its own try/catch:
    // previously, one instance throwing (e.g. TransactionTooLargeException
    // from too much photo data in a single RemoteViews Parcel) aborted this
    // whole suspend function, which in turn aborted updateWidgets' entire
    // coroutine — silently skipping every OTHER provider queued after it,
    // not just the one that actually failed. That's a real bug this
    // fixes on its own; see WidgetRenderer's loadPhoto param for the fix
    // to the actual oversized-payload cause.
    private suspend fun updateProvider(
        appWidgetManager: AppWidgetManager,
        providerClass: Class<*>,
        render: suspend (appWidgetId: Int) -> RemoteViews
    ): Boolean {
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
        return appWidgetIds.isNotEmpty()
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NotificationChannels.WIDGET_SYNC)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

    companion object {
        private const val TAG = "WidgetUpdateService"
        private const val NOTIFICATION_ID = 1001
    }
}
