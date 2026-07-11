package com.glimpse.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.repository.AuthRepository
import com.glimpse.app.data.repository.NeedsPairingException
import com.glimpse.app.data.repository.PairingRepository
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    // Signed in with Google, but not yet in allowedUsers — shows the
    // "enter your partner's invite code" UI instead of a dead-end denial.
    // Carries its own submit/error sub-state (rather than bouncing through
    // the top-level Loading/Error variants) so a failed or in-flight
    // redemption doesn't kick the screen back to the plain sign-in button.
    data class NeedsPairing(val isSubmitting: Boolean = false, val error: String? = null) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

class LoginViewModel : ViewModel() {
    private val authRepository = AuthRepository()
    private val pairingRepository = PairingRepository()

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
                .onFailure { throwable -> handleAuthFailure(throwable) }
        }
    }

    // Called on every launch while a Firebase Auth session already exists —
    // a fresh Google sign-in only resolves pairing status once, so this is
    // what catches "redeemed a code on another device since you last opened
    // this one" or "still hasn't paired" on relaunch.
    fun checkPairingStatus(onPaired: () -> Unit) {
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            authRepository.checkPairingStatus()
                .onSuccess {
                    _uiState.value = LoginUiState.Idle
                    onPaired()
                }
                .onFailure { throwable -> handleAuthFailure(throwable) }
        }
    }

    fun redeemPairingCode(code: String, onPaired: () -> Unit) {
        _uiState.value = LoginUiState.NeedsPairing(isSubmitting = true)
        viewModelScope.launch {
            pairingRepository.redeemPairingCode(code)
                .onSuccess {
                    _uiState.value = LoginUiState.Idle
                    onPaired()
                }
                .onFailure { throwable ->
                    _uiState.value = LoginUiState.NeedsPairing(
                        isSubmitting = false,
                        error = throwable.message ?: "Couldn't redeem that code."
                    )
                }
        }
    }

    private fun handleAuthFailure(throwable: Throwable) {
        _uiState.value = if (throwable is NeedsPairingException) {
            LoginUiState.NeedsPairing()
        } else {
            LoginUiState.Error(throwable.message ?: "Sign-in failed.")
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
