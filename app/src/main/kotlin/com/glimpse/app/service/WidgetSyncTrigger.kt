package com.glimpse.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object WidgetSyncTrigger {
    private const val TAG = "WidgetSyncTrigger"

    fun requestSync(context: Context) {
        val intent = Intent(context, WidgetUpdateService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: IllegalStateException) {
            // Android 12+ restricts starting a foreground service from a
            // background/receiver context — e.g. ShapedMessageWidget's
            // onEnabled()/onUpdate() firing right after the widget is
            // (re-)added, before the app has any foreground exemption.
            // Throws ForegroundServiceStartNotAllowedException (a subclass
            // of this, only present on API 31+, so catching the broader
            // IllegalStateException avoids referencing a class that
            // doesn't exist on older devices). Not fatal: requestSync()
            // also runs from genuinely foregrounded contexts (sending a
            // message/reaction, receiving a push) whenever the app is
            // actually open, which will succeed and populate the widget.
            Log.w(TAG, "startForegroundService not allowed right now", e)
        }
    }
}
