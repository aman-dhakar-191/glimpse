package com.glimpse.app.ui.message

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.glimpse.app.R
import com.glimpse.app.data.VideoLimitStore
import com.glimpse.app.data.repository.MessageRepository
import com.glimpse.app.service.IncomingEvents
import com.glimpse.app.service.PhotoSendResults
import com.glimpse.app.service.PhotoSendService
import com.glimpse.app.service.WidgetSyncTrigger
import com.glimpse.app.util.ConnectivityUtil
import com.glimpse.app.util.CrashLogger
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

    // A distinct in-app burst for when your PARTNER sends YOU a "thinking of
    // you" — separate from ComposeUiState.Sent, which is about your OWN
    // sends (nudge included). Only fires while this ViewModel is alive to
    // show it live; FCMService's own notification channel/vibration covers
    // the case where the app wasn't open to see it.
    private val _thinkingOfYouBurst = MutableStateFlow(false)
    val thinkingOfYouBurst: StateFlow<Boolean> = _thinkingOfYouBurst.asStateFlow()

    // Same idea, for when your PARTNER reacts to one of YOUR messages —
    // carries the actual emoji reacted with, not just a generic burst.
    private val _reactionBurstEmoji = MutableStateFlow<String?>(null)
    val reactionBurstEmoji: StateFlow<String?> = _reactionBurstEmoji.asStateFlow()

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
        viewModelScope.launch {
            IncomingEvents.thinkingOfYou.collect { _thinkingOfYouBurst.value = true }
        }
        viewModelScope.launch {
            IncomingEvents.reactions.collect { emoji -> _reactionBurstEmoji.value = emoji }
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
                copyToCacheFile(app, imageUri, "outgoing_photos", "photo", "jpg")
            } catch (e: Exception) {
                CrashLogger.recordException("sendPhotoMessage: copyToCacheFile failed (uri=$imageUri, contentType=$contentType)", e)
                _uiState.value = ComposeUiState.Error("Couldn't read that photo. Try again.")
                return@launch
            }
            PhotoSendService.start(app, file, caption, unlockAt, contentType)
        }
    }

    // Same reasoning/durable-copy pattern as sendPhotoMessage above, plus a
    // hard byte-size cap — video files run far bigger than photos, and
    // Storage isn't free, so this is the one thing actually enforced before
    // ever starting an upload (the capture intent's duration-limit extra in
    // ComposeMessageScreen is only a hint some camera apps honor, not a
    // guarantee, and this also has to catch long videos picked from the
    // gallery, which never goes through that intent at all).
    fun sendVideoMessage(videoUri: Uri, caption: String, unlockAt: Long = 0) {
        val app = getApplication<Application>()
        if (!ConnectivityUtil.isConnected(app)) {
            _uiState.value = ComposeUiState.Error(
                "No internet connection. Videos can't be queued offline — try again once you're back online."
            )
            return
        }
        _uiState.value = ComposeUiState.Sending
        viewModelScope.launch {
            val file = try {
                copyToCacheFile(app, videoUri, "outgoing_videos", "video", "mp4")
            } catch (e: Exception) {
                CrashLogger.recordException("sendVideoMessage: copyToCacheFile failed (uri=$videoUri)", e)
                _uiState.value = ComposeUiState.Error("Couldn't read that video. Try again.")
                return@launch
            }
            if (file.length() > MAX_VIDEO_BYTES) {
                file.delete()
                _uiState.value = ComposeUiState.Error(
                    app.getString(R.string.compose_video_too_large, (MAX_VIDEO_BYTES / (1024 * 1024)).toInt())
                )
                return@launch
            }
            // Actually enforced, unlike the capture intent's duration-limit
            // extra (ComposeMessageScreen) — that's only a hint some camera
            // apps ignore, and it never applies at all to a video picked
            // from the gallery instead of recorded fresh. Reads the same
            // per-device setting (Settings screen) the intent hint itself
            // reads, so whichever one actually takes effect agrees with
            // the other.
            //
            // Even when a camera app DOES honor the hint, it doesn't cut at
            // the exact millisecond — there's stop-command/encoder latency,
            // so a "10s" recording routinely comes out as 10.3-11s. Without
            // VIDEO_DURATION_GRACE_MILLIS, that overshoot (which the user
            // never asked for and can't see) would get rejected as if they'd
            // deliberately recorded something way longer.
            val limitSeconds = VideoLimitStore.load(app)
            val durationMs = videoDurationMillis(file)
            if (durationMs != null && durationMs > limitSeconds * 1000L + VIDEO_DURATION_GRACE_MILLIS) {
                file.delete()
                _uiState.value = ComposeUiState.Error(
                    app.getString(R.string.compose_video_too_long, limitSeconds)
                )
                return@launch
            }
            PhotoSendService.start(app, file, caption, unlockAt, contentType = "video/mp4", messageType = "video")
        }
    }

    // release() (not the AutoCloseable close()/use{}, only added in API 29)
    // — minSdk here is 26, and release() has always existed.
    private fun videoDurationMillis(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun copyToCacheFile(context: Context, uri: Uri, dirName: String, filePrefix: String, extension: String): File {
        val dir = File(context.cacheDir, dirName).apply { mkdirs() }
        val file = File(dir, "${filePrefix}_${System.currentTimeMillis()}.$extension")
        val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open file")
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

    fun consumeThinkingOfYouBurst() {
        _thinkingOfYouBurst.value = false
    }

    fun consumeReactionBurst() {
        _reactionBurstEmoji.value = null
    }

    companion object {
        private const val MAX_VIDEO_BYTES = 25L * 1024 * 1024
        private const val VIDEO_DURATION_GRACE_MILLIS = 2000L
    }
}
