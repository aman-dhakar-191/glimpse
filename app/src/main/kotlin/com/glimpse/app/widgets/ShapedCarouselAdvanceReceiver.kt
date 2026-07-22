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
// widget_shaped_message.xml. Tapping it means "I've seen this one, show me
// the next": it bumps my own last-seen-at marker forward to (at least) the
// currently-displayed message's createdAt, which is what actually
// determines the next unseen window on the following render (see
// ShapedWidgetRenderer.unseenWindow) — there's no separately persisted
// "current page" index to keep in sync; the oldest still-unseen message IS
// the current page, always.
class ShapedCarouselAdvanceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            try {
                // Re-fetched rather than passed through the PendingIntent —
                // the window's exact contents may have shifted (a new
                // message arrived, or the app itself moved the seen marker)
                // since this button's RemoteViews were last rendered.
                val messages = FirebaseSync.fetchRecentMessagesOnce(ShapedWidgetRenderer.CAROUSEL_LIMIT)
                val myUid = FirebaseAuth.getInstance().currentUser?.uid
                val myLastSeenAt = FirebaseSync.fetchLastSeenAtOnce()[myUid] ?: 0L
                val window = ShapedWidgetRenderer.unseenWindow(messages, myUid, myLastSeenAt)
                val current = window.firstOrNull()
                if (current != null && window.size > 1) {
                    FirebaseSync.markSeenUpTo(current.createdAt)
                }

                val remoteViews = ShapedWidgetRenderer.render(context, appWidgetId, messages)
                AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, remoteViews)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
