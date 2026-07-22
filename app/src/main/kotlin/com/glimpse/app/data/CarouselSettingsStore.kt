package com.glimpse.app.data

import android.content.Context
import androidx.core.content.edit

// Local-only, per-device (like quiet hours) — how many of the most recent
// messages the shaped widget's carousel pages through. Deliberately NOT
// seen/unseen-based (see ShapedWidgetRenderer.displayWindow): it's always
// just "the latest N", so a background widget refresh can never drift out
// of sync with per-user read state the way the old catch-up-only carousel
// did.
object CarouselSettingsStore {
    private const val PREFS_NAME = "carousel_settings"
    private const val KEY_SIZE = "size"
    const val DEFAULT_SIZE = 5

    // Upper bound both for what's selectable in Settings and for how many
    // messages the widget ever fetches/keeps in RemoteViews reach — see
    // ShapedWidgetRenderer.CAROUSEL_LIMIT.
    const val MAX_SIZE = 10
    val SIZE_OPTIONS = listOf(3, 5, 7, MAX_SIZE)

    fun load(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SIZE, DEFAULT_SIZE).coerceIn(1, MAX_SIZE)
    }

    fun save(context: Context, size: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putInt(KEY_SIZE, size.coerceIn(1, MAX_SIZE))
        }
    }
}
