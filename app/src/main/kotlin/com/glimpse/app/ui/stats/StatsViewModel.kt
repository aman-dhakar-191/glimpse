package com.glimpse.app.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.StreakCalculator
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
            val partnerNickname = FirebaseSync.fetchPartnerNicknameOnce()
            _uiState.value = StatsUiState.Loaded(
                totalMessages = messages.size,
                firstMessageAt = messages.minByOrNull { it.createdAt }?.createdAt,
                countsByAuthor = countsByAuthor(messages, partnerNickname),
                topReaction = topReaction(messages),
                streakDays = StreakCalculator.currentStreakDays(messages)
            )
        }
    }

    private fun countsByAuthor(messages: List<Message>, partnerNickname: String): List<PersonCount> {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        // Latest display name seen per uid, in case it ever changes.
        val namesByUid = messages.associate { it.authorUid to it.authorName }
        return messages.groupingBy { it.authorUid }.eachCount()
            .map { (uid, count) ->
                val label = if (uid == myUid) {
                    "You"
                } else {
                    partnerNickname.ifBlank { namesByUid[uid].orEmpty().ifBlank { "Partner" } }
                }
                PersonCount(label, count)
            }
            .sortedByDescending { it.count }
    }

    private fun topReaction(messages: List<Message>): Pair<String, Int>? {
        val totals = mutableMapOf<String, Int>()
        messages.forEach { message ->
            message.reactions.forEach { (emoji, userIds) ->
                if (userIds.isEmpty()) return@forEach
                totals[emoji] = (totals[emoji] ?: 0) + userIds.size
            }
        }
        return totals.maxByOrNull { it.value }?.toPair()
    }
}
