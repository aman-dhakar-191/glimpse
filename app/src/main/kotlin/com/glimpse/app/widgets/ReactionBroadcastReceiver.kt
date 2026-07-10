package com.glimpse.app.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.glimpse.app.data.firebase.FirebaseSync

class ReactionBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ADD_REACTION) return
        val emoji = intent.getStringExtra(EXTRA_EMOJI) ?: return

        // Widget updates itself via WidgetUpdateService's live Firebase
        // listener once the write lands, so this receiver's only job is the
        // write itself.
        val pendingResult = goAsync()
        FirebaseSync.addReaction(emoji) { _ ->
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_ADD_REACTION = "com.glimpse.app.action.ADD_REACTION"
        const val EXTRA_EMOJI = "extra_emoji"
    }
}
