package com.glimpse.app.widgets

import android.content.Context

// Per-appWidgetId "which page of the latest-N carousel is currently
// selected," dedicated to the separate ShapedCarouselWidget — kept isolated
// here rather than as a shared/generic widget utility, since the plain
// ShapedMessageWidget has no carousel at all.
object ShapedCarouselPageStore {
    private const val PREFS_NAME = "shaped_carousel_page"
    private const val KEY_INDEX_PREFIX = "index_"
    private const val KEY_WINDOW_PREFIX = "window_"

    // Resets to 0 (the newest message — see ShapedCarouselWidgetRenderer's
    // newest-first ordering) whenever the underlying latest-N window's
    // exact contents change, e.g. a new message arrived and shifted who's
    // in the window — otherwise persists across renders so a tapped-to
    // page survives the next Firebase-triggered refresh. windowKey is
    // expected to uniquely identify the window's exact contents and order.
    fun indexForWindow(context: Context, appWidgetId: Int, windowKey: String, windowSize: Int): Int {
        if (windowSize <= 0) return 0
        val prefs = prefs(context)
        val storedWindowKey = prefs.getString(KEY_WINDOW_PREFIX + appWidgetId, null)
        return if (storedWindowKey != windowKey) {
            prefs.edit()
                .putString(KEY_WINDOW_PREFIX + appWidgetId, windowKey)
                .putInt(KEY_INDEX_PREFIX + appWidgetId, 0)
                .apply()
            0
        } else {
            prefs.getInt(KEY_INDEX_PREFIX + appWidgetId, 0).coerceIn(0, windowSize - 1)
        }
    }

    // windowSize passed in again so the wraparound always reflects the
    // window at the moment of the tap, not whatever it was when this
    // instance was last rendered.
    fun advance(context: Context, appWidgetId: Int, windowSize: Int) {
        if (windowSize <= 0) return
        val prefs = prefs(context)
        val current = prefs.getInt(KEY_INDEX_PREFIX + appWidgetId, 0)
        val next = (current + 1) % windowSize
        prefs.edit().putInt(KEY_INDEX_PREFIX + appWidgetId, next).apply()
    }

    // Called from ShapedCarouselWidget.onDeleted so removed widget instances
    // don't leave stale entries behind indefinitely.
    fun clear(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove(KEY_INDEX_PREFIX + appWidgetId)
            .remove(KEY_WINDOW_PREFIX + appWidgetId)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
