package com.glimpse.app.data.repository

import android.net.Uri
import com.glimpse.app.data.model.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class MessageRepository {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance()

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
        // setValue (not updateChildren) replaces the whole node, which is what
        // we want for a brand new message — it clears out reactions left over
        // from whatever the previous message was.
        database.child("shared/current_message").setValue(message).await()
    }

    suspend fun sendPhotoMessage(imageUri: Uri, caption: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in.")
        val now = System.currentTimeMillis()

        val photoRef = storage.reference.child("messages/${user.uid}/$now.jpg")
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
        database.child("shared/current_message").setValue(message).await()
    }

    private fun isEmojiOnly(text: String): Boolean =
        text.length <= 8 && text.codePoints().noneMatch { Character.isLetterOrDigit(it) }

    companion object {
        private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
