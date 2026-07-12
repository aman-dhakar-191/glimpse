package com.glimpse.app.ui.widgetbackground

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.WidgetBackgroundPhotoStore
import com.glimpse.app.service.WidgetSyncTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface WidgetBackgroundUiState {
    data object Loading : WidgetBackgroundUiState
    data class Loaded(
        val hasPhoto: Boolean,
        val isSaving: Boolean = false,
        val error: String? = null
    ) : WidgetBackgroundUiState
}

class WidgetBackgroundViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<WidgetBackgroundUiState>(WidgetBackgroundUiState.Loading)
    val uiState: StateFlow<WidgetBackgroundUiState> = _uiState.asStateFlow()

    fun load() {
        val app = getApplication<Application>()
        _uiState.value = WidgetBackgroundUiState.Loaded(hasPhoto = WidgetBackgroundPhotoStore.exists(app))
    }

    fun setPhoto(uri: Uri) {
        val app = getApplication<Application>()
        val current = _uiState.value as? WidgetBackgroundUiState.Loaded
            ?: WidgetBackgroundUiState.Loaded(hasPhoto = false)
        _uiState.value = current.copy(isSaving = true, error = null)
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { WidgetBackgroundPhotoStore.save(app, uri) }
            if (success) {
                _uiState.value = WidgetBackgroundUiState.Loaded(hasPhoto = true)
                WidgetSyncTrigger.requestSync(app)
            } else {
                _uiState.value = current.copy(isSaving = false, error = "Couldn't use that photo. Try another one.")
            }
        }
    }

    fun clearPhoto() {
        val app = getApplication<Application>()
        val current = _uiState.value as? WidgetBackgroundUiState.Loaded
            ?: WidgetBackgroundUiState.Loaded(hasPhoto = true)
        _uiState.value = current.copy(isSaving = true, error = null)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { WidgetBackgroundPhotoStore.clear(app) }
            _uiState.value = WidgetBackgroundUiState.Loaded(hasPhoto = false)
            WidgetSyncTrigger.requestSync(app)
        }
    }
}
