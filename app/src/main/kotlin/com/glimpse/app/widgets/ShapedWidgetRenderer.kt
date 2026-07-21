package com.glimpse.app.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
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

    // Photo is masked to a fixed square so the mask math below doesn't need
    // to know the ImageView's actual runtime pixel size (RemoteViews gives
    // no reliable way to query that) — shaped_message_photo uses
    // scaleType="fitCenter", so this square is scaled to fit its slot
    // without ever cropping into the masked shape.
    private const val PHOTO_MASK_SIZE = 300

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
                val borderColor = ContextCompat.getColor(context, R.color.widget_border)
                val masked = maskToBlobShape(photoBitmap, PHOTO_MASK_SIZE, borderColor)
                remoteViews.setImageViewBitmap(R.id.shaped_message_photo, masked)
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

    // Same normalized path as BlobShapeSoftC (ui/theme/BlobShapes.kt) and
    // widget_blob_shape.xml's background silhouette, scaled to whatever
    // pixel size the photo is masked at. Using the literal same curve —
    // not an approximation like a rounded rect — is what guarantees the
    // masked photo's edges can never poke outside the blob outline.
    private fun blobPath(size: Float): Path = Path().apply {
        moveTo(0.14f * size, 0.22f * size)
        cubicTo(0.22f * size, 0.08f * size, 0.40f * size, 0.04f * size, 0.56f * size, 0.07f * size)
        cubicTo(0.68f * size, 0.09f * size, 0.78f * size, 0.06f * size, 0.87f * size, 0.14f * size)
        cubicTo(0.96f * size, 0.22f * size, 0.94f * size, 0.34f * size, 0.91f * size, 0.44f * size)
        cubicTo(0.88f * size, 0.54f * size, 0.94f * size, 0.60f * size, 0.92f * size, 0.70f * size)
        cubicTo(0.89f * size, 0.84f * size, 0.76f * size, 0.94f * size, 0.60f * size, 0.94f * size)
        cubicTo(0.48f * size, 0.94f * size, 0.40f * size, 0.90f * size, 0.28f * size, 0.91f * size)
        cubicTo(0.14f * size, 0.92f * size, 0.06f * size, 0.82f * size, 0.06f * size, 0.70f * size)
        cubicTo(0.06f * size, 0.60f * size, 0.12f * size, 0.54f * size, 0.10f * size, 0.44f * size)
        cubicTo(0.08f * size, 0.34f * size, 0.08f * size, 0.30f * size, 0.14f * size, 0.22f * size)
        close()
    }

    // RemoteViews can't clip an ImageView to an arbitrary path —
    // clipToOutline only derives an Outline from a rect/round-rect/oval
    // background, never a <path> vector drawable — so masking the bitmap
    // ourselves, in-process, before handing it to the widget is the actual
    // way to make the photo's edges follow the blob silhouette instead of
    // a rounded-rect approximation that can clip past the curve's concave
    // points. Border stroke drawn first (unclipped) so its outer half stays
    // visible once the clipped photo paints over the inner half.
    private fun maskToBlobShape(source: Bitmap, targetSize: Int, borderColor: Int): Bitmap {
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val path = blobPath(targetSize.toFloat())

        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = targetSize * 0.015f
                color = borderColor
            }
        )

        canvas.clipPath(path)
        // minOf (contain), not maxOf (cover/centerCrop) — the whole photo
        // stays visible, letterboxed within the blob if its aspect ratio
        // doesn't match, rather than cropping into the top/bottom of a
        // portrait photo to fill every corner of the shape.
        val scale = minOf(targetSize.toFloat() / source.width, targetSize.toFloat() / source.height)
        val dx = (targetSize - source.width * scale) / 2f
        val dy = (targetSize - source.height * scale) / 2f
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
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
