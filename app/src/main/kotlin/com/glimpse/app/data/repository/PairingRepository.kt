package com.glimpse.app.data.repository

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

data class PairingCode(val code: String, val expiresAt: Long)

// shared/settings.write is locked to false in the database rules (see
// database.rules.json) — allowedUsers is the app's whole authorization
// boundary, so no client can grant itself (or anyone else) access there
// directly. These two calls go through Cloud Functions instead, which use
// the Admin SDK to bypass the rules under carefully-checked conditions
// (see functions/index.js: createPairingCode/redeemPairingCode).
class PairingRepository {
    private val functions = FirebaseFunctions.getInstance()

    suspend fun createPairingCode(): Result<PairingCode> = runCatching {
        val result = functions.getHttpsCallable("createPairingCode").call().await()
        @Suppress("UNCHECKED_CAST")
        val data = result.data as Map<String, Any>
        PairingCode(
            code = data["code"] as String,
            expiresAt = (data["expiresAt"] as Number).toLong()
        )
    }.recoverCatching { throwable -> throw describedFailure(throwable) }

    suspend fun redeemPairingCode(code: String): Result<Unit> = runCatching {
        functions.getHttpsCallable("redeemPairingCode")
            .call(mapOf("code" to code))
            .await()
        Unit
    }.recoverCatching { throwable -> throw describedFailure(throwable) }

    // FirebaseFunctionsException carries the HttpsError message from the
    // Cloud Function (e.g. "That code has expired") in its own .message,
    // but wrapped inside a less useful outer exception by default — surface
    // that inner message directly so the UI can show it as-is.
    private fun describedFailure(throwable: Throwable): Throwable =
        if (throwable is FirebaseFunctionsException) {
            Exception(throwable.message ?: "Something went wrong. Try again.")
        } else {
            throwable
        }
}
