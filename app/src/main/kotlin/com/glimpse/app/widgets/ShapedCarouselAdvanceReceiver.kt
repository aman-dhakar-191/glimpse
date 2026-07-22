package com.glimpse.app.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.glimpse.app.data.CarouselSettingsStore
import com.glimpse.app.data.firebase.FirebaseSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Handles the carousel's "next page" tap — btn_carousel_advance in
// widget_shaped_carousel.xml (the separate "Glimpse Carousel" widget, not
// the plain ShapedMessageWidget). Cycles through the fixed latest-N window
// (see ShapedCarouselWidgetRenderer.displayWindow) with wraparound; there's
// no seen/unseen state involved, so this is a pure "browse the recent
// history" control, not a catch-up mechanism.
class ShapedCarouselAdvanceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            try {
                // Re-fetched rather than passed through the PendingIntent —
                // the window's exact contents may have shifted (a new
                // message arrived) since this button's RemoteViews were
                // last rendered, and advance() needs the CURRENT window
                // size to wrap correctly.
                val messages = FirebaseSync.fetchRecentMessagesOnce(ShapedCarouselWidgetRenderer.CAROUSEL_LIMIT)
                val windowSize = ShapedCarouselWidgetRenderer.displayWindow(messages, CarouselSettingsStore.load(context)).size
                ShapedCarouselPageStore.advance(context, appWidgetId, windowSize)

                val remoteViews = ShapedCarouselWidgetRenderer.render(context, appWidgetId, messages)
                AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, remoteViews)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
