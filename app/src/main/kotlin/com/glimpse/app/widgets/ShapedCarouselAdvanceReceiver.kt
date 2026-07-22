package com.glimpse.app.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Handles the carousel's "next page" tap — btn_carousel_advance in
// widget_shaped_carousel.xml (the separate "Glimpse Carousel" widget, not
// the plain ShapedMessageWidget). Delegates to
// ShapedCarouselWidgetRenderer.advanceAndPush, the same logic
// CarouselAutoAdvanceWorker uses on a timer — this is just that triggered
// by a touch instead.
class ShapedCarouselAdvanceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            try {
                ShapedCarouselWidgetRenderer.advanceAndPush(context, appWidgetId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
