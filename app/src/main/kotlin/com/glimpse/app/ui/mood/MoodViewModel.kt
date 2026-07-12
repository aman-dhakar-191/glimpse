package com.glimpse.app.ui.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.firebase.FirebaseSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MoodUiState {
    data object Loading : MoodUiState
    data class Loaded(
        val currentEmoji: String,
        val isSaving: Boolean = false,
        val error: String? = null
    ) : MoodUiState
}

// Same fetch-once pattern as OnThisDayViewModel/DailyPromptViewModel — no
// live listener, just refreshed whenever the compose screen is opened.
sealed interface PartnerMoodUiState {
    data object Loading : PartnerMoodUiState
    data class Loaded(val emoji: String) : PartnerMoodUiState
}

// Shared status line, separate from the message stream — see
// FirebaseSync.setMood for why it lives under shared/ (visible to your
// partner) rather than users/{myUid}/ (local-only, like the nickname).
class MoodViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<MoodUiState>(MoodUiState.Loading)
    val uiState: StateFlow<MoodUiState> = _uiState.asStateFlow()

    private val _partnerMood = MutableStateFlow<PartnerMoodUiState>(PartnerMoodUiState.Loading)
    val partnerMood: StateFlow<PartnerMoodUiState> = _partnerMood.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = MoodUiState.Loaded(currentEmoji = FirebaseSync.fetchMyMoodOnce())
        }
    }

    fun setMood(emoji: String) {
        val current = _uiState.value as? MoodUiState.Loaded ?: MoodUiState.Loaded(currentEmoji = "")
        _uiState.value = current.copy(isSaving = true, error = null)
        viewModelScope.launch {
            FirebaseSync.setMood(emoji)
                .onSuccess { _uiState.value = MoodUiState.Loaded(currentEmoji = emoji) }
                .onFailure { throwable ->
                    _uiState.value = current.copy(isSaving = false, error = throwable.message ?: "Couldn't update mood.")
                }
        }
    }

    // Same source WidgetRenderer.applyMoodStatus uses — this just also
    // surfaces it in-app instead of only on the widget.
    fun loadPartnerMood() {
        viewModelScope.launch {
            _partnerMood.value = PartnerMoodUiState.Loaded(emoji = FirebaseSync.fetchPartnerMoodOnce())
        }
    }
}
