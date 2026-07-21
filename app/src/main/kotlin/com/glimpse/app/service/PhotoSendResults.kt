package com.glimpse.app.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Lets ComposeMessageViewModel show live Sending/Sent/Error feedback while
// the app is open, without PhotoSendService needing to know whether
// anything is listening — the actual send (backed by startForeground, so
// it survives the app closing) doesn't depend on this at all. No replay:
// if nothing was collecting when a result posted (the app was closed), the
// message is already in Firebase by the time anyone reopens the app, so
// there's nothing to retroactively show.
object PhotoSendResults {
    private val _results = MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 1)
    val results: SharedFlow<Result<Unit>> = _results.asSharedFlow()

    suspend fun post(result: Result<Unit>) {
        _results.emit(result)
    }
}
