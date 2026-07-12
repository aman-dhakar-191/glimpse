package com.glimpse.app.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import coil.ImageLoader
import coil.request.ImageRequest
import com.glimpse.app.R
import com.glimpse.app.data.WidgetCarouselIndexStore
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import com.google.firebase.auth.FirebaseAuth

// Shared between WidgetUpdateService (live Firebase listener, while its
// foreground service is allowed to run) and each AppWidgetProvider's own
// onUpdate (an immediate one-shot render that doesn't depend on that service
// being startable), so both paths produce identical widget output.
internal object WidgetRenderer {

    // Bounds both the Firebase fetch (FirebaseSync.fetchRecentMessagesOnce/
    // listenToHistory) and the RemoteViews payload — each carousel page can
    // carry its own downscaled photo, and RemoteViews' Parcel has to stay
    // well under the Binder transaction size limit even in a worst case of
    // several photo messages queued up in one catch-up window.
    const val CAROUSEL_LIMIT = 5

    // Both layouts use identical view IDs (author_name, message_content,
    // etc.) inside their carousel page layout, so buildPage/ReactionActionBinder
    // need no branching — only the layout resources (widget + page) differ
    // per size.
    private val SQUARE_SIZE = SizeF(110f, 110f)
    private val RECTANGULAR_SIZE = SizeF(250f, 110f)

    // For CurrentMessageWidget (and LatestMessageWidget, with latestOnly =
    // true — see that provider for why it exists alongside the carousel
    // instead of replacing it). Also opts into the responsive multi-size
    // RemoteViews on API 31+ as a bonus for launchers that support in-place
    // resize-to-switch-layout — SquareMessageWidget's own dedicated picker
    // entry (see renderSquare) is what makes the square shape available
    // everywhere else.
    suspend fun render(context: Context, appWidgetId: Int, messages: List<Message>, latestOnly: Boolean = false): RemoteViews {
        // The multi-size RemoteViews constructor (which lets the system pick
        // the best-fitting layout as the user resizes the widget) only
        // exists on API 31+ — older devices always get the rectangular
        // layout, same as before this feature existed.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return buildViews(
                context, appWidgetId, R.layout.widget_current_message, R.layout.widget_carousel_page, messages, latestOnly,
                allowPhoto = true
            )
        }

        // Both size variants get bundled into ONE RemoteViews and sent in a
        // single Binder transaction. Embedding the current page's photo in
        // BOTH would double that transaction's payload right at the point
        // it's already closest to the size limit (see loadPhoto in
        // buildPage) — that was silently failing for exactly this reason
        // whenever the current message was a photo. Only the size variant
        // actually on screen right now loads the real photo; the other
        // stays text-only until it becomes the active size, at which point
        // onAppWidgetOptionsChanged (see each provider) re-renders for real.
        val activeIsSquare = isCurrentlySquare(context, appWidgetId)
        val rectangularViews = buildViews(
            context, appWidgetId, R.layout.widget_current_message, R.layout.widget_carousel_page, messages, latestOnly,
            allowPhoto = !activeIsSquare
        )
        val squareViews = buildViews(
            context, appWidgetId, R.layout.widget_current_message_square, R.layout.widget_carousel_page_square, messages, latestOnly,
            allowPhoto = activeIsSquare
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
    // what determines its shape. Only ever one size variant in the
    // transaction, so no doubled-payload risk — always safe to load the photo.
    suspend fun renderSquare(context: Context, appWidgetId: Int, messages: List<Message>, latestOnly: Boolean = false): RemoteViews =
        buildViews(
            context, appWidgetId, R.layout.widget_current_message_square, R.layout.widget_carousel_page_square, messages, latestOnly,
            allowPhoto = true
        )

    // Approximates which of the two responsive size entries the system is
    // currently displaying, since RemoteViews exposes no direct "which
    // variant is active" query — compares the widget's current dp bounds
    // against each variant's aspect ratio (same signal the system itself
    // uses to pick a size entry). Defaults to rectangular (false) if
    // options aren't available yet, matching the pre-API-31 fallback.
    private fun isCurrentlySquare(context: Context, appWidgetId: Int): Boolean {
        return try {
            val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, -1)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, -1)
            if (width <= 0 || height <= 0) return false
            val ratio = width.toFloat() / height
            val squareDistance = kotlin.math.abs(ratio - SQUARE_SIZE.width / SQUARE_SIZE.height)
            val rectangularDistance = kotlin.math.abs(ratio - RECTANGULAR_SIZE.width / RECTANGULAR_SIZE.height)
            squareDistance < rectangularDistance
        } catch (e: Exception) {
            false
        }
    }

    // Whatever a render actually put on screen (see carouselWindow/
    // latestOnly below) is exactly what gets marked seen — kept here rather
    // than in each caller so "what counts as seen" has one definition next
    // to the rendering logic it has to match.
    suspend fun markSeenForRender(messages: List<Message>, latestOnly: Boolean = false) {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        val window = if (latestOnly) listOfNotNull(messages.lastOrNull()) else carouselWindow(messages, myUid)
        FirebaseSync.markMessagesSeenIfNeeded(window)
    }

    // Oldest-still-unseen through newest — a "catch up from where you left
    // off" order that ends on the most recent message, same as opening a
    // chat scrolled to your last read position. Falls back to just the
    // latest message (the widget's original single-message behavior) once
    // everything's been seen, so the common steady-state case still renders
    // the same single, long-proven page instead of a pointless 1-page
    // "carousel".
    internal fun carouselWindow(messages: List<Message>, myUid: String?): List<Message> {
        if (messages.isEmpty()) return emptyList()
        val firstUnseenIndex = messages.indexOfFirst { it.isUnseenBy(myUid) }
        return if (firstUnseenIndex == -1) listOf(messages.last()) else messages.subList(firstUnseenIndex, messages.size)
    }

    private fun Message.isUnseenBy(uid: String?): Boolean =
        authorUid != uid && reactions[FirebaseSync.SEEN_EMOJI]?.contains(uid) != true

    private suspend fun buildViews(
        context: Context,
        appWidgetId: Int,
        layoutRes: Int,
        pageLayoutRes: Int,
        messages: List<Message>,
        latestOnly: Boolean,
        allowPhoto: Boolean
    ): RemoteViews {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        // LatestMessageWidget's whole point is to always show just the
        // newest message, no catch-up backlog — the widget's original
        // behavior before the carousel existed.
        val window = if (latestOnly) listOfNotNull(messages.lastOrNull()) else carouselWindow(messages, myUid)
        val partnerNickname = FirebaseSync.fetchPartnerNicknameOnce()
        val partnerMood = FirebaseSync.fetchPartnerMoodOnce()

        val remoteViews = RemoteViews(context.packageName, layoutRes)
        ReactionActionBinder.bindOpenComposeAction(context, remoteViews, appWidgetId)

        if (window.isEmpty()) {
            remoteViews.addView(R.id.widget_carousel, buildEmptyPage(context, pageLayoutRes, appWidgetId, partnerMood))
            applyCarouselIndicator(context, remoteViews, appWidgetId, pageCount = 0, currentIndex = 0, latestOnly = latestOnly)
        } else {
            // The app now owns "which page is showing" entirely — there's
            // no more autoStart/flipInterval on the ViewFlipper (see
            // widget_current_message.xml), so nothing advances the page on
            // its own. Resets to 0 whenever the window's actual content
            // changes (see WidgetCarouselIndexStore), otherwise persists
            // across renders so a tap-selected page survives the next
            // Firebase-triggered refresh. Computed before building pages
            // since buildPage needs to know which one is current (see
            // loadPhoto below).
            val windowKey = window.joinToString(",") { it.id }
            val currentIndex = WidgetCarouselIndexStore.indexForWindow(context, appWidgetId, windowKey, window.size)

            window.forEachIndexed { index, message ->
                remoteViews.addView(
                    R.id.widget_carousel,
                    buildPage(
                        context, pageLayoutRes, appWidgetId, message, myUid, partnerNickname, partnerMood,
                        loadPhoto = allowPhoto && index == currentIndex
                    )
                )
            }
            remoteViews.setDisplayedChild(R.id.widget_carousel, currentIndex)
            applyCarouselIndicator(context, remoteViews, appWidgetId, window.size, currentIndex, latestOnly = latestOnly)
        }
        return remoteViews
    }

    // One dot per message actually in the window, the current one drawn
    // larger/filled — each dot is independently tappable and jumps straight
    // to that page (see ReactionActionBinder.bindCarouselJumpAction/
    // WidgetCarouselJumpReceiver).
    //
    // For carousel-capable widgets (latestOnly = false), this row is the
    // permanent visual marker for "this is the scrolling one": it stays
    // visible — even as a single dot — for as long as there's at least one
    // message, since otherwise a carousel widget sitting at its steady
    // state (only the latest message, nothing to page through yet) looks
    // pixel-identical to LatestMessageWidget. LatestMessageWidget itself
    // (latestOnly = true) never shows this row at all, so its absence is
    // what marks a widget as the non-scrolling kind.
    private fun applyCarouselIndicator(
        context: Context,
        remoteViews: RemoteViews,
        appWidgetId: Int,
        pageCount: Int,
        currentIndex: Int,
        latestOnly: Boolean
    ) {
        remoteViews.removeAllViews(R.id.carousel_indicator)
        if (latestOnly || pageCount < 1) {
            remoteViews.setViewVisibility(R.id.carousel_indicator, View.GONE)
            return
        }
        remoteViews.setViewVisibility(R.id.carousel_indicator, View.VISIBLE)
        for (i in 0 until pageCount) {
            val dotLayoutRes = if (i == currentIndex) R.layout.widget_carousel_dot_active else R.layout.widget_carousel_dot_inactive
            val dot = RemoteViews(context.packageName, dotLayoutRes)
            ReactionActionBinder.bindCarouselJumpAction(context, dot, R.id.dot_root, appWidgetId, i)
            remoteViews.addView(R.id.carousel_indicator, dot)
        }
    }

    // The message's stored authorName is whatever the sender's Google
    // account display name was at send time — this device's own
    // "what I call my partner" setting (see FirebaseSync.fetchPartnerNicknameOnce)
    // overrides that display, but only for messages that aren't mine, and
    // only locally: it never touches the stored message data itself.
    private suspend fun buildPage(
        context: Context,
        pageLayoutRes: Int,
        appWidgetId: Int,
        message: Message,
        myUid: String?,
        partnerNickname: String,
        partnerMood: String,
        loadPhoto: Boolean
    ): RemoteViews {
        val page = RemoteViews(context.packageName, pageLayoutRes)
        ReactionActionBinder.bindReactAction(context, page, appWidgetId, message.id)
        applyMoodStatus(page, partnerMood)

        val displayAuthorName = if (message.authorUid == myUid) {
            message.authorName
        } else {
            partnerNickname.ifBlank { message.authorName }
        }
        page.setTextViewText(R.id.author_name, displayAuthorName)

        // Only hidden from the recipient — same rule as MessageHistoryScreen.
        // Widgets aren't per-account UI in the OS sense, but this device is
        // signed into one specific account, so "am I the author" still
        // resolves the same way it does in-app.
        val hiddenByLock = message.isLocked && message.authorUid != myUid

        if (hiddenByLock) {
            page.setTextViewText(R.id.message_content, context.getString(R.string.widget_locked_message))
            page.setViewVisibility(R.id.message_content, View.VISIBLE)
            page.setViewVisibility(R.id.message_photo, View.GONE)
            page.setViewVisibility(R.id.photo_caption, View.GONE)
        } else {
            page.setTextViewText(R.id.message_content, message.content)
            // GONE views are skipped entirely when a LinearLayout distributes
            // weighted space, so hiding this when there's no text (photo
            // messages) hands its whole weighted share to message_photo instead
            // of splitting the box with an empty label.
            page.setViewVisibility(
                R.id.message_content,
                if (message.content.isNotBlank()) View.VISIBLE else View.GONE
            )

            if (message.type == "photo" && loadPhoto) {
                val photoBitmap = if (message.photoUrl.isNotBlank()) loadBitmap(context, message.photoUrl) else null
                if (photoBitmap != null) {
                    page.setImageViewBitmap(R.id.message_photo, photoBitmap)
                    page.setViewVisibility(R.id.message_photo, View.VISIBLE)
                } else {
                    page.setViewVisibility(R.id.message_photo, View.GONE)
                }
                page.setTextViewText(R.id.photo_caption, message.caption)
                page.setViewVisibility(
                    R.id.photo_caption,
                    if (message.caption.isNotBlank()) View.VISIBLE else View.GONE
                )
            } else if (message.type == "photo") {
                // Not the page currently on screen — skip downloading and
                // decoding its photo entirely. Several photo messages'
                // worth of downscaled bitmaps embedded in one RemoteViews
                // at once was enough to exceed the Binder transaction size
                // limit, which throws TransactionTooLargeException on
                // updateAppWidget() and (since every widget provider is
                // updated in the same coroutine, see WidgetUpdateService)
                // silently aborted every OTHER widget's update too, not
                // just this one — the real cause behind widgets appearing
                // to freeze entirely after a few photos were sent. Tapping
                // this page's dot re-renders with just that page's real
                // photo loaded (see WidgetCarouselJumpReceiver).
                page.setViewVisibility(R.id.message_photo, View.GONE)
                page.setViewVisibility(R.id.photo_caption, View.GONE)
                page.setTextViewText(
                    R.id.message_content,
                    message.caption.ifBlank { context.getString(R.string.widget_photo_fallback) }
                )
                page.setViewVisibility(R.id.message_content, View.VISIBLE)
            } else {
                page.setViewVisibility(R.id.message_photo, View.GONE)
                page.setViewVisibility(R.id.photo_caption, View.GONE)
            }
        }

        message.reactions.filterValues { it.isNotEmpty() }.forEach { (emoji, userIds) ->
            val chip = RemoteViews(context.packageName, R.layout.reaction_chip)
            chip.setTextViewText(R.id.reaction_text, "$emoji ${userIds.size}")
            page.addView(R.id.reactions_container, chip)
        }

        return page
    }

    private fun buildEmptyPage(context: Context, pageLayoutRes: Int, appWidgetId: Int, partnerMood: String): RemoteViews {
        val page = RemoteViews(context.packageName, pageLayoutRes)
        ReactionActionBinder.bindReactAction(context, page, appWidgetId, "")
        applyMoodStatus(page, partnerMood)
        page.setTextViewText(R.id.author_name, "")
        page.setTextViewText(R.id.message_content, context.getString(R.string.widget_no_message))
        page.setViewVisibility(R.id.message_content, View.VISIBLE)
        page.setViewVisibility(R.id.message_photo, View.GONE)
        page.setViewVisibility(R.id.photo_caption, View.GONE)
        return page
    }

    // A plain TextView leaf (partner_mood) next to author_name — see
    // FirebaseSync.setMood for why this is safe to show (shared, not
    // sensitive) and MoodViewModel for where it's set. Applied identically
    // to every page since it isn't message-specific.
    private fun applyMoodStatus(remoteViews: RemoteViews, partnerMood: String) {
        if (partnerMood.isNotBlank()) {
            remoteViews.setTextViewText(R.id.partner_mood, partnerMood)
            remoteViews.setViewVisibility(R.id.partner_mood, View.VISIBLE)
        } else {
            remoteViews.setViewVisibility(R.id.partner_mood, View.GONE)
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
}
