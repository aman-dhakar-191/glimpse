package com.glimpse.app.data.repository

import android.net.Uri
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

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

    suspend fun sendMessage(content: String, unlockAt: Long = 0): Result<Unit> = runCatching {
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
            expiresAt = now + THIRTY_DAYS_MILLIS,
            unlockAt = unlockAt
        )
        // A new push key per message (instead of overwriting one shared
        // node) is what makes a scrollable history possible — each message
        // keeps its own reactions rather than a new one wiping out the last.
        //
        // Firebase's own Task never times out on its own — with no network
        // it just sits pending forever (the SDK queues the write and waits
        // for a connection), which without this would leave the UI stuck on
        // its spinner indefinitely instead of failing visibly. Callers here
        // (SendMessageWorker) run behind a NetworkType.CONNECTED constraint,
        // so this timeout is really a safety net for connectivity dropping
        // mid-write, not the primary offline handling.
        withTimeout(NETWORK_TIMEOUT_MILLIS) {
            database.child("shared/messages").push().setValue(message).await()
        }
    }

    suspend fun sendPhotoMessage(imageUri: Uri, caption: String, unlockAt: Long = 0): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in.")
        val now = System.currentTimeMillis()

        withTimeout(NETWORK_TIMEOUT_MILLIS) {
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
                expiresAt = now + THIRTY_DAYS_MILLIS,
                unlockAt = unlockAt
            )
            database.child("shared/messages").push().setValue(message).await()
        }
    }

    // A single overwritten node (not a growing list like messages) — a
    // nudge is a fire-and-forget ping, not something either of you needs a
    // history of. createdAt is ServerValue.TIMESTAMP on every send (not a
    // client-side value) specifically so the node's value always genuinely
    // changes even on back-to-back nudges — Firebase's onWrite Cloud
    // Function trigger only fires on an actual value change, so a nudge
    // sent twice in a row with the same payload would otherwise silently
    // no-op the second time.
    suspend fun sendNudge(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in.")
        val nudge = mapOf(
            "senderUid" to user.uid,
            "createdAt" to ServerValue.TIMESTAMP
        )
        withTimeout(NETWORK_TIMEOUT_MILLIS) {
            database.child("shared/nudge").setValue(nudge).await()
        }
    }

    private fun isEmojiOnly(text: String): Boolean =
        text.length <= 8 && text.codePoints().noneMatch { Character.isLetterOrDigit(it) }

    companion object {
        private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000
        private const val NETWORK_TIMEOUT_MILLIS = 15_000L
    }
}
