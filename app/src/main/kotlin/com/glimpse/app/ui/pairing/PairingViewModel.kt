package com.glimpse.app.ui.pairing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.repository.PairingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PairingUiState {
    data object Idle : PairingUiState
    data object Loading : PairingUiState
    data class CodeReady(val code: String, val expiresAt: Long) : PairingUiState
    data class Error(val message: String) : PairingUiState
}

class PairingViewModel(application: Application) : AndroidViewModel(application) {
    private val pairingRepository = PairingRepository()

    private val _uiState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    fun generateCode() {
        _uiState.value = PairingUiState.Loading
        viewModelScope.launch {
            pairingRepository.createPairingCode()
                .onSuccess { info ->
                    _uiState.value = PairingUiState.CodeReady(info.code, info.expiresAt)
                }
                .onFailure { throwable ->
                    _uiState.value = PairingUiState.Error(throwable.message ?: "Couldn't create an invite code.")
                }
        }
    }
}
