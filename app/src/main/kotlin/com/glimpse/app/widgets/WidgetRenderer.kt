package com.glimpse.app.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import coil.ImageLoader
import coil.request.ImageRequest
import com.glimpse.app.R
import com.glimpse.app.data.WidgetBackgroundPhotoStore
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Shared between WidgetUpdateService (live Firebase listener, while its
// foreground service is allowed to run) and CurrentMessageWidget's onUpdate
// (an immediate one-shot render that doesn't depend on that service being
// startable), so both paths produce identical widget output.
internal object WidgetRenderer {

    // Both layouts use identical view IDs (author_name, message_content,
    // etc.), so applyMessage/ReactionActionBinder need no branching — only
    // the layout resource and its paddings/text sizes differ per size.
    private val SQUARE_SIZE = SizeF(110f, 110f)
    private val RECTANGULAR_SIZE = SizeF(250f, 110f)

    // For CurrentMessageWidget. Also opts into the responsive multi-size
    // RemoteViews on API 31+ as a bonus for launchers that support in-place
    // resize-to-switch-layout — SquareMessageWidget's own dedicated picker
    // entry (see renderSquare) is what makes the square shape available
    // everywhere else.
    suspend fun render(context: Context, appWidgetId: Int, message: Message?): RemoteViews {
        val photoBitmap = loadPhotoIfNeeded(context, message)
        val displayAuthorName = resolveAuthorName(message)
        val partnerMood = FirebaseSync.fetchPartnerMoodOnce()

        val rectangularViews = buildViews(
            context, appWidgetId, R.layout.widget_current_message, message, photoBitmap, displayAuthorName, partnerMood
        )

        // The multi-size RemoteViews constructor (which lets the system pick
        // the best-fitting layout as the user resizes the widget) only
        // exists on API 31+ — older devices always get the rectangular
        // layout, same as before this feature existed.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return rectangularViews

        val squareViews = buildViews(
            context, appWidgetId, R.layout.widget_current_message_square, message, photoBitmap, displayAuthorName, partnerMood
        )
        return RemoteViews(
            mapOf(
                SQUARE_SIZE to squareViews,
                RECTANGULAR_SIZE to rectangularViews
            )
        )
    }

    // For SquareMessageWidget — always the square layout, regardless of API
    // level, since this provider's own footprint (not an in-place resize) is
    // what determines its shape.
    suspend fun renderSquare(context: Context, appWidgetId: Int, message: Message?): RemoteViews {
        val photoBitmap = loadPhotoIfNeeded(context, message)
        val displayAuthorName = resolveAuthorName(message)
        val partnerMood = FirebaseSync.fetchPartnerMoodOnce()
        return buildViews(
            context, appWidgetId, R.layout.widget_current_message_square, message, photoBitmap, displayAuthorName, partnerMood
        )
    }

    // The message's stored authorName is whatever the sender's Google
    // account display name was at send time — this device's own
    // "what I call my partner" setting (see FirebaseSync.fetchPartnerNicknameOnce)
    // overrides that display, but only for messages that aren't mine, and
    // only locally: it never touches the stored message data itself.
    private suspend fun resolveAuthorName(message: Message?): String {
        if (message == null) return ""
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        if (message.authorUid == myUid) return message.authorName
        val nickname = FirebaseSync.fetchPartnerNicknameOnce()
        return nickname.ifBlank { message.authorName }
    }

    // RemoteViews.setImageViewUri() rejects arbitrary https:// URLs on
    // Android 12+ (SecurityException: "Disallowed URI ... in
    // RemoteViews") — widgets can only be handed real pixel data, not a
    // URI for the host to fetch itself. So we download it here and hand
    // over a Bitmap instead.
    private suspend fun loadPhotoIfNeeded(context: Context, message: Message?): Bitmap? =
        if (message?.type == "photo" && message.photoUrl.isNotBlank()) {
            loadBitmap(context, message.photoUrl)
        } else {
            null
        }

    private suspend fun buildViews(
        context: Context,
        appWidgetId: Int,
        layoutRes: Int,
        message: Message?,
        photoBitmap: Bitmap?,
        displayAuthorName: String,
        partnerMood: String
    ): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, layoutRes)
        ReactionActionBinder.bindReactAction(context, remoteViews, appWidgetId, message?.id.orEmpty())
        ReactionActionBinder.bindOpenComposeAction(context, remoteViews, appWidgetId)
        applyMessage(context, remoteViews, message, photoBitmap, displayAuthorName)
        applyBackgroundPhoto(context, remoteViews, layoutRes)
        applyMoodStatus(remoteViews, partnerMood)
        return remoteViews
    }

    // A plain TextView leaf (partner_mood) next to author_name — see
    // FirebaseSync.setMood for why this is safe to show (shared, not
    // sensitive) and MoodViewModel for where it's set.
    private fun applyMoodStatus(remoteViews: RemoteViews, partnerMood: String) {
        if (partnerMood.isNotBlank()) {
            remoteViews.setTextViewText(R.id.partner_mood, partnerMood)
            remoteViews.setViewVisibility(R.id.partner_mood, View.VISIBLE)
        } else {
            remoteViews.setViewVisibility(R.id.partner_mood, View.GONE)
        }
    }

    // Local-only, per-device customization — see WidgetBackgroundPhotoStore.
    // Defaults (no photo set) leave widget_root's normal opaque background
    // completely untouched, so this is a no-op for anyone who hasn't opted
    // in via WidgetGuideScreen's background-photo card.
    private suspend fun applyBackgroundPhoto(context: Context, remoteViews: RemoteViews, layoutRes: Int) {
        val backgroundBitmap = withContext(Dispatchers.IO) { WidgetBackgroundPhotoStore.loadBitmap(context) }
        if (backgroundBitmap != null) {
            remoteViews.setImageViewBitmap(R.id.widget_background_photo, backgroundBitmap)
            remoteViews.setViewVisibility(R.id.widget_background_photo, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.widget_background_scrim, View.VISIBLE)
            remoteViews.setInt(R.id.widget_root, "setBackgroundColor", Color.TRANSPARENT)
        } else {
            remoteViews.setViewVisibility(R.id.widget_background_photo, View.GONE)
            remoteViews.setViewVisibility(R.id.widget_background_scrim, View.GONE)
            val defaultBackgroundRes = if (layoutRes == R.layout.widget_current_message_square) {
                R.drawable.bg_widget_root_square
            } else {
                R.drawable.bg_widget_root
            }
            remoteViews.setInt(R.id.widget_root, "setBackgroundResource", defaultBackgroundRes)
        }
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
        photoBitmap: Bitmap?,
        displayAuthorName: String
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

        remoteViews.setTextViewText(R.id.author_name, displayAuthorName)
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
