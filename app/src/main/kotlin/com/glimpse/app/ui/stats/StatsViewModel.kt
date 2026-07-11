package com.glimpse.app.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class PersonCount(val name: String, val count: Int)

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data class Loaded(
        val totalMessages: Int,
        val firstMessageAt: Long?,
        val countsByAuthor: List<PersonCount>,
        val topReaction: Pair<String, Int>?,
        val streakDays: Int
    ) : StatsUiState
}

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<StatsUiState>(StatsUiState.Loading)
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val messages = FirebaseSync.fetchAllMessages()
            _uiState.value = StatsUiState.Loaded(
                totalMessages = messages.size,
                firstMessageAt = messages.minByOrNull { it.createdAt }?.createdAt,
                countsByAuthor = countsByAuthor(messages),
                topReaction = topReaction(messages),
                streakDays = currentStreakDays(messages)
            )
        }
    }

    private fun countsByAuthor(messages: List<Message>): List<PersonCount> {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        // Latest display name seen per uid, in case it ever changes.
        val namesByUid = messages.associate { it.authorUid to it.authorName }
        return messages.groupingBy { it.authorUid }.eachCount()
            .map { (uid, count) ->
                val label = if (uid == myUid) "You" else namesByUid[uid].orEmpty().ifBlank { "Partner" }
                PersonCount(label, count)
            }
            .sortedByDescending { it.count }
    }

    private fun topReaction(messages: List<Message>): Pair<String, Int>? {
        val totals = mutableMapOf<String, Int>()
        messages.forEach { message ->
            message.reactions.forEach { (emoji, userIds) ->
                if (emoji == FirebaseSync.SEEN_EMOJI) return@forEach
                if (userIds.isEmpty()) return@forEach
                totals[emoji] = (totals[emoji] ?: 0) + userIds.size
            }
        }
        return totals.maxByOrNull { it.value }?.toPair()
    }

    // Consecutive days (counting back from today) with at least one message.
    // Today not having one yet doesn't zero the streak — same convention as
    // Duolingo/Snapchat, since the day isn't over.
    private fun currentStreakDays(messages: List<Message>): Int {
        val zone = ZoneId.systemDefault()
        val datesWithMessages = messages.map {
            Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate()
        }.toSet()
        if (datesWithMessages.isEmpty()) return 0

        var day = LocalDate.now(zone)
        if (day !in datesWithMessages) day = day.minusDays(1)
        var streak = 0
        while (day in datesWithMessages) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }
}
