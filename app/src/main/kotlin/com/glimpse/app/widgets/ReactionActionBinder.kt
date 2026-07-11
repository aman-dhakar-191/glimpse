package com.glimpse.app.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.glimpse.app.MainActivity
import com.glimpse.app.R

internal object ReactionActionBinder {

    // RemoteViews can't host an emoji picker (or any text input) itself, so
    // the React button opens the app straight to a full picker screen
    // instead of being limited to a fixed preset of reaction emojis.
    fun bindReactAction(context: Context, remoteViews: RemoteViews, appWidgetId: Int, messageId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            putExtra(MainActivity.EXTRA_OPEN_REACT, true)
            putExtra(MainActivity.EXTRA_REACT_MESSAGE_ID, messageId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            "$appWidgetId-react".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        remoteViews.setOnClickPendingIntent(R.id.btn_react, pendingIntent)
    }

    /**
     * Widgets can't contain a text input field (a hard RemoteViews
     * limitation), so tapping the message area is the fastest path to
     * actually writing one — jumps straight to the compose screen rather
     * than whatever screen the app was last left on.
     */
    fun bindOpenComposeAction(context: Context, remoteViews: RemoteViews, appWidgetId: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            putExtra(MainActivity.EXTRA_OPEN_COMPOSE, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        remoteViews.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
    }
}
