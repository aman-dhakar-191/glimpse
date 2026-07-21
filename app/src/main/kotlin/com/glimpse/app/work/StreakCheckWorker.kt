package com.glimpse.app.work

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.glimpse.app.MainActivity
import com.glimpse.app.R
import com.glimpse.app.data.QuietHoursStore
import com.glimpse.app.data.StreakCalculator
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.notification.NotificationChannels
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

// A gentle "you two have gone quiet" nudge — checks once a day whether the
// shared conversation's latest message is older than the threshold, and if
// so posts a local notification. Deliberately simple: no per-user tracking
// of who sent last, no "only once" suppression — if it's still quiet
// tomorrow, it nudges again tomorrow, same as it would today.
//
// Also checks once a day whether the streak (same calculation StatsScreen
// shows) just crossed a milestone, and if so posts a separate celebratory
// notification — this is the only place that check can live, since the
// streak is otherwise only computed on-demand when Stats happens to be
// open, and a milestone reached while neither of you is looking at Stats
// would otherwise go uncelebrated.
class StreakCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (FirebaseAuth.getInstance().currentUser == null) return Result.success()

        val latest = FirebaseSync.fetchLatestMessageOnce()
        val quietFor = System.currentTimeMillis() - (latest?.createdAt ?: 0L)
        if (quietFor >= QUIET_THRESHOLD_MILLIS) {
            showQuietNotification()
        }

        checkStreakMilestone()
        return Result.success()
    }

    private suspend fun checkStreakMilestone() {
        val messages = FirebaseSync.fetchAllMessages()
        val streak = StreakCalculator.currentStreakDays(messages)
        if (!StreakCalculator.isMilestone(streak)) return

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotifiedMilestone = prefs.getInt(KEY_LAST_MILESTONE, 0)
        // > (not >=) — the streak can only grow one day at a time, but this
        // guards against the worker somehow running twice in one day and
        // double-notifying for the same milestone.
        if (streak <= lastNotifiedMilestone) return

        showMilestoneNotification(streak)
        prefs.edit { putInt(KEY_LAST_MILESTONE, streak) }
    }

    private fun contentPendingIntent(requestCode: Int): PendingIntent {
        val context = applicationContext
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showQuietNotification() {
        val context = applicationContext
        // The milestone badge on StatsScreen is a guaranteed, persistent
        // fallback for anyone whose milestone push happens to land during
        // their quiet hours — this "gone quiet" nudge has no such fallback,
        // but skipping one day of it is a fine tradeoff for respecting
        // quiet hours consistently with every other notification source.
        if (QuietHoursStore.isQuietNow(context)) return

        val notification = NotificationCompat.Builder(context, NotificationChannels.STREAK_REMINDER)
            .setContentTitle(context.getString(R.string.streak_notification_title))
            .setContentText(context.getString(R.string.streak_notification_body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent(0))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun showMilestoneNotification(streakDays: Int) {
        val context = applicationContext
        // See showQuietNotification's comment — the StatsScreen badge is a
        // persistent fallback if this happens to land during quiet hours.
        if (QuietHoursStore.isQuietNow(context)) return

        val notification = NotificationCompat.Builder(context, NotificationChannels.STREAK_REMINDER)
            .setContentTitle(context.getString(R.string.streak_milestone_notification_title, streakDays))
            .setContentText(context.getString(R.string.streak_milestone_notification_body, streakDays))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent(1))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(MILESTONE_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 3001
        private const val MILESTONE_NOTIFICATION_ID = 3002
        private const val UNIQUE_WORK_NAME = "streak_check"
        private const val PREFS_NAME = "streak_milestone_prefs"
        private const val KEY_LAST_MILESTONE = "last_notified_milestone"
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
