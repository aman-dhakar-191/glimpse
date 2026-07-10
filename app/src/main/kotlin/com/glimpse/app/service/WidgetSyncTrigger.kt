package com.glimpse.app.service

import android.content.Context
import android.content.Intent
import android.os.Build

object WidgetSyncTrigger {
    fun requestSync(context: Context) {
        val intent = Intent(context, WidgetUpdateService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
