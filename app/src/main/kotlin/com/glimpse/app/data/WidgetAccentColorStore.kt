package com.glimpse.app.data

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.glimpse.app.R

// Local-only, per-device (like quiet hours/carousel settings) — each of you
// can pick your own accent color for your OWN home-screen widget; nothing
// here is shared or synced, and it has no effect on what your partner sees.
object WidgetAccentColorStore {
    private const val PREFS_NAME = "widget_accent_color"
    private const val KEY_COLOR = "color_hex"

    // Null = no custom accent chosen yet. Deliberately NOT a hardcoded
    // fallback hex — the existing widget_border color resource already
    // adapts to light/dark system theme (it's a different value in
    // values-night/colors.xml), so "unset" needs to keep resolving through
    // that resource rather than freezing on whichever theme happened to be
    // active the first time this was read.
    fun load(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_COLOR, null)

    fun save(context: Context, hex: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_COLOR, hex)
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_COLOR)
        }
    }

    // The actual color int either renderer should paint the widget's
    // border/dots with — a chosen accent if there is one, otherwise the
    // same adaptive default as before this feature existed.
    fun resolveColor(context: Context): Int {
        val hex = load(context)
        val parsed = hex?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
        return parsed ?: ContextCompat.getColor(context, R.color.widget_border)
    }
}
