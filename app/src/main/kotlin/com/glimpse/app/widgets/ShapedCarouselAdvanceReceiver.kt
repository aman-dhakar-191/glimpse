package com.glimpse.app.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.glimpse.app.data.firebase.FirebaseSync
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Handles the carousel's "next page" tap — btn_carousel_advance in
// widget_shaped_message.xml. Kept dedicated to ShapedMessageWidget's own
// carousel rather than folded into a shared widget-action receiver, same as
// ShapedCarouselIndexStore.
class ShapedCarouselAdvanceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            try {
                // Re-fetched rather than passed through the PendingIntent —
                // the window's exact contents may have shifted (a new
                // message arrived, or a reaction changed seen-state) since
                // this button's RemoteViews were last rendered, and advance()
                // needs the CURRENT window size to wrap correctly.
                val messages = FirebaseSync.fetchRecentMessagesOnce(ShapedWidgetRenderer.CAROUSEL_LIMIT)
                val myUid = FirebaseAuth.getInstance().currentUser?.uid
                val windowSize = ShapedWidgetRenderer.unseenWindow(messages, myUid).size
                ShapedCarouselIndexStore.advance(context, appWidgetId, windowSize)

                val remoteViews = ShapedWidgetRenderer.render(context, appWidgetId, messages)
                AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, remoteViews)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
