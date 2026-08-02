package dev.pschmitt.netboxandchill.sync

import androidx.work.WorkInfo
import androidx.work.WorkManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * App-wide "is background sync currently running" signal (NBC-23) - backs a persistent indicator
 * that's correct no matter which screen (if any) is on-screen, unlike the existing per-screen
 * `PullToRefreshBox` spinners, which only reflect a manual pull while that specific screen is
 * visible.
 *
 * Reads straight off [WorkManager]'s own locally-persisted [WorkInfo] state for the two unique work
 * names [SyncScheduler] enqueues under - that state lives in WorkManager's own Room database
 * on-device, so this renders correctly offline too. Only the sync work *itself* needs connectivity
 * (see its `NetworkType.CONNECTED` constraint); observing whether it's running does not.
 */
@Singleton
class SyncStatusRepository @Inject constructor(private val workManager: WorkManager) {

    val isSyncing: Flow<Boolean> =
        combine(
            workManager.getWorkInfosForUniqueWorkFlow(SyncScheduler.PERIODIC_WORK_NAME),
            workManager.getWorkInfosForUniqueWorkFlow(SyncScheduler.ONE_TIME_WORK_NAME),
            workManager.getWorkInfosForUniqueWorkFlow(SyncScheduler.STARTUP_WORK_NAME),
        ) { periodic, oneTime, startup ->
            (periodic + oneTime + startup).any { it.state == WorkInfo.State.RUNNING }
        }

    /** The latest manual sync state, including terminal success/failure for refresh feedback. */
    val manualSyncState: Flow<WorkInfo.State?> =
        workManager
            .getWorkInfosForUniqueWorkFlow(SyncScheduler.ONE_TIME_WORK_NAME)
            .map { infos -> infos.firstOrNull()?.state }
}
