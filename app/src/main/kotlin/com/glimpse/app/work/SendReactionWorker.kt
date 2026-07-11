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

// See SendMessageWorker for the reasoning — same NetworkType.CONNECTED +
// backoff-retry pattern, just for a reaction targeting a specific message.
class SendReactionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val messageId = inputData.getString(KEY_MESSAGE_ID) ?: return Result.failure()
        val emoji = inputData.getString(KEY_EMOJI) ?: return Result.failure()
        val sendResult = MessageRepository().addReaction(messageId, emoji)
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
        private const val KEY_MESSAGE_ID = "messageId"
        private const val KEY_EMOJI = "emoji"
        private const val MAX_ATTEMPTS = 3

        fun buildRequest(messageId: String, emoji: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SendReactionWorker>()
                .setInputData(workDataOf(KEY_MESSAGE_ID to messageId, KEY_EMOJI to emoji))
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
