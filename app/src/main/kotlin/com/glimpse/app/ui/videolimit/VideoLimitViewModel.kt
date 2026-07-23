package com.glimpse.app.ui.videolimit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.glimpse.app.data.VideoLimitStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VideoLimitUiState(
    val loaded: Boolean = false,
    val limitSeconds: Int = VideoLimitStore.DEFAULT_SECONDS
)

// Local-only, per-device — see VideoLimitStore.
class VideoLimitViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(VideoLimitUiState())
    val uiState: StateFlow<VideoLimitUiState> = _uiState.asStateFlow()

    fun load() {
        _uiState.value = VideoLimitUiState(loaded = true, limitSeconds = VideoLimitStore.load(getApplication()))
    }

    fun setLimitSeconds(seconds: Int) {
        VideoLimitStore.save(getApplication(), seconds)
        _uiState.value = _uiState.value.copy(limitSeconds = seconds)
    }
}
