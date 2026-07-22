package com.glimpse.app.data.firebase

import android.util.Log
import com.glimpse.app.data.model.LiveStroke
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

    private fun messagesRef() = database.child("shared/messages")
    private fun lastSeenAtRef() = database.child("shared/last_seen_at")
    private fun partnerNicknameRef(uid: String) = database.child("users/$uid/settings/partnerNickname")
    private fun allowedUsersRef() = database.child("shared/settings/allowedUsers")
    private fun moodsRef() = database.child("shared/moods")
    private fun specialDateRef() = database.child("shared/specialDate")
    private fun liveDrawingStrokesRef() = database.child("shared/live_drawing/strokes")
    private fun liveDrawingPresenceRef() = database.child("shared/live_drawing/presence")

    private fun latestMessageQuery(): Query =
        messagesRef().orderByChild("createdAt").limitToLast(1)

    private fun historyQuery(limit: Int): Query =
        messagesRef().orderByChild("createdAt").limitToLast(limit)

    // Oldest-first, matching the order a chat scrollback reads in.
    private fun DataSnapshot.toMessages(): List<Message> =
        children.mapNotNull { child ->
            child.getValue(Message::class.java)?.copy(id = child.key.orEmpty())
        }.sortedBy { it.createdAt }

    // withTimeout here isn't optional the way it might look on a plain
    // suspend fun — this is called from ShapedMessageWidget.onUpdate()'s
    // goAsync()-extended coroutine, and get() with no cached value and no
    // network otherwise waits indefinitely. That leaves pendingResult.finish()
    // never called and the broadcast receiver's extended lifetime never
    // released, which is exactly what surfaces as "Glimpse isn't responding".
    suspend fun fetchLatestMessageOnce(): Message? = try {
        withTimeout(NETWORK_TIMEOUT_MILLIS) {
            latestMessageQuery().get().await().toMessages().lastOrNull()
        }
    } catch (e: Exception) {
        Log.e(TAG, "fetchLatestMessageOnce failed", e)
        null
    }

    // One-shot counterpart to listenToHistory below — used by
    // ShapedMessageWidget.onUpdate() so a freshly (re-)added widget instance
    // has the same carousel window available immediately, without waiting
    // for the live listener's first firing.
    suspend fun fetchRecentMessagesOnce(limit: Int): List<Message> = try {
        withTimeout(NETWORK_TIMEOUT_MILLIS) {
            historyQuery(limit).get().await().toMessages()
        }
    } catch (e: Exception) {
        Log.e(TAG, "fetchRecentMessagesOnce failed", e)
        emptyList()
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
        withTimeout(NETWORK_TIMEOUT_MILLIS) {
            messagesRef().get().await().toMessages()
        }
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
            // forever — this bounds it so the reaction retry worker always
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
    // the former.
    suspend fun markSeenIfNeeded(message: Message?) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (message == null || message.id.isBlank() || message.authorUid == uid) return
        markSeenUpTo(message.createdAt)
    }

    // Bumps my own last-seen-at marker forward to (at least) upToMillis —
    // never backward, via a transaction, so an out-of-order caller can never
    // undo a more recent "seen". This is the single source of truth "have I
    // seen this" is judged against everywhere it matters — History screen's
    // Sent/Seen badge, and the widget's own small "seen" mark on your sent
    // message (see ShapedWidgetRenderer) — it no longer has any bearing on
    // what the widget's carousel actually displays, which is always just
    // the latest N messages regardless of seen state.
    suspend fun markSeenUpTo(upToMillis: Long) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            withTimeout(NETWORK_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    lastSeenAtRef().child(uid).runTransaction(object : Transaction.Handler {
                        override fun doTransaction(mutableData: MutableData): Transaction.Result {
                            val current = mutableData.getValue(Long::class.java) ?: 0L
                            if (upToMillis > current) {
                                mutableData.value = upToMillis
                            }
                            return Transaction.success(mutableData)
                        }

                        override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                            if (error != null) {
                                Log.e(TAG, "markSeenUpTo failed", error.toException())
                            }
                            if (continuation.isActive) {
                                continuation.resume(Unit)
                            }
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "markSeenUpTo failed", e)
        }
    }

    // One-shot counterpart to listenToLastSeenAt below — used by the widget
    // on every render to show a "seen" mark on your own sent message once
    // your partner's marker catches up to it, without keeping a live
    // listener running.
    suspend fun fetchLastSeenAtOnce(): Map<String, Long> = try {
        withTimeout(NETWORK_TIMEOUT_MILLIS) {
            lastSeenAtRef().get().await().children.mapNotNull { child ->
                val ts = child.getValue(Long::class.java) ?: return@mapNotNull null
                (child.key ?: return@mapNotNull null) to ts
            }.toMap()
        }
    } catch (e: Exception) {
        Log.e(TAG, "fetchLastSeenAtOnce failed", e)
        emptyMap()
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
    // Also on ShapedMessageWidget's onUpdate render path (via
    // ShapedWidgetRenderer) — see fetchLatestMessageOnce's comment for why
    // this can't be left unbounded.
    suspend fun fetchPartnerNicknameOnce(): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return ""
        return try {
            withTimeout(NETWORK_TIMEOUT_MILLIS) {
                partnerNicknameRef(uid).get().await().getValue(String::class.java).orEmpty()
            }
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
            withTimeout(NETWORK_TIMEOUT_MILLIS) {
                allowedUsersRef().get().await().children.mapNotNull { it.key }.firstOrNull { it != myUid }
            }
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
            withTimeout(NETWORK_TIMEOUT_MILLIS) {
                moodsRef().child(uid).child("emoji").get().await().getValue(String::class.java).orEmpty()
            }
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
            withTimeout(NETWORK_TIMEOUT_MILLIS) {
                moodsRef().child(partnerUid).child("emoji").get().await().getValue(String::class.java).orEmpty()
            }
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
        val snapshot = withTimeout(NETWORK_TIMEOUT_MILLIS) { specialDateRef().get().await() }
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

    // The shared, joint drawing canvas — both of you can add strokes to the
    // same one at once (see DrawingViewModel), keyed by push id so two
    // strokes being drawn at the same time by different people never
    // overwrite each other the way a single flat "whole canvas" value would.

    // push() allocates a locally-generated unique key synchronously with no
    // network round trip — safe to call the instant a stroke starts, before
    // any point has actually been written yet.
    fun newLiveStrokeId(): String = liveDrawingStrokesRef().push().key ?: java.util.UUID.randomUUID().toString()

    fun listenToLiveDrawing(onStrokes: (Map<String, LiveStroke>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onStrokes(snapshot.toLiveStrokes())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenToLiveDrawing cancelled", error.toException())
            }
        }
        liveDrawingStrokesRef().addValueEventListener(listener)
        return listener
    }

    fun removeLiveDrawingListener(listener: ValueEventListener) {
        liveDrawingStrokesRef().removeEventListener(listener)
    }

    // One-shot counterpart to listenToLiveDrawing — used when finalizing a
    // Send, so the rasterized image reflects the exact strokes at that
    // moment even if this device's own listener callback for the very
    // latest point hasn't fired yet.
    suspend fun fetchLiveDrawingOnce(): Map<String, LiveStroke> = try {
        withTimeout(NETWORK_TIMEOUT_MILLIS) {
            liveDrawingStrokesRef().get().await().toLiveStrokes()
        }
    } catch (e: Exception) {
        Log.e(TAG, "fetchLiveDrawingOnce failed", e)
        emptyMap()
    }

    private fun DataSnapshot.toLiveStrokes(): Map<String, LiveStroke> =
        children.mapNotNull { child ->
            val stroke = child.getValue(LiveStroke::class.java) ?: return@mapNotNull null
            (child.key ?: return@mapNotNull null) to stroke
        }.toMap()

    // Deliberately not awaited/suspend — these fire on every added point
    // while actively dragging a finger across the canvas, so waiting on a
    // round trip per point would add visible input lag. The SDK queues and
    // sends writes in order on its own; a dropped one just means the
    // partner's view catches up on the next successful write; it's a live
    // preview, not data that needs a delivery guarantee.
    fun updateLiveStroke(strokeId: String, stroke: LiveStroke) {
        liveDrawingStrokesRef().child(strokeId).setValue(stroke)
    }

    fun removeLiveStroke(strokeId: String) {
        liveDrawingStrokesRef().child(strokeId).removeValue()
    }

    // Wipes the WHOLE shared canvas, not just the caller's own strokes —
    // both the deliberate "Clear" action and a successful Send are meant to
    // reset it for both of you at once.
    suspend fun clearLiveDrawing() {
        try {
            withTimeout(NETWORK_TIMEOUT_MILLIS) {
                liveDrawingStrokesRef().removeValue().await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "clearLiveDrawing failed", e)
        }
    }

    // Marks you as actively on the Draw screen — lets the OTHER person see
    // "your partner is drawing too" in real time. onDisconnect() clears this
    // server-side the moment the connection drops (app killed, network
    // lost, etc.), so a crash/force-close can't leave you stuck looking
    // "present" forever; a normal screen exit clears it immediately via
    // clearDrawingPresence() instead of waiting on that.
    fun markDrawingPresence() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = liveDrawingPresenceRef().child(uid)
        ref.onDisconnect().removeValue()
        ref.setValue(true)
    }

    fun clearDrawingPresence() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        liveDrawingPresenceRef().child(uid).removeValue()
    }

    // Every uid currently marked present — callers filter out their own to
    // get "is my PARTNER on the Draw screen right now".
    fun listenToDrawingPresence(onPresentUids: (Set<String>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onPresentUids(snapshot.children.mapNotNull { it.key }.toSet())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenToDrawingPresence cancelled", error.toException())
            }
        }
        liveDrawingPresenceRef().addValueEventListener(listener)
        return listener
    }

    fun removeDrawingPresenceListener(listener: ValueEventListener) {
        liveDrawingPresenceRef().removeEventListener(listener)
    }
}
