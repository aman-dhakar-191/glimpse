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

// A taller (4x6) counterpart to CurrentMessageWidget — its own dedicated
// provider (same reasoning as SquareMessageWidget) so it always shows up as
// its own entry in the widget picker rather than relying on manual resize.
// Uses the same rectangular layout/render path as CurrentMessageWidget; only
// the AppWidgetProviderInfo's default size differs.
class LargeMessageWidget : AppWidgetProvider() {

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
                    val remoteViews = WidgetRenderer.render(context, appWidgetId, message)
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
