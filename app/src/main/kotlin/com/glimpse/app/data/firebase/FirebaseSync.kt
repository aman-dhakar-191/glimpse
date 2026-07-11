package com.glimpse.app.data.firebase

import android.util.Log
import com.glimpse.app.data.model.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

object FirebaseSync {
    private const val TAG = "FirebaseSync"
    private const val NETWORK_TIMEOUT_MILLIS = 15_000L
    private val database get() = FirebaseDatabase.getInstance().reference

    // Reusing the ordinary reactions mechanism for the "seen" receipt (rather
    // than inventing a separate marker) means it shows up as a normal
    // reaction chip on the widget for free, and merges harmlessly if the
    // other person also happens to react with the same emoji themselves.
    private const val SEEN_EMOJI = "👀" // 👀

    private fun messagesRef() = database.child("shared/messages")
    private fun lastSeenAtRef() = database.child("shared/last_seen_at")

    private fun latestMessageQuery(): Query =
        messagesRef().orderByChild("createdAt").limitToLast(1)

    private fun historyQuery(limit: Int): Query =
        messagesRef().orderByChild("createdAt").limitToLast(limit)

    // Oldest-first, matching the order a chat scrollback reads in.
    private fun DataSnapshot.toMessages(): List<Message> =
        children.mapNotNull { child ->
            child.getValue(Message::class.java)?.copy(id = child.key.orEmpty())
        }.sortedBy { it.createdAt }

    suspend fun fetchLatestMessageOnce(): Message? = try {
        latestMessageQuery().get().await().toMessages().lastOrNull()
    } catch (e: Exception) {
        Log.e(TAG, "fetchLatestMessageOnce failed", e)
        null
    }

    fun listenToLatestMessage(onMessage: (Message?) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onMessage(snapshot.toMessages().lastOrNull())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenToLatestMessage cancelled", error.toException())
            }
        }
        latestMessageQuery().addValueEventListener(listener)
        return listener
    }

    fun removeLatestMessageListener(listener: ValueEventListener) {
        latestMessageQuery().removeEventListener(listener)
    }

    fun listenToHistory(limit: Int, onMessages: (List<Message>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onMessages(snapshot.toMessages())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenToHistory cancelled", error.toException())
            }
        }
        historyQuery(limit).addValueEventListener(listener)
        return listener
    }

    fun removeHistoryListener(limit: Int, listener: ValueEventListener) {
        historyQuery(limit).removeEventListener(listener)
    }

    suspend fun addReaction(messageId: String, emoji: String): Boolean {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        if (messageId.isBlank()) return false
        val reactionsRef = messagesRef().child(messageId).child("reactions").child(emoji)

        return try {
            // A dangling transaction with no network otherwise waits
            // forever — this bounds it so a caller (the reaction retry
            // worker, or markSeenIfNeeded firing from the widget) always
            // gets an answer instead of hanging indefinitely.
            withTimeout(NETWORK_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine { continuation ->
                    reactionsRef.runTransaction(object : Transaction.Handler {
                        override fun doTransaction(mutableData: MutableData): Transaction.Result {
                            @Suppress("UNCHECKED_CAST")
                            val userIds = (mutableData.value as? List<String>).orEmpty()
                            if (userId !in userIds) {
                                mutableData.value = userIds + userId
                            }
                            return Transaction.success(mutableData)
                        }

                        override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                            if (error != null) {
                                Log.e(TAG, "addReaction failed", error.toException())
                            }
                            if (continuation.isActive) {
                                continuation.resume(error == null && committed)
                            }
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "addReaction failed", e)
            false
        }
    }

    // Best-effort: called whenever the latest message is fetched/rendered,
    // either by the app (History screen) or the widget (one-shot fetch or
    // live listener). Android gives no signal for "a human actually looked
    // at the home-screen widget", so this is a proxy for "reached their
    // screen" rather than a literal read receipt — same gap most chat apps
    // have between delivered and read, just here we can only cheaply get
    // the former. Guarded by the existing reactions dedupe so it's safe to
    // call repeatedly (e.g. every live-listener firing).
    suspend fun markSeenIfNeeded(message: Message?) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (message == null || message.id.isBlank() || message.authorUid == uid) return
        if (message.reactions[SEEN_EMOJI]?.contains(uid) == true) return

        addReaction(message.id, SEEN_EMOJI)
        try {
            lastSeenAtRef().child(uid).setValue(ServerValue.TIMESTAMP).await()
        } catch (e: Exception) {
            Log.e(TAG, "markSeenIfNeeded: last_seen_at write failed", e)
        }
    }

    // Whole map (not a single uid) so the caller can work out "did anyone
    // else see this" without needing to know the other person's uid ahead
    // of time — this is a 2-person app today, but nothing here assumes that.
    fun listenToLastSeenAt(onValues: (Map<String, Long>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val values = snapshot.children.mapNotNull { child ->
                    val ts = child.getValue(Long::class.java) ?: return@mapNotNull null
                    (child.key ?: return@mapNotNull null) to ts
                }.toMap()
                onValues(values)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenToLastSeenAt cancelled", error.toException())
            }
        }
        lastSeenAtRef().addValueEventListener(listener)
        return listener
    }

    fun removeLastSeenAtListener(listener: ValueEventListener) {
        lastSeenAtRef().removeEventListener(listener)
    }
}
