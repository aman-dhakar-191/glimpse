package com.glimpse.app.ui.reaction

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.glimpse.app.util.ConnectivityUtil
import com.glimpse.app.work.SendReactionWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReactUiState {
    data object Idle : ReactUiState
    data object Sending : ReactUiState
    // No connection right now — still enqueued via WorkManager, goes out the
    // moment one comes back.
    data object Queued : ReactUiState
    data object Sent : ReactUiState
    data class Error(val message: String) : ReactUiState
}

class ReactionPickerViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<ReactUiState>(ReactUiState.Idle)
    val uiState: StateFlow<ReactUiState> = _uiState.asStateFlow()

    // Set from the React button's intent extra (MainActivity.EXTRA_REACT_MESSAGE_ID)
    // — reactions now attach to a specific message rather than an implicit
    // "current" one, since there can be many messages in history.
    private var targetMessageId: String = ""

    fun setTarget(messageId: String) {
        targetMessageId = messageId
    }

    // Same NetworkType.CONNECTED-backed WorkManager queue as sendMessage —
    // reactions are trivially small (a message id + emoji), so unlike
    // photos there's no durability concern queuing them offline.
    fun sendReaction(emoji: String) {
        if (emoji.isBlank()) return
        val app = getApplication<Application>()
        _uiState.value = if (ConnectivityUtil.isConnected(app)) {
            ReactUiState.Sending
        } else {
            ReactUiState.Queued
        }

        val request = SendReactionWorker.buildRequest(targetMessageId, emoji)
        val workManager = WorkManager.getInstance(app)
        workManager.enqueue(request)

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { info ->
                when (info?.state) {
                    WorkInfo.State.RUNNING -> _uiState.value = ReactUiState.Sending
                    WorkInfo.State.SUCCEEDED -> _uiState.value = ReactUiState.Sent
                    WorkInfo.State.FAILED -> _uiState.value =
                        ReactUiState.Error("Couldn't send that reaction. Try again.")
                    WorkInfo.State.CANCELLED -> _uiState.value =
                        ReactUiState.Error("Reaction send was cancelled.")
                    else -> Unit // ENQUEUED/BLOCKED — keep whatever was already set above
                }
            }
        }
    }

    fun consumeSentState() {
        _uiState.value = ReactUiState.Idle
    }
}
