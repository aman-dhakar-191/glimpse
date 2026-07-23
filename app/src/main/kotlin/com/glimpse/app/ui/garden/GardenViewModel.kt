package com.glimpse.app.ui.garden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.GardenGrowth
import com.glimpse.app.data.GardenStage
import com.glimpse.app.data.GardenWeather
import com.glimpse.app.data.GardenWeatherMapper
import com.glimpse.app.data.StreakCalculator
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.util.CrashLogger
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GardenUiState {
    data object Loading : GardenUiState
    data class Loaded(
        val isNamed: Boolean,
        val gardenName: String,
        val namedByMe: Boolean,
        val stage: GardenStage,
        val isWilting: Boolean,
        val streakDays: Int,
        // Your OWN current mood, not your partner's — this is meant to
        // read as "here's how it feels to be looking at this right now,"
        // not a second display of what's already on the mood picker.
        val weather: GardenWeather,
        val isNaming: Boolean = false,
        val nameError: String? = null
    ) : GardenUiState
}

class GardenViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<GardenUiState>(GardenUiState.Loading)
    val uiState: StateFlow<GardenUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val myUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            val messages = FirebaseSync.fetchAllMessages()
            val streakDays = StreakCalculator.currentStreakDays(messages)
            val idleDays = StreakCalculator.daysSinceLastMessage(messages)
            // Ratchet BEFORE reading back, so a brand new peak this load
            // (e.g. today just extended the streak) is reflected in the
            // info this same load displays, not just the next one.
            FirebaseSync.raiseGardenPeakStreak(streakDays)
            val info = FirebaseSync.fetchGardenInfoOnce()
            val myMoodEmoji = FirebaseSync.fetchMyMoodOnce()
            val peakStreakDays = maxOf(info.peakStreakDays, streakDays)
            _uiState.value = GardenUiState.Loaded(
                isNamed = info.isNamed,
                gardenName = info.name,
                namedByMe = info.namedBy == myUid,
                stage = GardenGrowth.currentStage(peakStreakDays, idleDays),
                isWilting = GardenGrowth.isWilting(idleDays),
                streakDays = streakDays,
                weather = GardenWeatherMapper.forMoodEmoji(myMoodEmoji)
            )
        }
    }

    // Only ever called while the garden is still unnamed (see
    // GardenScreen) — a one-time shared ritual, not something either of
    // you can casually rename afterward.
    fun nameGarden(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val current = _uiState.value as? GardenUiState.Loaded ?: return
        _uiState.value = current.copy(isNaming = true, nameError = null)
        viewModelScope.launch {
            FirebaseSync.nameGarden(trimmed)
                .onSuccess { load() }
                .onFailure { throwable ->
                    CrashLogger.recordException("GardenViewModel.nameGarden failed", throwable)
                    _uiState.value = current.copy(
                        isNaming = false,
                        nameError = throwable.message ?: "Couldn't name your garden."
                    )
                }
        }
    }

    fun consumeNameError() {
        val current = _uiState.value as? GardenUiState.Loaded ?: return
        _uiState.value = current.copy(nameError = null)
    }
}
