package com.glimpse.app.ui.nickname

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.firebase.FirebaseSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface NicknameUiState {
    data object Loading : NicknameUiState
    data class Loaded(
        val nickname: String,
        val isSaving: Boolean = false,
        val error: String? = null,
        // Drives a brief "Saved" confirmation instead of leaving the button
        // in a permanently ambiguous post-save state.
        val justSaved: Boolean = false
    ) : NicknameUiState
}

class NicknameViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<NicknameUiState>(NicknameUiState.Loading)
    val uiState: StateFlow<NicknameUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = NicknameUiState.Loaded(nickname = FirebaseSync.fetchPartnerNicknameOnce())
        }
    }

    fun save(nickname: String) {
        val current = _uiState.value as? NicknameUiState.Loaded ?: return
        _uiState.value = current.copy(isSaving = true, error = null, justSaved = false)
        viewModelScope.launch {
            FirebaseSync.setPartnerNickname(nickname)
                .onSuccess {
                    _uiState.value = NicknameUiState.Loaded(nickname = nickname.trim(), justSaved = true)
                }
                .onFailure { throwable ->
                    _uiState.value = current.copy(
                        isSaving = false,
                        error = throwable.message ?: "Couldn't save. Try again."
                    )
                }
        }
    }

    fun consumeSavedConfirmation() {
        val current = _uiState.value as? NicknameUiState.Loaded ?: return
        _uiState.value = current.copy(justSaved = false)
    }
}
