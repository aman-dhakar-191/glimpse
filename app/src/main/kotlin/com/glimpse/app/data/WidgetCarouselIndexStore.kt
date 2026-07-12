package com.glimpse.app.data

import android.content.Context

// Per-appWidgetId "which page of the catch-up carousel is currently
// selected." RemoteViews' ViewFlipper has no autoStart timer anymore (see
// WidgetRenderer) — the app owns this state entirely, and every render
// (whether triggered by new data or a tap on a dot) reads/writes it here
// and pushes the selected page + the matching highlighted dot in the same
// RemoteViews update, so the two can never drift out of sync the way an
// app-side timer racing ViewFlipper's own internal one would.
object WidgetCarouselIndexStore {
    private const val PREFS_NAME = "widget_carousel_index"
    private const val KEY_INDEX_PREFIX = "index_"
    private const val KEY_WINDOW_PREFIX = "window_"

    // Resets to 0 whenever the underlying message window's identity
    // changes (a new message arrived, or the seen-state shifted the
    // catch-up window elsewhere) — the catch-up flow should restart from
    // the oldest unseen rather than staying on a stale index into
    // different content. windowKey is expected to uniquely identify the
    // window's exact contents and order (see WidgetRenderer.buildViews).
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

    fun setIndex(context: Context, appWidgetId: Int, index: Int) {
        prefs(context).edit().putInt(KEY_INDEX_PREFIX + appWidgetId, index).apply()
    }

    // Called from each provider's onDeleted so removed widget instances
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
