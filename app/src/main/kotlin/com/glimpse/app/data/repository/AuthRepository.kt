package com.glimpse.app.data.repository

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    val currentUser: FirebaseUser? get() = auth.currentUser

    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<Unit> = runCatching {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        val authResult = auth.signInWithCredential(credential).await()
        val uid = authResult.user?.uid ?: error("Sign-in succeeded without a user")

        // TEMP: allowlist gate disabled during early testing so anyone can
        // reach the app and get their profile written to users/{uid} below
        // (visible in the Realtime Database Data tab) without needing to be
        // pre-approved first. The database rules still restrict shared/* to
        // allowedUsers regardless — this only skips the friendlier
        // client-side error, so the widget/messages will fail with a raw
        // permission-denied until the UID is actually added.
        // Re-enable before real use:
        // val isAllowed = database.child("shared/settings/allowedUsers/$uid")
        //     .get().await().getValue(Boolean::class.java) == true
        // if (!isAllowed) {
        //     auth.signOut()
        //     throw NotAllowedException()
        // }

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

    fun signOut() {
        auth.signOut()
    }
}

class NotAllowedException : Exception("This account is not authorized to use Glimpse.")
