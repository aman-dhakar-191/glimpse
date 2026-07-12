package com.glimpse.app.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.glimpse.app.data.WidgetCarouselIndexStore
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
                // Carousel temporarily disabled (latestOnly = true) — see
                // WidgetUpdateService.updateWidgets for why.
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetCarouselIndexStore.clear(context, it) }
    }
}
