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
    //
    // The request code includes messageId (not just appWidgetId) because the
    // widget carousel can bind several of these — one per page — in the same
    // render. PendingIntent.getActivity with FLAG_UPDATE_CURRENT reuses/
    // mutates any existing PendingIntent that shares a request code, so
    // without messageId in the mix every page's button would collapse onto
    // whichever page was bound last.
    fun bindReactAction(context: Context, remoteViews: RemoteViews, appWidgetId: Int, messageId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            putExtra(MainActivity.EXTRA_OPEN_REACT, true)
            putExtra(MainActivity.EXTRA_REACT_MESSAGE_ID, messageId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            "$appWidgetId-react-$messageId".hashCode(),
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

    // Tapping a specific dot jumps the carousel straight to that page —
    // see WidgetCarouselJumpReceiver/WidgetCarouselIndexStore for why this
    // (not a timer) is what drives the carousel now. Request code includes
    // targetIndex for the same reason bindReactAction's includes
    // messageId: several dots get bound in the same render, and
    // FLAG_UPDATE_CURRENT would collapse them onto one PendingIntent
    // without something unique per dot in the mix.
    fun bindCarouselJumpAction(context: Context, remoteViews: RemoteViews, viewId: Int, appWidgetId: Int, targetIndex: Int) {
        val intent = Intent(context, WidgetCarouselJumpReceiver::class.java).apply {
            putExtra(WidgetCarouselJumpReceiver.EXTRA_APP_WIDGET_ID, appWidgetId)
            putExtra(WidgetCarouselJumpReceiver.EXTRA_TARGET_INDEX, targetIndex)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            "$appWidgetId-dot-$targetIndex".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        remoteViews.setOnClickPendingIntent(viewId, pendingIntent)
    }
}
