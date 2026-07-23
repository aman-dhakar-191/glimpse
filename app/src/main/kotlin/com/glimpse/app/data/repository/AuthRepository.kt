package com.glimpse.app.data.repository

import com.glimpse.app.util.CrashLogger
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    val currentUser: FirebaseUser? get() = auth.currentUser

    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<Unit> = runCatching {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        val authResult = auth.signInWithCredential(credential).await()
        val uid = authResult.user?.uid ?: error("Sign-in succeeded without a user")
        // Tags every subsequent CrashLogger report (this session, on this
        // device) with who it was, before anything else has a chance to fail.
        CrashLogger.setUserId(uid)

        // Unlike before, not being allowed yet no longer signs the user back
        // out — PairingRepository.redeemPairingCode needs a live Firebase
        // Auth session to call as, so staying signed in (just stuck on the
        // "enter an invite code" screen) is what makes that possible.
        if (!isAllowed(uid)) throw NeedsPairingException()

        val updates = mapOf(
            "email" to account.email.orEmpty(),
            "displayName" to account.displayName.orEmpty(),
            "photoURL" to (account.photoUrl?.toString() ?: "")
        )
        database.child("users").child(uid).updateChildren(updates).await()
    }.onFailure { e ->
        if (e !is NeedsPairingException) CrashLogger.recordException("signInWithGoogle failed", e)
    }

    // Re-checked on every launch of an already-signed-in session, not just
    // right after a fresh Google sign-in — otherwise neither "redeemed a
    // code since you last opened the app" nor "still hasn't" would ever be
    // noticed.
    suspend fun checkPairingStatus(): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: error("Not signed in.")
        if (!isAllowed(uid)) throw NeedsPairingException()
    }

    // shared/settings' rules deny read entirely to a uid not already in
    // allowedUsers (see database.rules.json) — that shows up as a
    // DatabaseException here, not a clean "false" value, so both cases are
    // treated as "not allowed" rather than letting the permission error
    // surface as a generic sign-in failure.
    private suspend fun isAllowed(uid: String): Boolean = try {
        database.child("shared/settings/allowedUsers/$uid").get().await()
            .getValue(Boolean::class.java) == true
    } catch (e: DatabaseException) {
        false
    }

    fun registerFcmToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        database.child("users/$uid/fcmTokens/$token").setValue(true)
    }

    // FCMService.onNewToken() only fires when the token is first generated or
    // rotated — usually at install time, before the user has signed in, so
    // registerFcmToken() silently no-ops there and never gets another chance
    // (the token itself doesn't change just because you log in later). This
    // registers whatever token currently exists, so every sign-in — first
    // time or relaunch of an already-signed-in session — guarantees it's
    // actually saved. setValue with the same token is a harmless no-op if
    // it's already registered.
    suspend fun ensureFcmTokenRegistered() {
        val token = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            CrashLogger.recordException("ensureFcmTokenRegistered: couldn't fetch FCM token", e)
            return
        }
        registerFcmToken(token)
    }

    fun signOut() {
        auth.signOut()
    }
}

class NeedsPairingException : Exception("This account isn't paired with Glimpse yet.")
