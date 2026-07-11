package com.glimpse.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.repository.AuthRepository
import com.glimpse.app.data.repository.NotAllowedException
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data object AccessDenied : LoginUiState
    data class Error(val message: String) : LoginUiState
}

class LoginViewModel : ViewModel() {
    private val authRepository = AuthRepository()

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    val isSignedIn: Boolean get() = authRepository.currentUser != null

    fun onGoogleSignInResult(account: GoogleSignInAccount?, onSuccess: () -> Unit) {
        if (account?.idToken == null) {
            _uiState.value = LoginUiState.Error("Google sign-in was cancelled.")
            return
        }
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            authRepository.signInWithGoogle(account)
                .onSuccess {
                    _uiState.value = LoginUiState.Idle
                    onSuccess()
                }
                .onFailure { throwable ->
                    _uiState.value = if (throwable is NotAllowedException) {
                        LoginUiState.AccessDenied
                    } else {
                        LoginUiState.Error(throwable.message ?: "Sign-in failed.")
                    }
                }
        }
    }

    fun signOut() {
        authRepository.signOut()
        _uiState.value = LoginUiState.Idle
    }

    // Called on every launch where the user is already signed in, not just a
    // fresh sign-in — see AuthRepository.ensureFcmTokenRegistered for why
    // that matters.
    fun ensureFcmTokenRegistered() {
        viewModelScope.launch {
            authRepository.ensureFcmTokenRegistered()
        }
    }
}
