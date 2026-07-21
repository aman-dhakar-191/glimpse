package com.glimpse.app.ui.message

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.glimpse.app.data.repository.MessageRepository
import com.glimpse.app.notification.SendingNotifier
import com.glimpse.app.service.WidgetSyncTrigger
import com.glimpse.app.util.ConnectivityUtil
import com.glimpse.app.work.SendMessageWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ComposeUiState {
    data object Idle : ComposeUiState
    data object Sending : ComposeUiState
    // No connection right now — the send is still enqueued via WorkManager
    // and will go out the moment one comes back, no action needed from you.
    data object Queued : ComposeUiState
    data object Sent : ComposeUiState
    data class Error(val message: String) : ComposeUiState
}

class ComposeMessageViewModel(application: Application) : AndroidViewModel(application) {
    private val messageRepository = MessageRepository()

    private val _uiState = MutableStateFlow<ComposeUiState>(ComposeUiState.Idle)
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    // Text sends go through WorkManager (NetworkType.CONNECTED constraint)
    // instead of a direct Firebase call — with no signal, that call would
    // otherwise just hang until reconnection instead of failing or queuing
    // visibly. See SendMessageWorker for the retry/backoff behavior.
    fun sendMessage(content: String, unlockAt: Long = 0) {
        if (content.isBlank()) return
        val app = getApplication<Application>()
        _uiState.value = if (ConnectivityUtil.isConnected(app)) {
            ComposeUiState.Sending
        } else {
            ComposeUiState.Queued
        }

        val request = SendMessageWorker.buildRequest(content.trim(), unlockAt)
        val workManager = WorkManager.getInstance(app)
        workManager.enqueue(request)

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { info ->
                when (info?.state) {
                    WorkInfo.State.RUNNING -> _uiState.value = ComposeUiState.Sending
                    WorkInfo.State.SUCCEEDED -> _uiState.value = ComposeUiState.Sent
                    WorkInfo.State.FAILED -> _uiState.value =
                        ComposeUiState.Error("Couldn't send that message. Try again.")
                    WorkInfo.State.CANCELLED -> _uiState.value =
                        ComposeUiState.Error("Message send was cancelled.")
                    else -> Unit // ENQUEUED/BLOCKED — keep whatever was already set above
                }
            }
        }
    }

    // Photos aren't queued the same way: the picked/captured image's read
    // permission isn't guaranteed to survive a long background wait (the
    // modern photo picker's grant is scoped to the current session, not
    // persistable), so a deferred WorkManager retry could silently fail
    // later with no way to recover the image. Failing fast up front with a
    // clear message is more honest than promising offline delivery it can't
    // reliably provide.
    fun sendPhotoMessage(imageUri: Uri, caption: String, unlockAt: Long = 0) {
        val app = getApplication<Application>()
        if (!ConnectivityUtil.isConnected(app)) {
            _uiState.value = ComposeUiState.Error(
                "No internet connection. Photos can't be queued offline — try again once you're back online."
            )
            return
        }
        _uiState.value = ComposeUiState.Sending
        SendingNotifier.showSendingPhoto(app)
        viewModelScope.launch {
            messageRepository.sendPhotoMessage(imageUri, caption, unlockAt)
                .onSuccess {
                    SendingNotifier.cancel(app)
                    _uiState.value = ComposeUiState.Sent
                    WidgetSyncTrigger.requestSync(app)
                }
                .onFailure { throwable ->
                    SendingNotifier.cancel(app)
                    _uiState.value = ComposeUiState.Error(throwable.message ?: "Failed to send photo.")
                }
        }
    }

    // Fire-and-forget, no WorkManager queueing — a nudge is a lightweight
    // ping, not something worth retrying/persisting if it fails once. Reuses
    // ComposeUiState.Sent (same "sent" snackbar + heart-burst animation as a
    // regular send) rather than a dedicated state, since "you just sent
    // something" is exactly what happened.
    fun sendNudge() {
        viewModelScope.launch {
            messageRepository.sendNudge()
                .onSuccess { _uiState.value = ComposeUiState.Sent }
                .onFailure { throwable ->
                    _uiState.value = ComposeUiState.Error(throwable.message ?: "Couldn't send nudge.")
                }
        }
    }

    fun consumeSentState() {
        _uiState.value = ComposeUiState.Idle
    }
}
