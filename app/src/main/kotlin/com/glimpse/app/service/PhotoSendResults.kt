package com.glimpse.app.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Lets ComposeMessageViewModel/DrawingViewModel show live Sending/Sent/Error
// feedback while the app is open, without PhotoSendService needing to know
// whether anything is listening — the actual send (backed by
// startForeground, so it survives the app closing) doesn't depend on this
// at all. No replay: if nothing was collecting when a result posted (the
// app was closed), the message is already in Firebase by the time anyone
// reopens the app, so there's nothing to retroactively show.
//
// Split into a separate flow per message type (rather than one shared
// SharedFlow) — PhotoSendService now backs both photo and drawing sends,
// and without this split, ComposeMessageViewModel would flip its own UI to
// "Sent" for someone else's drawing send, and vice versa.
object PhotoSendResults {
    private val _photoResults = MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 1)
    val photoResults: SharedFlow<Result<Unit>> = _photoResults.asSharedFlow()

    private val _drawingResults = MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 1)
    val drawingResults: SharedFlow<Result<Unit>> = _drawingResults.asSharedFlow()

    suspend fun postPhoto(result: Result<Unit>) {
        _photoResults.emit(result)
    }

    suspend fun postDrawing(result: Result<Unit>) {
        _drawingResults.emit(result)
    }
}
