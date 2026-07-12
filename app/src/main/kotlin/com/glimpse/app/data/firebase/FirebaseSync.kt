package com.glimpse.app.data.firebase

import android.util.Log
import com.glimpse.app.data.model.Message
import com.glimpse.app.data.model.SpecialDate
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
    // Internal (not private) so StatsViewModel can exclude it when computing
    // "most used reaction" — it's an automatic marker, not a deliberate one.
    internal const val SEEN_EMOJI = "👀" // 👀

    private fun messagesRef() = database.child("shared/messages")
    private fun lastSeenAtRef() = database.child("shared/last_seen_at")
    private fun partnerNicknameRef(uid: String) = database.child("users/$uid/settings/partnerNickname")
    private fun allowedUsersRef() = database.child("shared/settings/allowedUsers")
    private fun moodsRef() = database.child("shared/moods")
    private fun specialDateRef() = database.child("shared/specialDate")

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

    // Unbounded, one-shot — used by the stats screen, which needs the whole
    // conversation to count totals rather than just the last N messages
    // History shows. Fine for a personal 2-person app's message volume.
    suspend fun fetchAllMessages(): List<Message> = try {
        messagesRef().get().await().toMessages()
    } catch (e: Exception) {
        Log.e(TAG, "fetchAllMessages failed", e)
        emptyList()
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

    // "What I call my partner" — purely local to the signed-in user: stored
    // under their own users/{uid} node (already owner-only per
    // database.rules.json, so no rules change needed) and only ever read by
    // that same person's own client. The partner's copy of the app never
    // sees or is affected by this value; each side can set their own.
    suspend fun fetchPartnerNicknameOnce(): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return ""
        return try {
            partnerNicknameRef(uid).get().await().getValue(String::class.java).orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "fetchPartnerNicknameOnce failed", e)
            ""
        }
    }

    suspend fun setPartnerNickname(nickname: String): Result<Unit> = runCatching {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Not signed in.")
        withTimeout(NETWORK_TIMEOUT_MILLIS) {
            partnerNicknameRef(uid).setValue(nickname.trim()).await()
        }
    }

    // Mood is shared (unlike the nickname above, which is purely local) —
    // the whole point is for your partner to see it, so it lives under
    // shared/ rather than users/{myUid}/, matching the same $resource
    // catch-all read/write rule messages and nudges already use.
    //
    // shared/settings/allowedUsers is the app's whole 2-person authorization
    // boundary (see database.rules.json) — "the other uid in there" is the
    // same "everyone except me" logic the onNewMessage/onNewReaction Cloud
    // Functions use server-side, just done client-side here since we only
    // ever need the one partner uid, not a token fan-out.
    private suspend fun fetchPartnerUidOnce(): String? {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        return try {
            allowedUsersRef().get().await().children.mapNotNull { it.key }.firstOrNull { it != myUid }
        } catch (e: Exception) {
            Log.e(TAG, "fetchPartnerUidOnce failed", e)
            null
        }
    }

    suspend fun setMood(emoji: String): Result<Unit> = runCatching {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Not signed in.")
        val mood = mapOf("emoji" to emoji, "updatedAt" to ServerValue.TIMESTAMP)
        withTimeout(NETWORK_TIMEOUT_MILLIS) {
            moodsRef().child(uid).setValue(mood).await()
        }
    }

    suspend fun fetchMyMoodOnce(): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return ""
        return try {
            moodsRef().child(uid).child("emoji").get().await().getValue(String::class.java).orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "fetchMyMoodOnce failed", e)
            ""
        }
    }

    // Used by the widget renderer to show "how they're doing" without
    // either of you needing to open the app.
    suspend fun fetchPartnerMoodOnce(): String {
        val partnerUid = fetchPartnerUidOnce() ?: return ""
        return try {
            moodsRef().child(partnerUid).child("emoji").get().await().getValue(String::class.java).orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "fetchPartnerMoodOnce failed", e)
            ""
        }
    }

    // Shared — either of you can set/see it, same reasoning as mood above.
    // Either person setting this changes it for both, which is the right
    // behavior for a single shared anniversary/birthday countdown.
    suspend fun setSpecialDate(label: String, month: Int, day: Int): Result<Unit> = runCatching {
        val data = mapOf("label" to label.trim(), "month" to month, "day" to day)
        withTimeout(NETWORK_TIMEOUT_MILLIS) {
            specialDateRef().setValue(data).await()
        }
    }

    suspend fun fetchSpecialDateOnce(): SpecialDate? = try {
        val snapshot = specialDateRef().get().await()
        val label = snapshot.child("label").getValue(String::class.java)
        val month = snapshot.child("month").getValue(Int::class.java)
        val day = snapshot.child("day").getValue(Int::class.java)
        if (label.isNullOrBlank() || month == null || day == null) {
            null
        } else {
            SpecialDate(label, month, day)
        }
    } catch (e: Exception) {
        Log.e(TAG, "fetchSpecialDateOnce failed", e)
        null
    }

    suspend fun clearSpecialDate(): Result<Unit> = runCatching {
        withTimeout(NETWORK_TIMEOUT_MILLIS) {
            specialDateRef().removeValue().await()
        }
    }
}
