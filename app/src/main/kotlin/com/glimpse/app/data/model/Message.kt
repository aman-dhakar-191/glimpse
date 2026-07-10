package com.glimpse.app.data.model

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
    val expiresAt: Long = 0
)
