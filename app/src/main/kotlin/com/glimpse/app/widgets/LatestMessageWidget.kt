package com.glimpse.app.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.service.WidgetSyncTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// A separate, always-available provider for people who'd rather not have
// the carousel auto-advance through a catch-up backlog — always shows just
// the single newest message, the same behavior CurrentMessageWidget had
// before the carousel existed. Both are offered side by side in the widget
// picker; add whichever one (or both) you want.
class LatestMessageWidget : AppWidgetProvider() {

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
                    val remoteViews = WidgetRenderer.render(context, appWidgetId, messages, latestOnly = true)
                    appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
                }
                WidgetRenderer.markSeenForRender(messages, latestOnly = true)
            } finally {
                pendingResult.finish()
            }
        }

        WidgetSyncTrigger.requestSync(context)
    }

    override fun onEnabled(context: Context) {
        WidgetSyncTrigger.requestSync(context)
    }

    // See CurrentMessageWidget's identical override — same responsive
    // square/rectangular split via WidgetRenderer.render.
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            try {
                val messages = FirebaseSync.fetchRecentMessagesOnce(WidgetRenderer.CAROUSEL_LIMIT)
                val remoteViews = WidgetRenderer.render(context, appWidgetId, messages, latestOnly = true)
                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
                WidgetRenderer.markSeenForRender(messages, latestOnly = true)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
