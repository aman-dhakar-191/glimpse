package com.glimpse.app.ui.message

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.repository.MessageRepository
import com.glimpse.app.service.WidgetSyncTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ComposeUiState {
    data object Idle : ComposeUiState
    data object Sending : ComposeUiState
    data object Sent : ComposeUiState
    data class Error(val message: String) : ComposeUiState
}

class ComposeMessageViewModel(application: Application) : AndroidViewModel(application) {
    private val messageRepository = MessageRepository()

    private val _uiState = MutableStateFlow<ComposeUiState>(ComposeUiState.Idle)
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        _uiState.value = ComposeUiState.Sending
        viewModelScope.launch {
            messageRepository.sendMessage(content)
                .onSuccess {
                    _uiState.value = ComposeUiState.Sent
                    // Restarting the sync service re-attaches a fresh Firebase
                    // listener, which fires immediately with the value we
                    // just wrote — without this, the widget only refreshes
                    // whenever its existing (possibly long-dead) listener
                    // happens to still be alive.
                    WidgetSyncTrigger.requestSync(getApplication())
                }
                .onFailure { throwable ->
                    _uiState.value = ComposeUiState.Error(throwable.message ?: "Failed to send message.")
                }
        }
    }

    fun consumeSentState() {
        _uiState.value = ComposeUiState.Idle
    }
}
