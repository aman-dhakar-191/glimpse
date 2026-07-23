package com.glimpse.app.widgets

import android.appwidget.AppWidgetManager
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
import com.glimpse.app.data.CarouselSettingsStore
import com.glimpse.app.data.WidgetAccentColorStore
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import com.glimpse.app.util.CrashLogger
import com.google.firebase.auth.FirebaseAuth

// Renders the SEPARATE "Glimpse Carousel" widget (widget_shaped_carousel.xml
// / ShapedCarouselWidget) — a distinct, individually pickable widget from
// the plain single-message "Glimpse" widget (ShapedWidgetRenderer /
// widget_shaped_message.xml), not a mode of it. Deliberately its own file
// rather than a shared/generic "WidgetRenderer" utility spanning both, even
// though the two share a lot of presentation logic below — keeping the
// carousel-specific concerns (window/page selection, dots, advance button)
// isolated here means the plain widget's own code never has to know the
// carousel exists.
//
// Single size, single provider instance's worth of RemoteViews, and only
// ever the CURRENTLY DISPLAYED page's photo gets loaded — never every page
// in the carousel window at once — so there's no risk of the same photo
// (or several different ones) getting embedded together into one Binder
// transaction. That rule is the actual fix for the old carousel's
// TransactionTooLargeException history; keep it if this is ever extended
// further.
internal object ShapedCarouselWidgetRenderer {

    // Photo is masked to a fixed square so the mask math below doesn't need
    // to know the ImageView's actual runtime pixel size (RemoteViews gives
    // no reliable way to query that) — shaped_message_photo uses
    // scaleType="fitCenter", so this square is scaled to fit its slot
    // without ever cropping into the masked shape.
    private const val PHOTO_MASK_SIZE = 300

    // Same reasoning as PHOTO_MASK_SIZE — a fixed bitmap size for the whole
    // widget's background silhouette, scaled to fit via the ImageView's own
    // scaleType="fitXY".
    private const val BACKGROUND_SIZE = 300

    // Upper bound on how many recent messages get fetched/kept in reach for
    // the carousel — independent of CarouselSettingsStore's user-facing
    // "how many to display" setting (which is capped at this same value),
    // so a Settings change never needs the underlying Firebase listener to
    // be re-created with a new limit.
    const val CAROUSEL_LIMIT = CarouselSettingsStore.MAX_SIZE

    // Advances the given widget instance to its next carousel page and
    // pushes the update — shared by the tap-triggered
    // ShapedCarouselAdvanceReceiver and the periodic
    // CarouselAutoAdvanceWorker, so both go through the exact same
    // page-advance logic; auto-advance is just this on a timer instead of a
    // touch.
    suspend fun advanceAndPush(context: Context, appWidgetId: Int) {
        val messages = FirebaseSync.fetchRecentMessagesOnce(CAROUSEL_LIMIT)
        val windowSize = displayWindow(messages, CarouselSettingsStore.load(context)).size
        ShapedCarouselPageStore.advance(context, appWidgetId, windowSize)

        val remoteViews = render(context, appWidgetId, messages)
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, remoteViews)
    }

    suspend fun render(context: Context, appWidgetId: Int, messages: List<Message>): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_shaped_carousel)
        ReactionActionBinder.bindOpenComposeAction(context, remoteViews, appWidgetId)

        // Drawn as a bitmap instead of relying on widget_blob_shape.xml's
        // own baked-in stroke color, so a per-device accent color pick (see
        // WidgetAccentColorStore) actually shows up on the outline itself,
        // not just the photo border/dots below. Unconditional (before the
        // no-messages early return) so the chosen color still shows on an
        // empty widget too.
        val accentColor = WidgetAccentColorStore.resolveColor(context)
        val fillColor = ContextCompat.getColor(context, R.color.widget_bg_dark)
        remoteViews.setImageViewBitmap(
            R.id.shaped_widget_background,
            buildBackgroundBitmap(BACKGROUND_SIZE, fillColor, accentColor)
        )

        val myUid = FirebaseAuth.getInstance().currentUser?.uid

        if (messages.isEmpty()) {
            ReactionActionBinder.bindReactAction(context, remoteViews, appWidgetId, "")
            hideCarouselChrome(remoteViews)
            setAuthorName(remoteViews, showPhoto = false, name = "")
            remoteViews.setTextViewText(R.id.shaped_message_content, context.getString(R.string.widget_no_message))
            remoteViews.setViewVisibility(R.id.shaped_photo_container, View.GONE)
            return remoteViews
        }

        val window = displayWindow(messages, CarouselSettingsStore.load(context))
        val windowKey = window.joinToString(",") { it.id }
        val currentIndex = ShapedCarouselPageStore.indexForWindow(context, appWidgetId, windowKey, window.size)
        val message = window[currentIndex]

        ReactionActionBinder.bindReactAction(context, remoteViews, appWidgetId, message.id)

        // Best-effort "I looked at this" proxy for whichever message
        // happens to be on screen right now — purely feeds the Sent/Seen
        // mark below (and the History screen's own badge). It has no
        // effect on what the carousel shows or which page you're on
        // anymore, so there's no risk of a background refresh nobody
        // actually looked at silently shifting the window out from under
        // you — see displayWindow's comment for why that used to happen.
        FirebaseSync.markSeenIfNeeded(message)

        // A small "seen" mark on your OWN message once your partner's
        // last-seen-at has caught up to it — mirrors the Sent/Seen badge
        // MessageHistoryScreen already shows for your latest message, now
        // visible on the widget too.
        val lastSeenAt = FirebaseSync.fetchLastSeenAtOnce()
        val partnerSeenAt = lastSeenAt.filterKeys { it != myUid }.values.maxOrNull() ?: 0L
        val seenByPartner = message.authorUid == myUid && partnerSeenAt >= message.createdAt

        val baseAuthorName = if (message.authorUid == myUid) {
            message.authorName
        } else {
            FirebaseSync.fetchPartnerNicknameOnce().ifBlank { message.authorName }
        }
        val displayAuthorName = if (seenByPartner) {
            context.getString(R.string.widget_seen_suffix, baseAuthorName)
        } else {
            baseAuthorName
        }

        val hiddenByLock = message.isLocked && message.authorUid != myUid
        val content = when {
            hiddenByLock -> context.getString(R.string.widget_locked_message)
            // RemoteViews can't play video at all, so unlike photo/drawing
            // below there's no ImageView content to show alongside this —
            // the fallback text IS the whole widget's content for a video.
            message.isVideo -> message.caption.ifBlank { context.getString(R.string.widget_video_fallback) }
            // The image itself goes into shaped_message_photo below — this
            // is just the caption line under it (or a fallback if blank).
            message.isImage -> message.caption
            else -> message.content
        }
        remoteViews.setTextViewText(R.id.shaped_message_content, content)
        remoteViews.setViewVisibility(R.id.shaped_message_content, if (content.isNotBlank()) View.VISIBLE else View.GONE)

        var showPhoto = false
        if (message.isImage && !hiddenByLock) {
            val photoBitmap = if (message.photoUrl.isNotBlank()) loadBitmap(context, message.photoUrl) else null
            if (photoBitmap != null) {
                val masked = maskToBlobShape(photoBitmap, PHOTO_MASK_SIZE, accentColor)
                remoteViews.setImageViewBitmap(R.id.shaped_message_photo, masked)
                showPhoto = true
            } else {
                val fallback = if (message.type == "drawing") R.string.widget_drawing_fallback else R.string.widget_photo_fallback
                remoteViews.setTextViewText(R.id.shaped_message_content, content.ifBlank { context.getString(fallback) })
                remoteViews.setViewVisibility(R.id.shaped_message_content, View.VISIBLE)
            }
        }

        // The whole photo container (not just the photo ImageView) is
        // hidden for text-only messages — it carries layout_weight="3", so
        // leaving it visible-but-empty would still reserve most of the
        // card for nothing and squeeze the caption into a sliver.
        remoteViews.setViewVisibility(R.id.shaped_photo_container, if (showPhoto) View.VISIBLE else View.GONE)
        setAuthorName(remoteViews, showPhoto, displayAuthorName)

        // Carousel chrome shows whenever there's more than one message
        // available to page through, period — not gated by anyone's seen
        // status (see displayWindow below). The only time it's hidden is
        // when there simply isn't a second message yet to page to.
        if (window.size > 1) {
            remoteViews.setImageViewBitmap(R.id.shaped_carousel_dots, buildDotRowBitmap(window.size, currentIndex, accentColor))
            remoteViews.setViewVisibility(R.id.shaped_carousel_dots, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.btn_carousel_advance, View.VISIBLE)
            ReactionActionBinder.bindAdvanceAction(context, remoteViews, appWidgetId)
        } else {
            hideCarouselChrome(remoteViews)
        }

        return remoteViews
    }

    private fun hideCarouselChrome(remoteViews: RemoteViews) {
        remoteViews.setViewVisibility(R.id.shaped_carousel_dots, View.GONE)
        remoteViews.setViewVisibility(R.id.btn_carousel_advance, View.GONE)
    }

    // Newest-first, capped at `size` — always just "the latest N messages",
    // full stop, independent of anyone's seen/unseen status. Internal (not
    // private) so ShapedCarouselAdvanceReceiver can compute the same window
    // size to wrap its "next" tap against.
    internal fun displayWindow(messages: List<Message>, size: Int): List<Message> =
        messages.takeLast(size.coerceAtLeast(1)).asReversed()

    // Drawn as a single bitmap rather than one real view per dot — dots are
    // purely decorative (not individually tappable; btn_carousel_advance is
    // the only navigation), so there's no need to pay for N inflated views
    // plus N PendingIntents just to show N small circles.
    private fun buildDotRowBitmap(count: Int, activeIndex: Int, activeColor: Int): Bitmap {
        val dotSize = 40
        val spacing = 24
        val inactiveColor = 0x66FFFFFF
        val width = count * dotSize + (count - 1) * spacing
        val bitmap = Bitmap.createBitmap(width, dotSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until count) {
            val cx = i * (dotSize + spacing) + dotSize / 2f
            val cy = dotSize / 2f
            if (i == activeIndex) {
                paint.color = activeColor
                canvas.drawCircle(cx, cy, dotSize / 2f, paint)
            } else {
                paint.color = inactiveColor
                canvas.drawCircle(cx, cy, dotSize / 2.8f, paint)
            }
        }
        return bitmap
    }

    // Photo messages show the author name as a small label overlaid on the
    // photo itself; text-only messages (and the photo-load-failure
    // fallback, where showPhoto is also false) show it in its own row
    // above the caption instead — there's no photo to overlay it on there.
    private fun setAuthorName(remoteViews: RemoteViews, showPhoto: Boolean, name: String) {
        remoteViews.setTextViewText(R.id.shaped_author_name_overlay, name)
        remoteViews.setTextViewText(R.id.shaped_author_name_row, name)
        remoteViews.setViewVisibility(R.id.shaped_author_name_overlay, if (showPhoto) View.VISIBLE else View.GONE)
        remoteViews.setViewVisibility(R.id.shaped_author_name_row, if (showPhoto) View.GONE else View.VISIBLE)
    }

    // Renders the same silhouette widget_blob_shape.xml draws statically,
    // but with a caller-supplied border color instead of that XML's own
    // baked-in one — the only way to let a per-device accent color pick
    // actually affect the outline, since RemoteViews can't recolor a single
    // path inside a vector drawable at runtime.
    private fun buildBackgroundBitmap(size: Int, fillColor: Int, borderColor: Int): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val path = blobPath(size.toFloat())
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = fillColor })
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = size * 0.012f
                color = borderColor
            }
        )
        return output
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
            // Keeps this single-photo Parcel well under the Binder
            // transaction limit.
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
            CrashLogger.recordException("ShapedCarouselWidgetRenderer.loadBitmap failed (url=$url)", e)
            null
        }
    }
}
