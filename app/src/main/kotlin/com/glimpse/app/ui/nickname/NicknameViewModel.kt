package com.glimpse.app.ui.nickname

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.PartnerNicknameStore
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

// AndroidViewModel (not a plain ViewModel) for the Application context
// PartnerNicknameStore needs — every load/save mirrors the nickname into
// that local store so FCMService can read it synchronously while handling
// an incoming nudge. See PartnerNicknameStore for why that matters.
class NicknameViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<NicknameUiState>(NicknameUiState.Loading)
    val uiState: StateFlow<NicknameUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val nickname = FirebaseSync.fetchPartnerNicknameOnce()
            PartnerNicknameStore.save(getApplication(), nickname)
            _uiState.value = NicknameUiState.Loaded(nickname = nickname)
        }
    }

    fun save(nickname: String) {
        val current = _uiState.value as? NicknameUiState.Loaded ?: return
        _uiState.value = current.copy(isSaving = true, error = null, justSaved = false)
        viewModelScope.launch {
            FirebaseSync.setPartnerNickname(nickname)
                .onSuccess {
                    PartnerNicknameStore.save(getApplication(), nickname)
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
