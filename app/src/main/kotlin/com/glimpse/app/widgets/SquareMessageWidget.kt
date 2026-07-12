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

// The square-shaped counterpart to CurrentMessageWidget — a distinct
// provider (not a resize variant of the other one) so it shows up as its
// own separate, always-visible entry in the widget picker. See
// WidgetRenderer.renderSquare for why this can't just be the same provider.
class SquareMessageWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            try {
                val messages = FirebaseSync.fetchRecentMessagesOnce(WidgetRenderer.CAROUSEL_LIMIT)
                appWidgetIds.forEach { appWidgetId ->
                    val remoteViews = WidgetRenderer.renderSquare(context, appWidgetId, messages)
                    appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
                }
                WidgetRenderer.markSeenForRender(messages)
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
