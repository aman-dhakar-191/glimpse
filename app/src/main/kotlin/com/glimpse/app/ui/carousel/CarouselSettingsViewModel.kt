package com.glimpse.app.ui.carousel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.glimpse.app.data.CarouselSettingsStore
import com.glimpse.app.work.CarouselAutoAdvanceWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CarouselSettingsUiState(
    val loaded: Boolean = false,
    val size: Int = CarouselSettingsStore.DEFAULT_SIZE,
    val autoAdvanceMinutes: Int = CarouselSettingsStore.AUTO_ADVANCE_OFF
)

// Local-only, per-device — see CarouselSettingsStore.
class CarouselSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(CarouselSettingsUiState())
    val uiState: StateFlow<CarouselSettingsUiState> = _uiState.asStateFlow()

    fun load() {
        _uiState.value = CarouselSettingsUiState(
            loaded = true,
            size = CarouselSettingsStore.load(getApplication()),
            autoAdvanceMinutes = CarouselSettingsStore.loadAutoAdvanceMinutes(getApplication())
        )
    }

    fun setSize(size: Int) {
        CarouselSettingsStore.save(getApplication(), size)
        _uiState.value = _uiState.value.copy(size = size)
    }

    fun setAutoAdvanceMinutes(minutes: Int) {
        CarouselSettingsStore.saveAutoAdvanceMinutes(getApplication(), minutes)
        _uiState.value = _uiState.value.copy(autoAdvanceMinutes = minutes)
        // Takes effect immediately rather than waiting for the next app
        // launch's reschedule() call (see MainActivity.onSignedIn) — UPDATE
        // policy inside reschedule() means this also correctly cancels any
        // previously-running schedule when switching to Off.
        CarouselAutoAdvanceWorker.reschedule(getApplication())
    }
}
