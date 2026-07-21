package com.glimpse.app.work

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.glimpse.app.MainActivity
import com.glimpse.app.R
import com.glimpse.app.data.QuietHoursStore
import com.glimpse.app.data.update.UpdateChecker
import com.glimpse.app.notification.NotificationChannels
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

// Runs the same UpdateChecker.checkForUpdate() call MainActivity fires on
// sign-in, but on a periodic background schedule so a new release still
// gets surfaced to someone who hasn't reopened the app. Notifies once per
// release (tracked by versionCode) rather than every run, since checkForUpdate
// keeps returning the same still-not-installed release every day until the
// user actually updates.
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (FirebaseAuth.getInstance().currentUser == null) return Result.success()

        val info = UpdateChecker.checkForUpdate() ?: return Result.success()

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotifiedVersionCode = prefs.getInt(KEY_LAST_NOTIFIED_VERSION_CODE, 0)
        if (info.versionCode <= lastNotifiedVersionCode) return Result.success()

        showUpdateAvailableNotification(info.versionName)
        prefs.edit { putInt(KEY_LAST_NOTIFIED_VERSION_CODE, info.versionCode) }
        return Result.success()
    }

    private fun showUpdateAvailableNotification(versionName: String) {
        val context = applicationContext
        if (QuietHoursStore.isQuietNow(context)) return

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_UPDATE, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.UPDATE_AVAILABLE)
            .setContentTitle(context.getString(R.string.update_notification_title))
            .setContentText(context.getString(R.string.update_notification_body, versionName))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 4001
        private const val UNIQUE_WORK_NAME = "update_check"
        private const val PREFS_NAME = "update_check_prefs"
        private const val KEY_LAST_NOTIFIED_VERSION_CODE = "last_notified_version_code"

        // KEEP so calling this on every launch (see MainActivity) doesn't
        // reset an already-scheduled periodic timer. This alone covers
        // "haven't opened the app in a while" but NOT "just opened the
        // app" — a PeriodicWorkRequest's first run isn't immediate, it can
        // be delayed by WorkManager for hours, so checkNow() below is what
        // actually makes opening the app surface a notification promptly.
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        // One-off immediate run of the same check-and-notify logic, so
        // opening the app (see MainActivity.onSignedIn) gets a prompt
        // notification instead of waiting on the periodic schedule.
        fun checkNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<UpdateCheckWorker>().build())
        }
    }
}
