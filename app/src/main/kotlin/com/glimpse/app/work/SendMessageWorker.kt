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
import com.glimpse.app.service.WidgetSyncTrigger
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
        val sendResult = MessageRepository().sendMessage(content)
        return if (sendResult.isSuccess) {
            WidgetSyncTrigger.requestSync(applicationContext)
            Result.success()
        } else if (runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            Result.failure()
        }
    }

    companion object {
        private const val KEY_CONTENT = "content"
        private const val MAX_ATTEMPTS = 3

        fun buildRequest(content: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SendMessageWorker>()
                .setInputData(workDataOf(KEY_CONTENT to content))
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
