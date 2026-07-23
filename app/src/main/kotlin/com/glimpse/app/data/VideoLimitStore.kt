package com.glimpse.app.data

import android.content.Context
import androidx.core.content.edit

// Local-only, per-device (like quiet hours/carousel settings) — how long a
// video note can run before ComposeMessageViewModel.sendVideoMessage
// rejects it. Also read by ComposeMessageScreen's capture intent as the
// EXTRA_DURATION_LIMIT hint, though that's never guaranteed to be honored
// by every camera app — this store's value is what's actually enforced,
// regardless of source (recorded or picked from the gallery).
object VideoLimitStore {
    private const val PREFS_NAME = "video_limit_prefs"
    private const val KEY_SECONDS = "limit_seconds"
    const val DEFAULT_SECONDS = 30
    val OPTIONS = listOf(10, 15, 30, 60)

    fun load(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_SECONDS, DEFAULT_SECONDS)

    fun save(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putInt(KEY_SECONDS, seconds)
        }
    }
}
