package com.glimpse.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.glimpse.app.MainActivity
import com.glimpse.app.R
import com.glimpse.app.data.firebase.FirebaseSync
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

// A gentle "you two have gone quiet" nudge — checks once a day whether the
// shared conversation's latest message is older than the threshold, and if
// so posts a local notification. Deliberately simple: no per-user tracking
// of who sent last, no "only once" suppression — if it's still quiet
// tomorrow, it nudges again tomorrow, same as it would today.
class StreakCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (FirebaseAuth.getInstance().currentUser == null) return Result.success()

        val latest = FirebaseSync.fetchLatestMessageOnce()
        val quietFor = System.currentTimeMillis() - (latest?.createdAt ?: 0L)
        if (quietFor >= QUIET_THRESHOLD_MILLIS) {
            showNotification()
        }
        return Result.success()
    }

    private fun showNotification() {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.app_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.streak_notification_title))
            .setContentText(context.getString(R.string.streak_notification_body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "streak_reminder"
        private const val NOTIFICATION_ID = 3001
        private const val UNIQUE_WORK_NAME = "streak_check"
        private val QUIET_THRESHOLD_MILLIS = TimeUnit.HOURS.toMillis(20)

        // KEEP so calling this on every launch (see MainActivity) doesn't
        // reset an already-scheduled periodic timer.
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StreakCheckWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
