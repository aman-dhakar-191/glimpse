package com.glimpse.app.ui.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.update.UpdateChecker
import com.glimpse.app.data.update.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val progress: Float) : UpdateUiState
    data class ReadyToInstall(val file: File) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdate() {
        if (_uiState.value !is UpdateUiState.Idle) return
        _uiState.value = UpdateUiState.Checking
        viewModelScope.launch {
            val info = UpdateChecker.checkForUpdate()
            _uiState.value = if (info != null) UpdateUiState.Available(info) else UpdateUiState.Idle
        }
    }

    fun downloadAndInstall() {
        val state = _uiState.value
        if (state !is UpdateUiState.Available) return
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Downloading(0f)
            val file = UpdateChecker.downloadApk(getApplication(), state.info) { progress ->
                _uiState.value = UpdateUiState.Downloading(progress)
            }
            if (file != null) {
                _uiState.value = UpdateUiState.ReadyToInstall(file)
                UpdateChecker.installApk(getApplication(), file)
            } else {
                _uiState.value = UpdateUiState.Error("Couldn't download the update. Try again later.")
            }
        }
    }

    fun dismiss() {
        _uiState.value = UpdateUiState.Idle
    }
}
