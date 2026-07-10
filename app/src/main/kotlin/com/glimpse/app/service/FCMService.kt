package com.glimpse.app.service

import com.glimpse.app.data.repository.AuthRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        AuthRepository().registerFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // The Current Message widget refreshes itself via
        // WidgetUpdateService's live Firebase listener; this just makes sure
        // that listener is running when a push arrives while the app/widget
        // service isn't already active.
        WidgetSyncTrigger.requestSync(this)
    }
}
