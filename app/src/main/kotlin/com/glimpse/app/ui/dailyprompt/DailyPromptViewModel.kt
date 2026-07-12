package com.glimpse.app.ui.dailyprompt

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.glimpse.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

sealed interface DailyPromptUiState {
    data object Hidden : DailyPromptUiState
    data class Visible(val prompt: String) : DailyPromptUiState
}

// Deterministic (day-of-year indexes into a fixed prompt list), not random —
// both of you need to land on the same question on the same day for this to
// work as a shared icebreaker, and there's no server round-trip needed to
// guarantee that since the list itself ships with the app.
class DailyPromptViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<DailyPromptUiState>(DailyPromptUiState.Hidden)
    val uiState: StateFlow<DailyPromptUiState> = _uiState.asStateFlow()

    fun check() {
        val app = getApplication<Application>()
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_DISMISSED_DATE, null) == today) return

        val prompts = app.resources.getStringArray(R.array.daily_prompts)
        if (prompts.isEmpty()) return
        val prompt = prompts[LocalDate.now().dayOfYear % prompts.size]
        _uiState.value = DailyPromptUiState.Visible(prompt)
    }

    fun dismiss() {
        val app = getApplication<Application>()
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_DISMISSED_DATE, LocalDate.now().toString()) }
        _uiState.value = DailyPromptUiState.Hidden
    }

    companion object {
        private const val PREFS_NAME = "daily_prompt_prefs"
        private const val KEY_DISMISSED_DATE = "dismissed_date"
    }
}
