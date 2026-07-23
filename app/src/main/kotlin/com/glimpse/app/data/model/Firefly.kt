package com.glimpse.app.data.model

// shared/garden/fireflies/{pushId} — a small lasting visual for a nudge
// sent late at night (see MessageRepository.sendNudge), unlike
// shared/nudge itself, which is a single overwritten node with no
// history at all.
data class Firefly(val senderUid: String = "", val createdAt: Long = 0L)
