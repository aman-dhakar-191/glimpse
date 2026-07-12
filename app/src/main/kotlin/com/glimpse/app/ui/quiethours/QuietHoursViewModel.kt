package com.glimpse.app.ui.quiethours

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.glimpse.app.data.QuietHoursStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QuietHoursUiState(
    val loaded: Boolean = false,
    val enabled: Boolean = false,
    val startMinutes: Int = 22 * 60,
    val endMinutes: Int = 7 * 60
)

// Local-only, per-device — see QuietHoursStore.
class QuietHoursViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(QuietHoursUiState())
    val uiState: StateFlow<QuietHoursUiState> = _uiState.asStateFlow()

    fun load() {
        val stored = QuietHoursStore.load(getApplication())
        _uiState.value = QuietHoursUiState(
            loaded = true,
            enabled = stored.enabled,
            startMinutes = stored.startMinutes,
            endMinutes = stored.endMinutes
        )
    }

    fun setEnabled(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(enabled = enabled)
        persist(current.copy(enabled = enabled))
    }

    fun setStartMinutes(minutes: Int) {
        val current = _uiState.value
        _uiState.value = current.copy(startMinutes = minutes)
        persist(current.copy(startMinutes = minutes))
    }

    fun setEndMinutes(minutes: Int) {
        val current = _uiState.value
        _uiState.value = current.copy(endMinutes = minutes)
        persist(current.copy(endMinutes = minutes))
    }

    private fun persist(state: QuietHoursUiState) {
        QuietHoursStore.save(getApplication(), state.enabled, state.startMinutes, state.endMinutes)
    }
}
