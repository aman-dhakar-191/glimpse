package com.glimpse.app.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.glimpse.app.R
import com.glimpse.app.data.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// The 1x1 widget's whole reason to exist: send a "thinking of you" tap
// straight from the home screen — same fire-and-forget path
// ComposeMessageScreen's own button uses (MessageRepository.sendNudge).
// The receiving device's distinct notification channel/vibration (see
// NotificationChannels.THINKING_OF_YOU) is what actually signals the send
// on their end; the Toast here is just local "yes, that worked" feedback
// since a static 1x1 icon has no other way to show it.
class ThinkingOfYouSendReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            try {
                val result = MessageRepository().sendNudge()
                val messageRes = if (result.isSuccess) {
                    R.string.thinking_of_you_widget_sent
                } else {
                    R.string.thinking_of_you_widget_failed
                }
                Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
