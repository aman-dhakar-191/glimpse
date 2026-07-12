package com.glimpse.app.ui.history

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import com.glimpse.app.util.ImageSaver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SeenStatus {
    data object Sent : SeenStatus
    data class Seen(val at: Long) : SeenStatus
}

data class HistoryUiState(
    val messages: List<Message> = emptyList(),
    val myUid: String = "",
    // Only the most recent message gets a Sent/Seen tag (same convention as
    // iMessage/WhatsApp) — null when that message isn't yours, or there's
    // nothing to show yet.
    val lastMessageSeenStatus: SeenStatus? = null,
    val downloadResult: DownloadResult? = null,
    val searchQuery: String = ""
)

sealed interface DownloadResult {
    data object Success : DownloadResult
    data object Failure : DownloadResult
}

class MessageHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(HistoryUiState(myUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()))
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var messagesListener: ValueEventListener? = null
    private var lastSeenListener: ValueEventListener? = null
    private var messages: List<Message> = emptyList()
    private var lastSeenAt: Map<String, Long> = emptyMap()

    fun start() {
        if (messagesListener != null) return
        messagesListener = FirebaseSync.listenToHistory(HISTORY_LIMIT) { fetched ->
            messages = fetched
            recompute()
            viewModelScope.launch { FirebaseSync.markSeenIfNeeded(fetched.lastOrNull()) }
        }
        lastSeenListener = FirebaseSync.listenToLastSeenAt { values ->
            lastSeenAt = values
            recompute()
        }
    }

    private fun recompute() {
        val myUid = _uiState.value.myUid
        val last = messages.lastOrNull()
        val status = if (last != null && last.authorUid == myUid) {
            val seenAt = lastSeenAt.filterKeys { it != myUid }.values.maxOrNull()
            if (seenAt != null && seenAt >= last.createdAt) SeenStatus.Seen(seenAt) else SeenStatus.Sent
        } else {
            null
        }
        _uiState.value = _uiState.value.copy(messages = messages, lastMessageSeenStatus = status)
    }

    fun downloadImage(context: Context, imageUrl: String) {
        viewModelScope.launch {
            val success = ImageSaver.saveToGallery(context, imageUrl)
            _uiState.value = _uiState.value.copy(
                downloadResult = if (success) DownloadResult.Success else DownloadResult.Failure
            )
        }
    }

    fun consumeDownloadResult() {
        _uiState.value = _uiState.value.copy(downloadResult = null)
    }

    // Filtering happens client-side in the screen (see MessageHistoryScreen)
    // against the already-loaded HISTORY_LIMIT window — Realtime Database
    // has no real full-text search, and 50 messages is a small enough set
    // that a server round-trip would just be slower than filtering locally.
    fun search(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    override fun onCleared() {
        messagesListener?.let { FirebaseSync.removeHistoryListener(HISTORY_LIMIT, it) }
        lastSeenListener?.let { FirebaseSync.removeLastSeenAtListener(it) }
        super.onCleared()
    }

    companion object {
        private const val HISTORY_LIMIT = 50
    }
}
