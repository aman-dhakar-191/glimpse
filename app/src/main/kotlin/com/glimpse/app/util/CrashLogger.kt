package com.glimpse.app.util

import com.google.firebase.crashlytics.FirebaseCrashlytics

// Thin wrapper around FirebaseCrashlytics so every non-fatal failure path in
// the app reports the same way — a breadcrumb naming WHAT was being
// attempted (upload, download, database write, etc.) alongside the actual
// exception, instead of the Crashlytics dashboard just showing a bare stack
// trace with no context for what led to it. Fatal crashes (uncaught
// exceptions) are captured automatically by the SDK with no code needed;
// this is only for the "caught it, but something still went wrong" paths —
// e.g. a failed message/photo/video send, which otherwise only ever
// resulted in a generic on-screen error message with nothing to look up
// afterward.
object CrashLogger {
    fun log(message: String) {
        FirebaseCrashlytics.getInstance().log(message)
    }

    fun recordException(message: String, throwable: Throwable) {
        FirebaseCrashlytics.getInstance().log(message)
        FirebaseCrashlytics.getInstance().recordException(throwable)
    }

    // Tags subsequent reports with which of you hit them — the two of you
    // share this whole app, so a crash/non-fatal with no uid attached is
    // much harder to reason about than one that says whose device it was.
    fun setUserId(uid: String) {
        FirebaseCrashlytics.getInstance().setUserId(uid)
    }
}
