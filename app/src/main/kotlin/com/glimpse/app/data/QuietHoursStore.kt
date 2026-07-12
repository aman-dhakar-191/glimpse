package com.glimpse.app.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalTime

// Local-only, per-device (like the widget background photo) — each of you
// might keep a different sleep schedule, so this only ever suppresses the
// visible notification popup on the device it's set on, never anything
// shared. See FCMService/StreakCheckWorker for where isQuietNow gates
// notify() calls; the widget content itself still refreshes silently
// either way (WidgetSyncTrigger.requestSync is unconditional).
object QuietHoursStore {
    private const val PREFS_NAME = "quiet_hours_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_START_MINUTES = "start_minutes"
    private const val KEY_END_MINUTES = "end_minutes"
    private const val DEFAULT_START_MINUTES = 22 * 60 // 10:00 PM
    private const val DEFAULT_END_MINUTES = 7 * 60 // 7:00 AM

    data class QuietHours(val enabled: Boolean, val startMinutes: Int, val endMinutes: Int)

    fun load(context: Context): QuietHours {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return QuietHours(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            startMinutes = prefs.getInt(KEY_START_MINUTES, DEFAULT_START_MINUTES),
            endMinutes = prefs.getInt(KEY_END_MINUTES, DEFAULT_END_MINUTES)
        )
    }

    fun save(context: Context, enabled: Boolean, startMinutes: Int, endMinutes: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ENABLED, enabled)
            putInt(KEY_START_MINUTES, startMinutes)
            putInt(KEY_END_MINUTES, endMinutes)
        }
    }

    fun isQuietNow(context: Context): Boolean {
        val quietHours = load(context)
        if (!quietHours.enabled) return false
        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        return if (quietHours.startMinutes <= quietHours.endMinutes) {
            nowMinutes in quietHours.startMinutes until quietHours.endMinutes
        } else {
            // Wraps past midnight (e.g. 22:00 -> 07:00).
            nowMinutes >= quietHours.startMinutes || nowMinutes < quietHours.endMinutes
        }
    }
}
