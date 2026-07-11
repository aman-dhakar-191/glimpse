package com.glimpse.app.ui.reaction

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.repository.MessageRepository
import com.glimpse.app.service.WidgetSyncTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReactUiState {
    data object Idle : ReactUiState
    data object Sending : ReactUiState
    data object Sent : ReactUiState
    data class Error(val message: String) : ReactUiState
}

class ReactionPickerViewModel(application: Application) : AndroidViewModel(application) {
    private val messageRepository = MessageRepository()

    private val _uiState = MutableStateFlow<ReactUiState>(ReactUiState.Idle)
    val uiState: StateFlow<ReactUiState> = _uiState.asStateFlow()

    // Set from the React button's intent extra (MainActivity.EXTRA_REACT_MESSAGE_ID)
    // — reactions now attach to a specific message rather than an implicit
    // "current" one, since there can be many messages in history.
    private var targetMessageId: String = ""

    fun setTarget(messageId: String) {
        targetMessageId = messageId
    }

    fun sendReaction(emoji: String) {
        if (emoji.isBlank()) return
        _uiState.value = ReactUiState.Sending
        viewModelScope.launch {
            messageRepository.addReaction(targetMessageId, emoji)
                .onSuccess {
                    _uiState.value = ReactUiState.Sent
                    // Restarting the sync service re-attaches a fresh Firebase
                    // listener, which fires immediately with the value we just
                    // wrote — without this the widget only refreshes whenever
                    // its existing (possibly long-dead) listener happens to
                    // still be alive.
                    WidgetSyncTrigger.requestSync(getApplication())
                }
                .onFailure { throwable ->
                    _uiState.value = ReactUiState.Error(throwable.message ?: "Failed to send reaction.")
                }
        }
    }

    fun consumeSentState() {
        _uiState.value = ReactUiState.Idle
    }
}
