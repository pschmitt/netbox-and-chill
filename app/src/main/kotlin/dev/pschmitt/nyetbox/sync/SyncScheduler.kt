package dev.pschmitt.nyetbox.sync

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.pschmitt.nyetbox.data.repository.SettingsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler
@Inject
constructor(
    private val workManager: WorkManager,
    private val settingsRepository: SettingsRepository,
) {

    fun schedulePeriodic() {
        val request =
            PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(syncConstraints())
                .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun syncNow() {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(syncConstraints()).build()
        workManager.enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /** Queues the first refresh without making app startup wait for network or disk work. */
    fun scheduleStartup() {
        if (
            !shouldScheduleStartup(
                syncOnAppLaunch = settingsRepository.syncOnAppLaunch.value,
                offlineMode = settingsRepository.offlineMode.value,
            )
        ) {
            return
        }
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(syncConstraints()).build()
        workManager.enqueueUniqueWork(STARTUP_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancelStartup() {
        workManager.cancelUniqueWork(STARTUP_WORK_NAME)
    }

    /** Stops work that could otherwise continue writing into the profile being left. */
    fun cancelForServerSwitch() {
        workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
        workManager.cancelUniqueWork(STARTUP_WORK_NAME)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    private fun syncConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(
                SyncNetworkPolicy.requiredNetworkType(
                    syncOnlyOnWifi = settingsRepository.syncOnlyOnWifi.value,
                    syncWhileRoaming = settingsRepository.syncWhileRoaming.value,
                )
            )
            // Battery Saver is stricter than Android's low-battery threshold and is checked by
            // SyncWorker as well, since WorkManager has no native power-save-mode constraint.
            .setRequiresBatteryNotLow(true)
            .build()

    companion object {
        // Not private: SyncStatusRepository observes WorkManager by these same unique work names
        // to derive the app-wide "is background sync running" signal (NBC-23), so it needs to
        // agree with whatever SyncScheduler actually enqueues under.
        const val PERIODIC_WORK_NAME = "netbox-periodic-sync"
        const val ONE_TIME_WORK_NAME = "netbox-manual-sync"
        const val STARTUP_WORK_NAME = "netbox-startup-sync"
    }
}

internal fun shouldScheduleStartup(syncOnAppLaunch: Boolean, offlineMode: Boolean): Boolean =
    syncOnAppLaunch && !offlineMode
