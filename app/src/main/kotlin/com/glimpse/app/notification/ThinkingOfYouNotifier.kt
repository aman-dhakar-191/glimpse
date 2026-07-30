package com.glimpse.app.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.glimpse.app.MainActivity
import com.glimpse.app.R
import com.glimpse.app.data.PartnerNicknameStore
import com.glimpse.app.data.QuietHoursStore

// The one place a "thinking of you" notification gets posted, shared by the
// real push path (FCMService) and the Settings test button. Keeping them on
// a single code path is the entire point: the Morse pattern lives on the
// notification CHANNEL, not on the notification, so a test that built its
// own notification could happily buzz while the real nudge was landing on a
// stale or wrong channel. A test that doesn't exercise the thing it's
// testing is worse than no test.
object ThinkingOfYouNotifier {

    // Distinct from FCMService's message notification ID — a nudge and a
    // text message are unrelated events, and sharing one ID meant whichever
    // arrived second silently replaced the first in the shade.
    const val NOTIFICATION_ID = 2002

    // Why a post can end up invisible/silent. The real push path ignores
    // these (there's nobody watching to tell), but the test button reports
    // them — "I pressed test and felt nothing" is exactly the case where
    // knowing quiet hours swallowed it saves an hour of debugging.
    // Named Outcome, not Result: kotlin.Result is default-imported and
    // used all over this codebase for Firebase calls, so a nested type
    // shadowing it inside this object would resolve fine but read as a bug.
    enum class Outcome { POSTED, SUPPRESSED_QUIET_HOURS, NOTIFICATIONS_DISABLED }

    // The name whose Morse this device buzzes for an incoming nudge: the
    // nickname you set for your partner, falling back to whatever the
    // server told us the sender was called. See FCMService for why the
    // nickname wins.
    fun nameFor(context: Context, senderName: String): String =
        PartnerNicknameStore.load(context).ifBlank { senderName }

    fun post(context: Context, name: String, title: String, body: String): Outcome {
        // Mirrors FCMService's own quiet-hours check: this suppresses the
        // popup/sound/vibration, never the underlying content sync.
        if (QuietHoursStore.isQuietNow(context)) return Outcome.SUPPRESSED_QUIET_HOURS
        if (!notificationsAllowed(context)) return Outcome.NOTIFICATIONS_DISABLED

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // The channel carries the vibration pattern (see
        // NotificationChannels.thinkingOfYouChannelFor) — on Android O+ a
        // per-notification setVibrate() is ignored, so this call is what
        // actually decides how the nudge feels.
        val notification = NotificationCompat.Builder(context, NotificationChannels.thinkingOfYouChannelFor(context, name))
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
        return Outcome.POSTED
    }

    // POST_NOTIFICATIONS is a runtime permission on API 33+ (MainActivity
    // requests it on sign-in), and areNotificationsEnabled() additionally
    // covers the user switching Glimpse's notifications off in system
    // settings afterward — either way nothing would appear, which the test
    // button needs to say out loud rather than look like a broken buzz.
    private fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }
}
