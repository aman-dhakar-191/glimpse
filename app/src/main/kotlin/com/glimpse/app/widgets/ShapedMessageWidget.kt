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

// EXPERIMENTAL — validates the "transparent root + custom silhouette
// ImageView" technique for faking a non-rectangular widget outline (see
// ShapedWidgetRenderer and widget_shaped_message.xml for the full
// reasoning and limitations). Entirely self-contained: its own provider,
// layout, drawable, and rendering code, sharing nothing with the other
// widget providers except ReactionActionBinder's generic bind functions.
// Safe to delete this file — plus ShapedWidgetRenderer.kt,
// widget_shaped_message.xml, widget_blob_shape.xml, the manifest
// receiver, and the widget_label_shaped/widget_shaped_* strings — with
// zero impact on any other widget if this doesn't hold up on a real
// device.
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
