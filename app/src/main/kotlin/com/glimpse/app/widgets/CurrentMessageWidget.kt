package com.glimpse.app.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.glimpse.app.R
import com.glimpse.app.service.WidgetSyncTrigger

class CurrentMessageWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_current_message)
            ReactionActionBinder.bindReactAction(context, remoteViews, appWidgetId)
            ReactionActionBinder.bindOpenComposeAction(context, remoteViews, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
        WidgetSyncTrigger.requestSync(context)
    }

    override fun onEnabled(context: Context) {
        WidgetSyncTrigger.requestSync(context)
    }
}
