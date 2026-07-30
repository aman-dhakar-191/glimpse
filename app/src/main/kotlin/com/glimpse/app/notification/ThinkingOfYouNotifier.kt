package com.glimpse.app.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.glimpse.app.MainActivity
import com.glimpse.app.R
import com.glimpse.app.data.PartnerNicknameStore
import com.glimpse.app.data.QuietHoursStore

// The one place a "thinking of you" notification gets posted, shared by the
// real push path (FCMService) and the Settings test button. Keeping them on
// a single code path is the entire point: posting the notification and
// playing the Morse are two steps that have to stay in lockstep, and a test
// button that reimplemented either could pass while real nudges were
// broken. A test that doesn't exercise the thing it's testing is worse than
// no test.
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
    enum class Outcome {
        POSTED,
        // Notification shown, buzz deliberately skipped — the phone is in
        // silent mode or Do Not Disturb, the two states where buzzing
        // anyway would be the app overruling an explicit "leave me alone".
        POSTED_WITHOUT_VIBRATION,
        SUPPRESSED_QUIET_HOURS,
        NOTIFICATIONS_DISABLED
    }

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

        // The channel is the visual/sound half only; it's created with
        // vibration off and the buzz is played explicitly below. See
        // NotificationChannels.thinkingOfYouChannelFor for why.
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

        // The buzz is driven here rather than by the channel — see
        // NotificationChannels.thinkingOfYouChannelFor for why the channel
        // route silently lost on a real device. The cost of taking it over
        // is that the two "the user asked for quiet" states the channel
        // used to honor for free now have to be honored by hand, directly
        // below.
        if (!shouldVibrate(context)) return Outcome.POSTED_WITHOUT_VIBRATION
        MorseVibration.play(context, name)
        return Outcome.POSTED
    }

    // Silent mode is an explicit "not now" and a love note doesn't get to
    // overrule it. Vibrate ringer mode emphatically does NOT belong here —
    // that one means "buzz me, don't ring me", which is exactly this
    // feature's job.
    //
    // Do Not Disturb is deliberately only honored at its strictest setting.
    // The looser filters let allowed apps through, and whether Glimpse is
    // on that allowlist is already the user's decision, enforced by the
    // system on the notification itself. Treating any DND at all as "stay
    // still" would mute the buzz on a nudge whose sound the system just
    // permitted — which is precisely the inconsistency that made this
    // feature look broken in the first place.
    private fun shouldVibrate(context: Context): Boolean {
        val audioManager = context.getSystemService(AudioManager::class.java)
        if (audioManager?.ringerMode == AudioManager.RINGER_MODE_SILENT) return false

        return context.getSystemService(NotificationManager::class.java)
            .currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_NONE
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
