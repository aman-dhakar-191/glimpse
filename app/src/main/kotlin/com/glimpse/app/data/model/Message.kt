package com.glimpse.app.data.model

import com.google.firebase.database.Exclude

data class Message @JvmOverloads constructor(
    val authorUid: String = "",
    val authorName: String = "",
    val type: String = "text",
    val content: String = "",
    val photoUrl: String = "",
    val caption: String = "",
    val reactions: Map<String, List<String>> = emptyMap(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val expiresAt: Long = 0,
    // The Firebase push key of this message under shared/messages — not
    // stored as a value field itself (Exclude keeps it out of setValue()
    // writes), populated by the caller from the DataSnapshot's key.
    @get:Exclude val id: String = ""
)
