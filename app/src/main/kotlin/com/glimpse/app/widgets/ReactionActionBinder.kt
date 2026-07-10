package com.glimpse.app.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
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
}
