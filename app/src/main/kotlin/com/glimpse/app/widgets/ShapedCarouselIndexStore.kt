package com.glimpse.app.widgets

import android.content.Context

// Per-appWidgetId "which page of the catch-up carousel is currently
// selected," dedicated to ShapedMessageWidget — kept isolated here rather
// than as a shared/generic widget utility, since this widget is the only
// one that has (or should have) a carousel.
object ShapedCarouselIndexStore {
    private const val PREFS_NAME = "shaped_carousel_index"
    private const val KEY_INDEX_PREFIX = "index_"
    private const val KEY_WINDOW_PREFIX = "window_"

    // Resets to 0 whenever the underlying message window's identity
    // changes (a new message arrived, or the seen-state shifted the
    // catch-up window elsewhere) — the catch-up flow should restart from
    // the oldest unseen rather than staying on a stale index into
    // different content. windowKey is expected to uniquely identify the
    // window's exact contents and order (see ShapedWidgetRenderer).
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

    // Called from ShapedMessageWidget.onDeleted so removed widget instances
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
