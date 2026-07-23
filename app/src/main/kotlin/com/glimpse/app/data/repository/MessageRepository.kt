package com.glimpse.app.data.repository

import android.net.Uri
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import com.glimpse.app.util.CrashLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.time.LocalTime

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
    }.onFailure { e -> CrashLogger.recordException("addReaction failed (messageId=$messageId, emoji=$emoji)", e) }

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
        // Sending implies you've read the conversation up to now — keeps
        // your last-seen-at marker (and so your partner's Sent/Seen badge
        // for their own messages, plus the widget's small "seen" mark on
        // yours) from lagging behind just because you replied without
        // separately opening History. Best-effort: FirebaseSync.markSeenUpTo
        // swallows its own errors, so a hiccup here never fails the send.
        FirebaseSync.markSeenUpTo(now)
    }.onFailure { e -> CrashLogger.recordException("sendMessage failed", e) }

    // contentType defaults to image/jpeg for callers uploading from a plain
    // file:// Uri (see PhotoSendService) — unlike a content:// Uri from the
    // picker, ContentResolver.getType() can't resolve a MIME type for those,
    // so Storage would otherwise default to application/octet-stream.
    //
    // messageType lets this same upload-then-create-message flow (and, via
    // PhotoSendService, the same app-closure-survival guarantee) serve the
    // drawing and video features too — a finished drawing is rasterized to
    // a PNG file and sent through here exactly like a photo, just tagged
    // "drawing" instead of "photo" so the widget/history can show an
    // appropriate fallback if the image fails to load; a video recording
    // is sent through here the same way, tagged "video".
    suspend fun sendPhotoMessage(
        imageUri: Uri,
        caption: String,
        unlockAt: Long = 0,
        contentType: String = "image/jpeg",
        messageType: String = "photo"
    ): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in.")
        val now = System.currentTimeMillis()

        // A video file is routinely several MB (up to MAX_VIDEO_BYTES in
        // ComposeMessageViewModel) where a photo is a few hundred KB at
        // most — NETWORK_TIMEOUT_MILLIS's 15s is fine for the latter but
        // regularly not enough time to actually finish uploading the
        // former on anything but a fast connection, cancelling the upload
        // mid-transfer and surfacing a raw "Timed out waiting for ms"
        // exception message as if the network had failed outright.
        val timeoutMillis = if (contentType == "video/mp4") VIDEO_NETWORK_TIMEOUT_MILLIS else NETWORK_TIMEOUT_MILLIS
        withTimeout(timeoutMillis) {
            // glimpse/ namespace since this Storage bucket is shared with
            // other projects on the same Firebase project. Videos live under
            // their own glimpse/videos/ prefix (not glimpse/messages/) so a
            // future cleanup job can target just that prefix for expiry
            // without touching photos, which aren't expired.
            val extension = if (contentType == "video/mp4") "mp4" else if (contentType == "image/png") "png" else "jpg"
            val basePath = if (contentType == "video/mp4") "glimpse/videos" else "glimpse/messages"
            val photoRef = storage.reference.child("$basePath/${user.uid}/$now.$extension")
            val metadata = StorageMetadata.Builder().setContentType(contentType).build()
            photoRef.putFile(imageUri, metadata).await()
            val photoUrl = photoRef.downloadUrl.await().toString()

            val message = Message(
                authorUid = user.uid,
                authorName = user.displayName.orEmpty(),
                type = messageType,
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
        FirebaseSync.markSeenUpTo(now)
    }.onFailure { e ->
        CrashLogger.recordException(
            "sendPhotoMessage failed (messageType=$messageType, contentType=$contentType, uri=$imageUri)",
            e
        )
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
        // A nudge sent late at night gets a small lasting visual in the
        // garden (a firefly caught in a jar) instead of just vanishing
        // into a one-shot notification the moment it's dismissed.
        if (isNightHere()) {
            FirebaseSync.addFirefly(user.uid)
        }
        Unit
    }.onFailure { e -> CrashLogger.recordException("sendNudge failed", e) }

    private fun isNightHere(): Boolean {
        val hour = LocalTime.now().hour
        return hour >= 20 || hour < 6
    }

    private fun isEmojiOnly(text: String): Boolean =
        text.length <= 8 && text.codePoints().noneMatch { Character.isLetterOrDigit(it) }

    companion object {
        private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000
        // 15s used to be the default here, but that's not actually enough
        // time to finish a photo upload (let alone the text/nudge writes
        // sharing this same constant) on a slow/spotty connection — not
        // just large video files, which get their own longer timeout below.
        private const val NETWORK_TIMEOUT_MILLIS = 45_000L
        private const val VIDEO_NETWORK_TIMEOUT_MILLIS = 120_000L
    }
}
