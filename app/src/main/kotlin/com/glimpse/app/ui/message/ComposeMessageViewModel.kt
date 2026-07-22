package com.glimpse.app.ui.message

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.glimpse.app.data.repository.MessageRepository
import com.glimpse.app.service.PhotoSendResults
import com.glimpse.app.service.PhotoSendService
import com.glimpse.app.service.WidgetSyncTrigger
import com.glimpse.app.util.ConnectivityUtil
import com.glimpse.app.work.SendMessageWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

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

    init {
        // PhotoSendService posts here when it finishes — only relevant
        // while this ViewModel is actually alive to show it; the service's
        // own job doesn't wait on it (see PhotoSendResults).
        viewModelScope.launch {
            PhotoSendResults.photoResults.collect { result ->
                result.onSuccess {
                    _uiState.value = ComposeUiState.Sent
                }.onFailure { throwable ->
                    _uiState.value = ComposeUiState.Error(throwable.message ?: "Failed to send photo.")
                }
            }
        }
    }

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

    // Photos aren't queued via WorkManager the way text is: the picked/
    // captured image's read permission isn't guaranteed to survive a long
    // background wait (the modern photo picker's grant is scoped to the
    // current session, not persistable), so a deferred retry could
    // silently fail later with no way to recover the image. Instead, copy
    // the bytes into our own cache file right away — while that access is
    // still guaranteed — then hand the durable local copy off to
    // PhotoSendService, a real foreground service that survives the app
    // closing, for the actual upload.
    fun sendPhotoMessage(imageUri: Uri, caption: String, unlockAt: Long = 0) {
        val app = getApplication<Application>()
        if (!ConnectivityUtil.isConnected(app)) {
            _uiState.value = ComposeUiState.Error(
                "No internet connection. Photos can't be queued offline — try again once you're back online."
            )
            return
        }
        _uiState.value = ComposeUiState.Sending
        viewModelScope.launch {
            val contentType = app.contentResolver.getType(imageUri) ?: "image/jpeg"
            val file = try {
                copyToCacheFile(app, imageUri)
            } catch (e: Exception) {
                _uiState.value = ComposeUiState.Error("Couldn't read that photo. Try again.")
                return@launch
            }
            PhotoSendService.start(app, file, caption, unlockAt, contentType)
        }
    }

    private fun copyToCacheFile(context: Context, uri: Uri): File {
        val dir = File(context.cacheDir, "outgoing_photos").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open photo")
        input.use { stream -> file.outputStream().use { output -> stream.copyTo(output) } }
        return file
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
