package com.glimpse.app.ui.onthisday

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class LookbackPeriod(val days: Long) {
    WEEK(7), MONTH(30), THREE_MONTHS(90), SIX_MONTHS(180), YEAR(365)
}

sealed interface OnThisDayUiState {
    data object Idle : OnThisDayUiState
    data class Found(val message: Message, val period: LookbackPeriod) : OnThisDayUiState
}

// A little nostalgia hit on the compose screen — occasionally resurfaces an
// old message from roughly a week/month/season/year ago, without either of
// you needing to dig through History yourselves.
class OnThisDayViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<OnThisDayUiState>(OnThisDayUiState.Idle)
    val uiState: StateFlow<OnThisDayUiState> = _uiState.asStateFlow()

    // Once per calendar day, not once per screen visit — otherwise this
    // would resurface the same memory every time Compose is reopened.
    fun check() {
        val app = getApplication<Application>()
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_LAST_SHOWN_DATE, null) == today) return

        viewModelScope.launch {
            val messages = FirebaseSync.fetchAllMessages()
            val match = findMatch(messages)
            if (match != null) {
                _uiState.value = OnThisDayUiState.Found(match.first, match.second)
                prefs.edit { putString(KEY_LAST_SHOWN_DATE, today) }
            }
        }
    }

    fun dismiss() {
        _uiState.value = OnThisDayUiState.Idle
    }

    // Fixed day offsets rather than calendar month/year arithmetic — simpler
    // and avoids edge cases (different month lengths, leap years) for what's
    // meant to be an approximate "about this long ago" callout, not a
    // precise anniversary. Longest lookback first, so a message from a year
    // ago takes priority over one from last week when both exist.
    private fun findMatch(messages: List<Message>): Pair<Message, LookbackPeriod>? {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        for (period in LookbackPeriod.entries.sortedByDescending { it.days }) {
            val targetDate = today.minusDays(period.days)
            val match = messages.firstOrNull { message ->
                Instant.ofEpochMilli(message.createdAt).atZone(zone).toLocalDate() == targetDate
            }
            if (match != null) return match to period
        }
        return null
    }

    companion object {
        private const val PREFS_NAME = "on_this_day_prefs"
        private const val KEY_LAST_SHOWN_DATE = "last_shown_date"
    }
}
