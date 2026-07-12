package com.glimpse.app.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.glimpse.app.data.WidgetCarouselIndexStore
import com.glimpse.app.data.firebase.FirebaseSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Handles a tap on one of the carousel's page dots — jumps straight to
// that page. The app owns "which page is showing" entirely (see
// WidgetCarouselIndexStore/WidgetRenderer's use of setDisplayedChild), so
// this just updates the stored index for that one appWidgetId and pushes
// a single fresh render. No polling, no timer — same cost as any other
// widget button tap (React, open-compose).
//
// Not exported: only ever invoked via a PendingIntent this app itself
// created (see ReactionActionBinder.bindCarouselJumpAction), so it never
// needs to accept broadcasts from other apps.
class WidgetCarouselJumpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(EXTRA_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val targetIndex = intent.getIntExtra(EXTRA_TARGET_INDEX, 0)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            try {
                WidgetCarouselIndexStore.setIndex(context, appWidgetId, targetIndex)

                val appWidgetManager = AppWidgetManager.getInstance(context)
                val providerClassName = appWidgetManager.getAppWidgetInfo(appWidgetId)?.provider?.className
                val messages = FirebaseSync.fetchRecentMessagesOnce(WidgetRenderer.CAROUSEL_LIMIT)

                // Only the square provider needs its own layout/page size —
                // CurrentMessageWidget and LargeMessageWidget both render
                // through the same rectangular (optionally multi-size) path.
                val remoteViews = if (providerClassName == SquareMessageWidget::class.java.name) {
                    WidgetRenderer.renderSquare(context, appWidgetId, messages)
                } else {
                    WidgetRenderer.render(context, appWidgetId, messages)
                }
                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_APP_WIDGET_ID = "com.glimpse.app.EXTRA_CAROUSEL_APP_WIDGET_ID"
        const val EXTRA_TARGET_INDEX = "com.glimpse.app.EXTRA_CAROUSEL_TARGET_INDEX"
    }
}
