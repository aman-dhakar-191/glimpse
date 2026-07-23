package com.glimpse.app.ui.widgetaccent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.glimpse.app.data.WidgetAccentColorStore
import com.glimpse.app.service.WidgetSyncTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WidgetAccentColorUiState(
    val loaded: Boolean = false,
    // Null = no custom accent chosen — the widget uses its original
    // adaptive default color instead (see WidgetAccentColorStore).
    val selectedColor: String? = null
)

// Local-only, per-device — see WidgetAccentColorStore.
class WidgetAccentColorViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(WidgetAccentColorUiState())
    val uiState: StateFlow<WidgetAccentColorUiState> = _uiState.asStateFlow()

    fun load() {
        _uiState.value = WidgetAccentColorUiState(
            loaded = true,
            selectedColor = WidgetAccentColorStore.load(getApplication())
        )
    }

    // hex == null resets to the original adaptive default.
    fun setColor(hex: String?) {
        val app = getApplication<Application>()
        if (hex == null) {
            WidgetAccentColorStore.clear(app)
        } else {
            WidgetAccentColorStore.save(app, hex)
        }
        _uiState.value = _uiState.value.copy(selectedColor = hex)
        // Takes effect immediately rather than waiting for the widget's own
        // periodic refresh — same reasoning as
        // CarouselSettingsViewModel.setAutoAdvanceMinutes.
        WidgetSyncTrigger.requestSync(app)
    }
}
