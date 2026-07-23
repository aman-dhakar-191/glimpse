package com.glimpse.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.glimpse.app.data.repository.MessageRepository
import com.glimpse.app.notification.SendingNotifier
import com.glimpse.app.service.WidgetSyncTrigger
import com.glimpse.app.util.CrashLogger
import java.util.concurrent.TimeUnit

// A NetworkType.CONNECTED constraint means this only ever starts once the
// device actually has a connection — that's what turns "compose a message
// with no signal" from an indefinite spinner into "queued, sends once
// you're back online" instead. If it does run and still fails (a real
// error, not just no network, since the constraint already guaranteed
// connectivity), a few retries with backoff cover transient blips before
// giving up.
class SendMessageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val content = inputData.getString(KEY_CONTENT) ?: return Result.failure()
        val unlockAt = inputData.getLong(KEY_UNLOCK_AT, 0)

        // Left up across retries (each doWork() call re-shows the same
        // content, no visible flicker) — only cancelled once this is truly
        // done, one way or the other.
        SendingNotifier.showSendingMessage(applicationContext)

        val sendResult = MessageRepository().sendMessage(content, unlockAt)
        return if (sendResult.isSuccess) {
            SendingNotifier.cancel(applicationContext)
            WidgetSyncTrigger.requestSync(applicationContext)
            Result.success()
        } else if (runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            // sendMessage() already recorded the underlying exception itself
            // — this breadcrumb marks the distinct "gave up after retries"
            // fault, which MessageRepository has no visibility into on its
            // own (it doesn't know this is attempt N of MAX_ATTEMPTS).
            CrashLogger.log("SendMessageWorker: giving up after $runAttemptCount attempts")
            SendingNotifier.cancel(applicationContext)
            Result.failure()
        }
    }

    companion object {
        private const val KEY_CONTENT = "content"
        private const val KEY_UNLOCK_AT = "unlock_at"
        private const val MAX_ATTEMPTS = 3

        fun buildRequest(content: String, unlockAt: Long = 0): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SendMessageWorker>()
                .setInputData(workDataOf(KEY_CONTENT to content, KEY_UNLOCK_AT to unlockAt))
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
    }
}
