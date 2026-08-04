package dev.pschmitt.nyetbox.data.backup

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.pschmitt.nyetbox.data.repository.SettingsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupScheduler
@Inject
constructor(
    private val workManager: WorkManager,
    private val settingsRepository: SettingsRepository,
) {
    fun schedule() {
        if (
            !settingsRepository.scheduledBackupEnabled.value ||
                settingsRepository.scheduledBackupFolderUri.value.isNullOrBlank()
        ) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request =
            PeriodicWorkRequestBuilder<SettingsBackupWorker>(
                    settingsRepository.scheduledBackupFrequency.value.intervalDays,
                    TimeUnit.DAYS,
                )
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "nyetbox-scheduled-settings-backup"
    }
}
