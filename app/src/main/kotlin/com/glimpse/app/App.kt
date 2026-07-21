package com.glimpse.app

import android.app.Application
import com.glimpse.app.notification.NotificationChannels

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.registerAll(this)
    }
}
