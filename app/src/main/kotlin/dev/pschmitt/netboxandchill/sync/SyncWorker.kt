package dev.pschmitt.netboxandchill.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository

@HiltWorker
class SyncWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val offlineSyncRepository: OfflineSyncRepository,
    private val settingsRepository: SettingsRepository,
    private val syncNotifier: SyncNotifier,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!settingsRepository.isConfigured) return Result.success()
        // Full syncs include every discovered model plus optional durable attachments and can
        // legitimately run for minutes. Promote the WorkManager job before touching the network
        // so Android keeps it alive and the user gets the real system progress notification.
        setForeground(syncNotifier.foregroundInfo())
        return offlineSyncRepository
            .syncAll(onProgress = syncNotifier::notifySyncProgress)
            .fold(
                onSuccess = {
                    syncNotifier.notifySyncSucceeded()
                    Result.success()
                },
                onFailure = { error ->
                    // WorkManager's own exponential backoff handles the retry delay - this just
                    // caps how many times a single scheduled run retries before giving up and
                    // surfacing a Notification (NBC-23), rather than retrying silently forever.
                    // A PeriodicWorkRequest's attempt count resets on its next period regardless,
                    // so this only bounds retries *within* one run, not across runs.
                    if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                        syncNotifier.notifySyncRetry(runAttemptCount + 1)
                        Result.retry()
                    } else {
                        syncNotifier.notifySyncFailed(error.message)
                        Result.failure()
                    }
                },
            )
    }

    private companion object {
        const val MAX_RETRY_ATTEMPTS = 3
    }
}
