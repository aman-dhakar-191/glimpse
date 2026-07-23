package com.glimpse.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.glimpse.app.R

// A dedicated 1x1 widget with a single job: send a "thinking of you" tap
// (see ThinkingOfYouSendReceiver) straight from the home screen, no need
// to open the app at all. Unlike ShapedMessageWidget there's no live data
// to show, so this never refreshes on a timer — updatePeriodMillis is 0 in
// widget_thinking_of_you_info.xml — the click PendingIntent bound in
// onUpdate is the only thing that ever matters here.
class ThinkingOfYouWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_thinking_of_you)
            val intent = Intent(context, ThinkingOfYouSendReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(R.id.thinking_of_you_root, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }
}
