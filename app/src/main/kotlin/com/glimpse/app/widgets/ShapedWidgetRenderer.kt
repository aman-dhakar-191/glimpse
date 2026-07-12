package com.glimpse.app.widgets

import android.content.Context
import android.widget.RemoteViews
import com.glimpse.app.R
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import com.google.firebase.auth.FirebaseAuth

// EXPERIMENTAL, fully isolated from WidgetRenderer/the carousel logic —
// see ShapedMessageWidget for the full reasoning behind this being a
// separate provider. Deliberately minimal (no photo, no reactions, no
// mood, no carousel) since the only thing actually being validated here
// is the fake-outline shape technique, not re-testing everything else.
internal object ShapedWidgetRenderer {

    suspend fun render(context: Context, appWidgetId: Int, message: Message?): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_shaped_message)
        ReactionActionBinder.bindOpenComposeAction(context, remoteViews, appWidgetId)
        ReactionActionBinder.bindReactAction(context, remoteViews, appWidgetId, message?.id.orEmpty())

        if (message == null) {
            remoteViews.setTextViewText(R.id.shaped_author_name, "")
            remoteViews.setTextViewText(R.id.shaped_message_content, context.getString(R.string.widget_no_message))
            return remoteViews
        }

        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        val displayAuthorName = if (message.authorUid == myUid) {
            message.authorName
        } else {
            FirebaseSync.fetchPartnerNicknameOnce().ifBlank { message.authorName }
        }
        remoteViews.setTextViewText(R.id.shaped_author_name, displayAuthorName)

        val hiddenByLock = message.isLocked && message.authorUid != myUid
        val content = when {
            hiddenByLock -> context.getString(R.string.widget_locked_message)
            // No photo rendering in this experimental layout — a caption
            // (or a plain fallback) stands in for it.
            message.type == "photo" -> message.caption.ifBlank { context.getString(R.string.widget_shaped_photo_fallback) }
            else -> message.content
        }
        remoteViews.setTextViewText(R.id.shaped_message_content, content)

        return remoteViews
    }
}
