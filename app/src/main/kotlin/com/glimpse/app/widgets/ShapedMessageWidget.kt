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

// Glimpse's home-screen widget — a "transparent root + custom silhouette
// ImageView" technique for faking a non-rectangular widget outline (see
// ShapedWidgetRenderer and widget_shaped_message.xml for the full
// reasoning and limitations).
class ShapedMessageWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            try {
                val message = FirebaseSync.fetchLatestMessageOnce()
                appWidgetIds.forEach { appWidgetId ->
                    val remoteViews = ShapedWidgetRenderer.render(context, appWidgetId, message)
                    appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
                }
                FirebaseSync.markSeenIfNeeded(message)
            } finally {
                pendingResult.finish()
            }
        }

        WidgetSyncTrigger.requestSync(context)
    }

    override fun onEnabled(context: Context) {
        WidgetSyncTrigger.requestSync(context)
    }
}
