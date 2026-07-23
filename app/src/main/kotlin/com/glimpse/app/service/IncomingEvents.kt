package com.glimpse.app.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Lets ComposeMessageViewModel show a live in-app burst the moment a push
// arrives while the app happens to be open, without FCMService needing to
// know whether anything is listening. No replay (see PhotoSendResults for
// the same reasoning): if nothing was collecting when this posted, the app
// wasn't open to show a live burst for anyway — the system notification
// (with its own distinct channel/vibration) already covered that case.
object IncomingEvents {
    private val _thinkingOfYou = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val thinkingOfYou: SharedFlow<Unit> = _thinkingOfYou.asSharedFlow()

    private val _reactions = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val reactions: SharedFlow<String> = _reactions.asSharedFlow()

    fun postThinkingOfYou() {
        _thinkingOfYou.tryEmit(Unit)
    }

    fun postReaction(emoji: String) {
        _reactions.tryEmit(emoji)
    }
}
