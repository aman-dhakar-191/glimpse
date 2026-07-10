package com.glimpse.app.data.model

// @JvmOverloads is required so the Realtime Database SDK's reflection-based
// deserializer can find a no-arg constructor — a plain Kotlin data class with
// only-defaulted params doesn't expose one on its own.
data class User @JvmOverloads constructor(
    val email: String = "",
    val displayName: String = "",
    val photoURL: String = "",
    val fcmTokens: Map<String, Boolean> = emptyMap(),
    val createdAt: Long = 0
)
