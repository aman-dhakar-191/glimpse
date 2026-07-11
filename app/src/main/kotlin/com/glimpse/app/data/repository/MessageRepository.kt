package com.glimpse.app.data.repository

import android.net.Uri
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class MessageRepository {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance()

    suspend fun addReaction(messageId: String, emoji: String): Result<Unit> = runCatching {
        val trimmed = emoji.trim()
        require(trimmed.isNotEmpty()) { "Pick an emoji first." }
        require(messageId.isNotEmpty()) { "No message to react to yet." }
        val success = FirebaseSync.addReaction(messageId, trimmed)
        if (!success) error("Failed to send reaction.")
    }

    suspend fun sendMessage(content: String): Result<Unit> = runCatching {
        val trimmed = content.trim()
        require(trimmed.isNotEmpty()) { "Message can't be empty." }
        val user = auth.currentUser ?: error("Not signed in.")

        val now = System.currentTimeMillis()
        val message = Message(
            authorUid = user.uid,
            authorName = user.displayName.orEmpty(),
            type = if (isEmojiOnly(trimmed)) "emoji" else "text",
            content = trimmed,
            createdAt = now,
            updatedAt = now,
            expiresAt = now + THIRTY_DAYS_MILLIS
        )
        // A new push key per message (instead of overwriting one shared
        // node) is what makes a scrollable history possible — each message
        // keeps its own reactions rather than a new one wiping out the last.
        database.child("shared/messages").push().setValue(message).await()
    }

    suspend fun sendPhotoMessage(imageUri: Uri, caption: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in.")
        val now = System.currentTimeMillis()

        // glimpse/ namespace since this Storage bucket is shared with other
        // projects on the same Firebase project.
        val photoRef = storage.reference.child("glimpse/messages/${user.uid}/$now.jpg")
        photoRef.putFile(imageUri).await()
        val photoUrl = photoRef.downloadUrl.await().toString()

        val message = Message(
            authorUid = user.uid,
            authorName = user.displayName.orEmpty(),
            type = "photo",
            content = "",
            photoUrl = photoUrl,
            caption = caption.trim(),
            createdAt = now,
            updatedAt = now,
            expiresAt = now + THIRTY_DAYS_MILLIS
        )
        database.child("shared/messages").push().setValue(message).await()
    }

    private fun isEmojiOnly(text: String): Boolean =
        text.length <= 8 && text.codePoints().noneMatch { Character.isLetterOrDigit(it) }

    companion object {
        private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
