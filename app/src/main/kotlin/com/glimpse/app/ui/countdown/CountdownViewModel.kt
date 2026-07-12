package com.glimpse.app.ui.countdown

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.SpecialDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

sealed interface CountdownUiState {
    data object Loading : CountdownUiState
    data class Loaded(
        val specialDate: SpecialDate?,
        // null when no date is set; 0 means today.
        val daysUntil: Int?,
        val isSaving: Boolean = false,
        val error: String? = null
    ) : CountdownUiState
}

// Shared, single countdown for the pair — see FirebaseSync.setSpecialDate.
// Recurring (month/day only): "days until" always counts to the next
// occurrence, wrapping to next year once this year's has passed.
class CountdownViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<CountdownUiState>(CountdownUiState.Loading)
    val uiState: StateFlow<CountdownUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val date = FirebaseSync.fetchSpecialDateOnce()
            _uiState.value = CountdownUiState.Loaded(specialDate = date, daysUntil = date?.let(::daysUntil))
        }
    }

    fun setDate(label: String, month: Int, day: Int) {
        val current = _uiState.value as? CountdownUiState.Loaded
            ?: CountdownUiState.Loaded(specialDate = null, daysUntil = null)
        _uiState.value = current.copy(isSaving = true, error = null)
        viewModelScope.launch {
            FirebaseSync.setSpecialDate(label, month, day)
                .onSuccess {
                    val date = SpecialDate(label.trim(), month, day)
                    _uiState.value = CountdownUiState.Loaded(specialDate = date, daysUntil = daysUntil(date))
                }
                .onFailure { throwable ->
                    _uiState.value = current.copy(isSaving = false, error = throwable.message ?: "Couldn't save.")
                }
        }
    }

    fun clearDate() {
        viewModelScope.launch {
            FirebaseSync.clearSpecialDate()
            _uiState.value = CountdownUiState.Loaded(specialDate = null, daysUntil = null)
        }
    }

    private fun daysUntil(date: SpecialDate): Int {
        val today = LocalDate.now()
        // Feb 29 in a non-leap year has no exact match — falls back to Mar 1
        // rather than crashing; a once-every-few-years off-by-a-day for leap
        // birthdays is an acceptable tradeoff for keeping this simple.
        var next = try {
            LocalDate.of(today.year, date.month, date.day)
        } catch (e: java.time.DateTimeException) {
            LocalDate.of(today.year, 3, 1)
        }
        if (next.isBefore(today)) next = next.plusYears(1)
        return ChronoUnit.DAYS.between(today, next).toInt()
    }
}
