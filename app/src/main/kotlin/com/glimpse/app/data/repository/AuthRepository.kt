package com.glimpse.app.data.repository

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
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

        val isAllowed = database.child("shared/settings/allowedUsers/$uid")
            .get().await().getValue(Boolean::class.java) == true
        if (!isAllowed) {
            auth.signOut()
            throw NotAllowedException()
        }

        val updates = mapOf(
            "email" to account.email.orEmpty(),
            "displayName" to account.displayName.orEmpty(),
            "photoURL" to (account.photoUrl?.toString() ?: "")
        )
        database.child("users").child(uid).updateChildren(updates).await()
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
            return
        }
        registerFcmToken(token)
    }

    fun signOut() {
        auth.signOut()
    }
}

class NotAllowedException : Exception("This account is not authorized to use Glimpse.")
