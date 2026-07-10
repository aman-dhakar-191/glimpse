package com.glimpse.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import coil.ImageLoader
import coil.request.ImageRequest
import com.glimpse.app.R
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.Message
import com.glimpse.app.widgets.CurrentMessageWidget
import com.glimpse.app.widgets.ReactionActionBinder
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WidgetUpdateService : Service() {

    private var listener: ValueEventListener? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val imageLoader by lazy { ImageLoader.Builder(applicationContext).build() }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always tear down and re-attach: if the previous listener was
        // dropped by Firebase (e.g. a permission-denied error, which the SDK
        // treats as terminal and never retries), `listener` here would still
        // be a non-null reference to a dead listener, so a null-check guard
        // would permanently skip re-attaching even after the underlying
        // problem (like a missing allowedUsers entry) is fixed.
        listener?.let { FirebaseSync.removeCurrentMessageListener(it) }
        listener = FirebaseSync.listenToCurrentMessage(::updateWidgets)
        return START_STICKY
    }

    override fun onDestroy() {
        listener?.let { FirebaseSync.removeCurrentMessageListener(it) }
        listener = null
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateWidgets(message: Message?) {
        serviceScope.launch {
            val appWidgetManager = AppWidgetManager.getInstance(this@WidgetUpdateService)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(this@WidgetUpdateService, CurrentMessageWidget::class.java)
            )
            if (appWidgetIds.isEmpty()) return@launch

            // RemoteViews.setImageViewUri() rejects arbitrary https:// URLs on
            // Android 12+ (SecurityException: "Disallowed URI ... in
            // RemoteViews") — widgets can only be handed real pixel data, not
            // a URI for the host to fetch itself. So we download it here and
            // hand over a Bitmap instead.
            val photoBitmap = if (message?.type == "photo" && message.photoUrl.isNotBlank()) {
                loadBitmap(message.photoUrl)
            } else {
                null
            }

            appWidgetIds.forEach { appWidgetId ->
                val remoteViews = RemoteViews(packageName, R.layout.widget_current_message)
                ReactionActionBinder.bindReactionButtons(this@WidgetUpdateService, remoteViews, appWidgetId)
                ReactionActionBinder.bindOpenComposeAction(this@WidgetUpdateService, remoteViews, appWidgetId)
                renderMessage(remoteViews, message, photoBitmap)
                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            }
        }
    }

    private suspend fun loadBitmap(url: String): Bitmap? {
        return try {
            val request = ImageRequest.Builder(applicationContext)
                .data(url)
                .allowHardware(false)
                .build()
            val bitmap = (imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
                ?: return null
            // Keep the RemoteViews Parcel well under the binder transaction
            // size limit — a full-resolution photo would blow past it.
            val maxDimension = 480
            if (bitmap.width <= maxDimension && bitmap.height <= maxDimension) {
                bitmap
            } else {
                val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun renderMessage(remoteViews: RemoteViews, message: Message?, photoBitmap: Bitmap?) {
        if (message == null) {
            remoteViews.setTextViewText(R.id.author_name, "")
            remoteViews.setTextViewText(R.id.message_content, getString(R.string.widget_no_message))
            remoteViews.setViewVisibility(R.id.message_photo, View.GONE)
            remoteViews.setViewVisibility(R.id.photo_caption, View.GONE)
            remoteViews.removeAllViews(R.id.reactions_container)
            return
        }

        remoteViews.setTextViewText(R.id.author_name, message.authorName)
        remoteViews.setTextViewText(R.id.message_content, message.content)

        if (message.type == "photo") {
            if (photoBitmap != null) {
                remoteViews.setImageViewBitmap(R.id.message_photo, photoBitmap)
                remoteViews.setViewVisibility(R.id.message_photo, View.VISIBLE)
            } else {
                remoteViews.setViewVisibility(R.id.message_photo, View.GONE)
            }
            remoteViews.setTextViewText(R.id.photo_caption, message.caption)
            remoteViews.setViewVisibility(
                R.id.photo_caption,
                if (message.caption.isNotBlank()) View.VISIBLE else View.GONE
            )
        } else {
            remoteViews.setViewVisibility(R.id.message_photo, View.GONE)
            remoteViews.setViewVisibility(R.id.photo_caption, View.GONE)
        }

        remoteViews.removeAllViews(R.id.reactions_container)
        message.reactions.filterValues { it.isNotEmpty() }.forEach { (emoji, userIds) ->
            val chip = RemoteViews(packageName, R.layout.reaction_chip)
            chip.setTextViewText(R.id.reaction_text, "$emoji ${userIds.size}")
            remoteViews.addView(R.id.reactions_container, chip)
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "widget_sync"
        private const val NOTIFICATION_ID = 1001
    }
}
