package com.glimpse.app.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.RemoteViews
import coil.ImageLoader
import coil.request.ImageRequest
import com.glimpse.app.R
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import com.google.firebase.auth.FirebaseAuth

// EXPERIMENTAL, fully isolated from WidgetRenderer/the carousel logic —
// see ShapedMessageWidget for the full reasoning behind this being a
// separate provider. Always a single message, single size, single
// provider instance's worth of RemoteViews — unlike CurrentMessageWidget's
// responsive multi-size render, there's no risk here of the same photo
// getting embedded twice into one Binder transaction, so it's safe to
// always load it.
internal object ShapedWidgetRenderer {

    suspend fun render(context: Context, appWidgetId: Int, message: Message?): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_shaped_message)
        ReactionActionBinder.bindOpenComposeAction(context, remoteViews, appWidgetId)
        ReactionActionBinder.bindReactAction(context, remoteViews, appWidgetId, message?.id.orEmpty())

        if (message == null) {
            remoteViews.setTextViewText(R.id.shaped_author_name, "")
            remoteViews.setTextViewText(R.id.shaped_message_content, context.getString(R.string.widget_no_message))
            remoteViews.setViewVisibility(R.id.shaped_message_photo, View.GONE)
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
            // The photo itself goes into shaped_message_photo below — this
            // is just the caption line under it (or a fallback if blank).
            message.type == "photo" -> message.caption
            else -> message.content
        }
        remoteViews.setTextViewText(R.id.shaped_message_content, content)
        remoteViews.setViewVisibility(R.id.shaped_message_content, if (content.isNotBlank()) View.VISIBLE else View.GONE)

        if (message.type == "photo" && !hiddenByLock) {
            val photoBitmap = if (message.photoUrl.isNotBlank()) loadBitmap(context, message.photoUrl) else null
            if (photoBitmap != null) {
                remoteViews.setImageViewBitmap(R.id.shaped_message_photo, photoBitmap)
                remoteViews.setViewVisibility(R.id.shaped_message_photo, View.VISIBLE)
            } else {
                remoteViews.setViewVisibility(R.id.shaped_message_photo, View.GONE)
                remoteViews.setTextViewText(R.id.shaped_message_content, content.ifBlank { context.getString(R.string.widget_photo_fallback) })
                remoteViews.setViewVisibility(R.id.shaped_message_content, View.VISIBLE)
            }
        } else {
            remoteViews.setViewVisibility(R.id.shaped_message_photo, View.GONE)
        }

        return remoteViews
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? {
        return try {
            val imageLoader = ImageLoader.Builder(context).build()
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val bitmap = (imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
                ?: return null
            // Same size budget as WidgetRenderer.loadBitmap — keeps this
            // single-photo Parcel well under the Binder transaction limit.
            val maxDimension = 480
            if (bitmap.width <= maxDimension && bitmap.height <= maxDimension) {
                bitmap
            } else {
                val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
