package com.glimpse.app.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.glimpse.app.MainActivity
import com.glimpse.app.R

internal object ReactionActionBinder {
    private val REACTION_BUTTONS = listOf(
        "❤️" to R.id.btn_react_heart,
        "😊" to R.id.btn_react_smile,
        "👍" to R.id.btn_react_thumbsup,
        "😂" to R.id.btn_react_laugh,
        "🎉" to R.id.btn_react_fire
    )

    fun bindReactionButtons(context: Context, remoteViews: RemoteViews, appWidgetId: Int) {
        REACTION_BUTTONS.forEach { (emoji, viewId) ->
            val intent = Intent(context, ReactionBroadcastReceiver::class.java).apply {
                action = ReactionBroadcastReceiver.ACTION_ADD_REACTION
                putExtra(ReactionBroadcastReceiver.EXTRA_EMOJI, emoji)
            }
            val requestCode = "$appWidgetId$emoji".hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(viewId, pendingIntent)
        }
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
