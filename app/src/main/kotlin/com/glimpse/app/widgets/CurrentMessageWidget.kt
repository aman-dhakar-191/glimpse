package com.glimpse.app.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.service.WidgetSyncTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CurrentMessageWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // A plain one-shot fetch+render, independent of
        // WidgetUpdateService's foreground service — that service can't
        // always start immediately from this receiver context (Android 12+
        // background-start restrictions, most visible right after the
        // widget is (re-)added), which previously left the widget blank
        // until the app was opened. This guarantees real content shows up
        // right away regardless.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            try {
                val message = FirebaseSync.fetchLatestMessageOnce()
                appWidgetIds.forEach { appWidgetId ->
                    val remoteViews = WidgetRenderer.render(context, appWidgetId, message)
                    appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
                }
                FirebaseSync.markSeenIfNeeded(message)
            } finally {
                pendingResult.finish()
            }
        }

        // Best-effort: also start the live-listener service, for real-time
        // updates from here on whenever the OS allows it.
        WidgetSyncTrigger.requestSync(context)
    }

    override fun onEnabled(context: Context) {
        WidgetSyncTrigger.requestSync(context)
    }
}
