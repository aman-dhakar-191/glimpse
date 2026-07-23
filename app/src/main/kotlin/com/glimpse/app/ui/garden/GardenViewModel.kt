package com.glimpse.app.ui.garden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.GardenGrowth
import com.glimpse.app.data.GardenStage
import com.glimpse.app.data.GardenWeather
import com.glimpse.app.data.GardenWeatherMapper
import com.glimpse.app.data.StreakCalculator
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import com.glimpse.app.util.CrashLogger
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// A still-locked time capsule, seen through the garden's "planted seed"
// metaphor — the underlying Message is completely unchanged (still just a
// regular message with unlockAt set); this is purely a different way of
// looking at the exact same isLocked messages History already hides.
data class GardenSeed(val messageId: String, val daysUntilBloom: Long, val plantedByMe: Boolean)

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
        val pendingSeeds: List<GardenSeed> = emptyList(),
        val wateredToday: Boolean = false,
        val isWatering: Boolean = false,
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
            val idleDaysFromMessages = StreakCalculator.daysSinceLastMessage(messages)
            // Ratchet BEFORE reading back, so a brand new peak this load
            // (e.g. today just extended the streak) is reflected in the
            // info this same load displays, not just the next one.
            FirebaseSync.raiseGardenPeakStreak(streakDays)
            val info = FirebaseSync.fetchGardenInfoOnce()
            val myMoodEmoji = FirebaseSync.fetchMyMoodOnce()
            val peakStreakDays = maxOf(info.peakStreakDays, streakDays)
            val daysSinceWatered = daysSince(info.lastWateredAt)
            // Whichever is more recent — a message or a tap of the Water
            // button — counts as "activity" for wilt purposes. The real
            // streak (streakDays above, and Stats' own display) is
            // completely untouched by this; watering only ever softens
            // how the garden LOOKS, never what it actually counts.
            val idleDays = listOfNotNull(idleDaysFromMessages, daysSinceWatered).minOrNull()
            _uiState.value = GardenUiState.Loaded(
                isNamed = info.isNamed,
                gardenName = info.name,
                namedByMe = info.namedBy == myUid,
                stage = GardenGrowth.currentStage(peakStreakDays, idleDays),
                isWilting = GardenGrowth.isWilting(idleDays),
                streakDays = streakDays,
                weather = GardenWeatherMapper.forMoodEmoji(myMoodEmoji),
                pendingSeeds = pendingSeeds(messages, myUid),
                wateredToday = daysSinceWatered == 0
            )
        }
    }

    private fun daysSince(epochMillis: Long): Int? {
        if (epochMillis <= 0) return null
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return ChronoUnit.DAYS.between(date, LocalDate.now()).toInt().coerceAtLeast(0)
    }

    // Reuses the SAME messages this load() already fetched for the streak
    // — no separate query, so a seed here is always exactly what History
    // would also show as still-locked, never a second, possibly-stale
    // notion of "locked."
    private fun pendingSeeds(messages: List<Message>, myUid: String): List<GardenSeed> {
        val today = LocalDate.now()
        return messages.filter { it.isLocked }
            .map { message ->
                val unlockDate = Instant.ofEpochMilli(message.unlockAt).atZone(ZoneId.systemDefault()).toLocalDate()
                GardenSeed(
                    messageId = message.id,
                    daysUntilBloom = ChronoUnit.DAYS.between(today, unlockDate).coerceAtLeast(0),
                    plantedByMe = message.authorUid == myUid
                )
            }
            .sortedBy { it.daysUntilBloom }
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

    fun waterGarden() {
        val current = _uiState.value as? GardenUiState.Loaded ?: return
        if (current.wateredToday || current.isWatering) return
        _uiState.value = current.copy(isWatering = true)
        viewModelScope.launch {
            FirebaseSync.waterGarden()
                .onSuccess { load() }
                .onFailure { throwable ->
                    CrashLogger.recordException("GardenViewModel.waterGarden failed", throwable)
                    _uiState.value = current.copy(isWatering = false)
                }
        }
    }
}
