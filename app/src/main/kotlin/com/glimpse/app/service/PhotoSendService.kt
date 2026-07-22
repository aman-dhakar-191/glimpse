package com.glimpse.app.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.glimpse.app.R
import com.glimpse.app.data.repository.MessageRepository
import com.glimpse.app.notification.NotificationChannels
import com.glimpse.app.notification.SendingNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

// Sending a photo (or a drawing — see messageType) can't be queued/retried
// later like a text message (see ComposeMessageViewModel/DrawingViewModel —
// the ORIGINAL picker/camera Uri's read access isn't guaranteed to survive
// a long background wait), but that only applies to that original Uri.
// Callers copy/render those bytes into our own cache file before ever
// starting this service, so the actual upload has nothing left to lose by
// running in a real foreground service instead of a ViewModel-scoped
// coroutine — closing/swiping the app mid-upload no longer interrupts the
// send.
class PhotoSendService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val filePath = intent?.getStringExtra(EXTRA_FILE_PATH)
        if (filePath == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val caption = intent.getStringExtra(EXTRA_CAPTION).orEmpty()
        val unlockAt = intent.getLongExtra(EXTRA_UNLOCK_AT, 0L)
        val contentType = intent.getStringExtra(EXTRA_CONTENT_TYPE) ?: "image/jpeg"
        val messageType = intent.getStringExtra(EXTRA_MESSAGE_TYPE) ?: "photo"

        startForeground(NOTIFICATION_ID, buildNotification(messageType))

        val file = File(filePath)
        serviceScope.launch {
            val result = MessageRepository().sendPhotoMessage(Uri.fromFile(file), caption, unlockAt, contentType, messageType)
            file.delete()
            // The ongoing "sending" notification above disappears on its own
            // once this foreground service stops — this is the actual
            // outcome, so whoever wasn't watching the app still finds out
            // whether it went through.
            if (result.isSuccess) {
                WidgetSyncTrigger.requestSync(applicationContext)
                if (messageType == "drawing") {
                    SendingNotifier.showDrawingSent(applicationContext)
                } else {
                    SendingNotifier.showPhotoSent(applicationContext)
                }
            } else {
                if (messageType == "drawing") {
                    SendingNotifier.showDrawingSendFailed(applicationContext)
                } else {
                    SendingNotifier.showPhotoSendFailed(applicationContext)
                }
            }
            if (messageType == "drawing") {
                PhotoSendResults.postDrawing(result)
            } else {
                PhotoSendResults.postPhoto(result)
            }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(messageType: String): Notification =
        NotificationCompat.Builder(this, NotificationChannels.SENDING)
            .setContentTitle(getString(if (messageType == "drawing") R.string.sending_drawing_notification else R.string.sending_photo_notification))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .build()

    companion object {
        private const val NOTIFICATION_ID = 5001
        private const val EXTRA_FILE_PATH = "com.glimpse.app.EXTRA_FILE_PATH"
        private const val EXTRA_CAPTION = "com.glimpse.app.EXTRA_CAPTION"
        private const val EXTRA_UNLOCK_AT = "com.glimpse.app.EXTRA_UNLOCK_AT"
        private const val EXTRA_CONTENT_TYPE = "com.glimpse.app.EXTRA_CONTENT_TYPE"
        private const val EXTRA_MESSAGE_TYPE = "com.glimpse.app.EXTRA_MESSAGE_TYPE"

        fun start(
            context: Context,
            file: File,
            caption: String,
            unlockAt: Long,
            contentType: String,
            messageType: String = "photo"
        ) {
            val intent = Intent(context, PhotoSendService::class.java).apply {
                putExtra(EXTRA_FILE_PATH, file.absolutePath)
                putExtra(EXTRA_CAPTION, caption)
                putExtra(EXTRA_UNLOCK_AT, unlockAt)
                putExtra(EXTRA_CONTENT_TYPE, contentType)
                putExtra(EXTRA_MESSAGE_TYPE, messageType)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
