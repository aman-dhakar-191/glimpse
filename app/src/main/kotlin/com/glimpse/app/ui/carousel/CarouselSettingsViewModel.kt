package com.glimpse.app.ui.carousel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.glimpse.app.data.CarouselSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CarouselSettingsUiState(
    val loaded: Boolean = false,
    val size: Int = CarouselSettingsStore.DEFAULT_SIZE
)

// Local-only, per-device — see CarouselSettingsStore.
class CarouselSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(CarouselSettingsUiState())
    val uiState: StateFlow<CarouselSettingsUiState> = _uiState.asStateFlow()

    fun load() {
        _uiState.value = CarouselSettingsUiState(loaded = true, size = CarouselSettingsStore.load(getApplication()))
    }

    fun setSize(size: Int) {
        CarouselSettingsStore.save(getApplication(), size)
        _uiState.value = _uiState.value.copy(size = size)
    }
}
