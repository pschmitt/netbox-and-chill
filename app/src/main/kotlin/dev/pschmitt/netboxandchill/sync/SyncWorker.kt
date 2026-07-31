package dev.pschmitt.netboxandchill.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.pschmitt.netboxandchill.data.repository.DeviceRepository
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository

@HiltWorker
class SyncWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val deviceRepository: DeviceRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!settingsRepository.isConfigured) return Result.success()
        return deviceRepository
            .syncAll()
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
