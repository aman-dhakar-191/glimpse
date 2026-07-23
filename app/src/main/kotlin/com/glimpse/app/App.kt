package com.glimpse.app

import android.app.Application
import com.glimpse.app.notification.NotificationChannels
import com.glimpse.app.util.CrashLogger
import com.google.firebase.auth.FirebaseAuth

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.registerAll(this)
        // AuthRepository.signInWithGoogle sets this on a FRESH sign-in, but
        // most app opens are relaunches of an already-authenticated
        // session that never goes through that path — this covers those.
        FirebaseAuth.getInstance().currentUser?.uid?.let { CrashLogger.setUserId(it) }
    }
}
