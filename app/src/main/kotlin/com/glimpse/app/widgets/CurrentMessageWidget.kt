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

        // Best-effort: also start the live-listener service, for real-time
        // updates from here on whenever the OS allows it.
        WidgetSyncTrigger.requestSync(context)
    }

    override fun onEnabled(context: Context) {
        WidgetSyncTrigger.requestSync(context)
    }

    // A resize changes which of the two responsive size variants (square vs
    // rectangular) is actually on screen — see WidgetRenderer.render's
    // allowPhoto split, which only loads the current page's photo into
    // whichever variant is active to avoid doubling the RemoteViews Parcel.
    // Re-rendering here (rather than waiting for the next Firebase-triggered
    // update) means the newly-active size picks up its real photo right away.
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
