package com.glimpse.app.ui.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.repository.MessageRepository
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

class ComposeMessageViewModel : ViewModel() {
    private val messageRepository = MessageRepository()

    private val _uiState = MutableStateFlow<ComposeUiState>(ComposeUiState.Idle)
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        _uiState.value = ComposeUiState.Sending
        viewModelScope.launch {
            messageRepository.sendMessage(content)
                .onSuccess { _uiState.value = ComposeUiState.Sent }
                .onFailure { throwable ->
                    _uiState.value = ComposeUiState.Error(throwable.message ?: "Failed to send message.")
                }
        }
    }

    fun consumeSentState() {
        _uiState.value = ComposeUiState.Idle
    }
}
