package com.glimpse.app.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.RemoteViews
import coil.ImageLoader
import coil.request.ImageRequest
import com.glimpse.app.R
import com.glimpse.app.data.model.Message

// Shared between WidgetUpdateService (live Firebase listener, while its
// foreground service is allowed to run) and CurrentMessageWidget's onUpdate
// (an immediate one-shot render that doesn't depend on that service being
// startable), so both paths produce identical widget output.
internal object WidgetRenderer {

    suspend fun render(context: Context, appWidgetId: Int, message: Message?): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_current_message)
        ReactionActionBinder.bindReactAction(context, remoteViews, appWidgetId, message?.id.orEmpty())
        ReactionActionBinder.bindOpenComposeAction(context, remoteViews, appWidgetId)

        // RemoteViews.setImageViewUri() rejects arbitrary https:// URLs on
        // Android 12+ (SecurityException: "Disallowed URI ... in
        // RemoteViews") — widgets can only be handed real pixel data, not a
        // URI for the host to fetch itself. So we download it here and hand
        // over a Bitmap instead.
        val photoBitmap = if (message?.type == "photo" && message.photoUrl.isNotBlank()) {
            loadBitmap(context, message.photoUrl)
        } else {
            null
        }

        applyMessage(context, remoteViews, message, photoBitmap)
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
            // Keep the RemoteViews Parcel well under the binder transaction
            // size limit — a full-resolution photo would blow past it.
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

    private fun applyMessage(
        context: Context,
        remoteViews: RemoteViews,
        message: Message?,
        photoBitmap: Bitmap?
    ) {
        if (message == null) {
            remoteViews.setTextViewText(R.id.author_name, "")
            remoteViews.setTextViewText(R.id.message_content, context.getString(R.string.widget_no_message))
            remoteViews.setViewVisibility(R.id.message_content, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.message_photo, View.GONE)
            remoteViews.setViewVisibility(R.id.photo_caption, View.GONE)
            remoteViews.removeAllViews(R.id.reactions_container)
            return
        }

        remoteViews.setTextViewText(R.id.author_name, message.authorName)
        remoteViews.setTextViewText(R.id.message_content, message.content)
        // GONE views are skipped entirely when a LinearLayout distributes
        // weighted space, so hiding this when there's no text (photo
        // messages) hands its whole weighted share to message_photo instead
        // of splitting the box with an empty label.
        remoteViews.setViewVisibility(
            R.id.message_content,
            if (message.content.isNotBlank()) View.VISIBLE else View.GONE
        )

        if (message.type == "photo") {
            if (photoBitmap != null) {
                remoteViews.setImageViewBitmap(R.id.message_photo, photoBitmap)
                remoteViews.setViewVisibility(R.id.message_photo, View.VISIBLE)
            } else {
                remoteViews.setViewVisibility(R.id.message_photo, View.GONE)
            }
            remoteViews.setTextViewText(R.id.photo_caption, message.caption)
            remoteViews.setViewVisibility(
                R.id.photo_caption,
                if (message.caption.isNotBlank()) View.VISIBLE else View.GONE
            )
        } else {
            remoteViews.setViewVisibility(R.id.message_photo, View.GONE)
            remoteViews.setViewVisibility(R.id.photo_caption, View.GONE)
        }

        remoteViews.removeAllViews(R.id.reactions_container)
        message.reactions.filterValues { it.isNotEmpty() }.forEach { (emoji, userIds) ->
            val chip = RemoteViews(context.packageName, R.layout.reaction_chip)
            chip.setTextViewText(R.id.reaction_text, "$emoji ${userIds.size}")
            remoteViews.addView(R.id.reactions_container, chip)
        }
    }
}
