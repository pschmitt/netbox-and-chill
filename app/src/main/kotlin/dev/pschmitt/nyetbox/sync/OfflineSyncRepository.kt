package dev.pschmitt.nyetbox.sync

import dev.pschmitt.nyetbox.data.db.CacheDatabaseManager
import dev.pschmitt.nyetbox.data.repository.CustomFieldRepository
import dev.pschmitt.nyetbox.data.repository.DashboardRepository
import dev.pschmitt.nyetbox.data.repository.DeviceRepository
import dev.pschmitt.nyetbox.data.repository.DeviceTypeRepository
import dev.pschmitt.nyetbox.data.repository.DirectoryRepository
import dev.pschmitt.nyetbox.data.repository.FileDownloadRepository
import dev.pschmitt.nyetbox.data.repository.GenericObjectRepository
import dev.pschmitt.nyetbox.data.repository.ImageAttachmentRepository
import dev.pschmitt.nyetbox.data.repository.OfflineAttachment
import dev.pschmitt.nyetbox.data.repository.PendingEditRepository
import dev.pschmitt.nyetbox.data.repository.RackElevationRepository
import dev.pschmitt.nyetbox.data.repository.RackFace
import dev.pschmitt.nyetbox.data.repository.ReconciliationSummary
import dev.pschmitt.nyetbox.data.repository.SettingsRepository
import dev.pschmitt.nyetbox.data.repository.TopologyRepository
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import retrofit2.HttpException
import timber.log.Timber

data class OfflineSyncSummary(
    val devices: Int,
    val genericObjects: Int,
    val durableAttachments: Int,
    val reconciliation: ReconciliationSummary = ReconciliationSummary(),
)

data class SyncProgress(
    val message: String,
    val step: Int,
    val totalSteps: Int,
    val itemLabel: String? = null,
    val itemCompleted: Int? = null,
    val itemTotal: Int? = null,
)

internal fun SyncProgress.itemProgressText(): String? =
    if (itemLabel != null && itemCompleted != null && itemTotal != null && itemTotal >= 0) {
        "$itemCompleted of $itemTotal $itemLabel"
    } else {
        null
    }

internal fun SyncProgress.notificationSubText(): String = buildList {
    add("Step ${step.coerceIn(0, totalSteps.coerceAtLeast(1))} of ${totalSteps.coerceAtLeast(1)}")
    itemProgressText()?.let(::add)
}
    .joinToString(" · ")

internal fun SyncProgress.notificationText(): String = buildString {
    append(message)
    itemProgressText()?.let {
        append('\n')
        append(it)
    }
}

/**
 * How long a cache is allowed to rely on incremental (`last_updated`-filtered) syncs before the
 * next sync forces a full, unfiltered pass. Incremental syncs can't see objects deleted on the
 * server since they only ask for what changed - a periodic full pass is what actually reconciles
 * those deletions (see the pruning step in [OfflineSyncRepository.syncAllLocked]).
 */
private val FULL_SYNC_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(24)

/** Coordinates the complete cache-first sync used by manual and background refreshes. */
@Singleton
class OfflineSyncRepository
@Inject
constructor(
    private val deviceRepository: DeviceRepository,
    private val dashboardRepository: DashboardRepository,
    private val customFieldRepository: CustomFieldRepository,
    private val directoryRepository: DirectoryRepository,
    private val genericObjectRepository: GenericObjectRepository,
    private val deviceTypeRepository: DeviceTypeRepository,
    private val imageAttachmentRepository: ImageAttachmentRepository,
    private val rackElevationRepository: RackElevationRepository,
    private val fileDownloadRepository: FileDownloadRepository,
    private val settingsRepository: SettingsRepository,
    private val pendingEditRepository: PendingEditRepository,
    private val topologyRepository: TopologyRepository,
    private val syncIssueReporter: SyncIssueReporter,
    private val cacheDatabaseManager: CacheDatabaseManager,
) {

    suspend fun syncAll(
        forceFullSync: Boolean = false,
        onProgress: (SyncProgress) -> Unit = {},
    ): Result<OfflineSyncSummary> = cacheDatabaseManager.withActiveServer {
        syncAllLocked(forceFullSync, onProgress)
    }

    private suspend fun syncAllLocked(
        forceFullSync: Boolean = false,
        onProgress: (SyncProgress) -> Unit = {},
    ): Result<OfflineSyncSummary> {
        // Everything upserted from this point on is stamped with a syncedAt >= passStartedAt, so
        // any row still older than it once a full fetch of its endpoint succeeds is something the
        // server no longer has - see the pruning calls below.
        val passStartedAt = System.currentTimeMillis()
        val isFullSyncPass =
            forceFullSync ||
                (passStartedAt - settingsRepository.lastFullSyncAt > FULL_SYNC_INTERVAL_MILLIS)
        val concurrency = settingsRepository.syncConcurrency.value

        val failureCount = AtomicInteger(0)
        val retryableFailure = AtomicReference<Throwable?>(null)
        var step = 0
        var totalSteps = 8 + if (settingsRepository.syncAttachmentsToDisk.value) 1 else 0

        fun reportProgress(message: String): SyncProgress {
            step++
            return SyncProgress(message, step, totalSteps).also(onProgress)
        }

        fun recordFailure(scope: String, error: Throwable) {
            failureCount.incrementAndGet()
            syncIssueReporter.report(
                "$scope: ${error.message ?: error::class.simpleName ?: "failed"}"
            )
            if (error.isRetryableSyncFailure()) retryableFailure.compareAndSet(null, error)
        }

        val result = runCatching {
            // Resolve queued edits before the normal cache refresh can replace their local view.
            reportProgress("Uploading queued edits…")
            val pendingResult = pendingEditRepository.syncPending()
            pendingResult.retryableFailure?.let { recordFailure("Queued mutation sync", it) }
            reportProgress("Syncing dashboard data…")
            dashboardRepository.refresh().onFailure { recordFailure("Dashboard sync", it) }
            reportProgress("Syncing custom-field definitions…")
            customFieldRepository.refresh().onFailure { recordFailure("Custom-field sync", it) }

            reportProgress("Syncing devices…")
            val (deviceResult, deviceFetchWasFull) = syncDevicesIncrementally(isFullSyncPass)
            val devices = deviceResult.getOrElse {
                recordFailure("Device sync", it)
                0
            }
            if (deviceResult.isSuccess && deviceFetchWasFull) {
                deviceRepository.pruneStale(passStartedAt)
            }

            reportProgress("Syncing device types…")
            deviceRepository
                .cachedDevices()
                .asSequence()
                .mapNotNull { it.deviceTypeId }
                .distinct()
                .toList()
                .syncConcurrently(concurrency) { deviceTypeId ->
                    deviceTypeRepository.refresh(deviceTypeId).onFailure { error ->
                        recordFailure("Device type $deviceTypeId sync", error)
                    }
                }

            reportProgress("Discovering NetBox models…")
            directoryRepository.refresh().onFailure { recordFailure("Directory sync", it) }

            val models = directoryRepository.cachedModels()
            val topologyAvailable = models.any { it.appKey == TopologyRepository.PLUGIN_APP_KEY }
            totalSteps =
                8 +
                    (if (settingsRepository.syncAttachmentsToDisk.value) 1 else 0) +
                    (if (topologyAvailable) 1 else 0)

            val modelsProgress = reportProgress("Syncing ${models.size} NetBox models…")
            val modelsCompleted = AtomicInteger(0)
            val genericObjectsTotal = AtomicInteger(0)
            // Endpoints whose full, unfiltered fetch just succeeded this pass - the only ones
            // safe to prune (an endpoint that only got an incremental fetch, or whose fetch
            // failed partway through, must keep whatever it already had cached).
            val fullySyncedEndpoints = ConcurrentHashMap.newKeySet<String>()
            models.syncConcurrently(concurrency) { model ->
                val (syncResult, wasFullFetch) =
                    syncModelIncrementally(model.endpointPath, isFullSyncPass)
                syncResult.fold(
                    onSuccess = { count ->
                        genericObjectsTotal.addAndGet(count)
                        if (wasFullFetch) fullySyncedEndpoints += model.endpointPath
                    },
                    onFailure = { recordFailure("${model.endpointPath} sync", it) },
                )
                onProgress(
                    modelsProgress.copy(
                        itemLabel = "models",
                        itemCompleted = modelsCompleted.incrementAndGet(),
                        itemTotal = models.size,
                    )
                )
            }
            fullySyncedEndpoints.forEach { endpointPath ->
                genericObjectRepository.pruneStale(endpointPath, passStartedAt)
            }
            val genericObjects = genericObjectsTotal.get()

            reportProgress("Syncing rack elevations…")
            val racks = genericObjectRepository.cachedObjects("api/dcim/racks/")
            racks.syncConcurrently(concurrency) { rack ->
                rackElevationRepository.refresh(rack.id, RackFace.FRONT).onFailure {
                    recordFailure("Rack ${rack.id} front elevation", it)
                }
                rackElevationRepository.refresh(rack.id, RackFace.REAR).onFailure {
                    recordFailure("Rack ${rack.id} rear elevation", it)
                }
            }

            if (topologyAvailable) {
                reportProgress("Syncing topology map…")
                topologyRepository.refresh().onFailure {
                    syncIssueReporter.report("Topology sync: ${it.message ?: "failed"}")
                }
            }

            val durableAttachments =
                if (settingsRepository.syncAttachmentsToDisk.value) {
                    val attachmentProgress =
                        reportProgress("Downloading cached images and documents…")
                    runCatching {
                        syncAttachments(concurrency) { completed, total ->
                            onProgress(
                                attachmentProgress.copy(
                                    itemLabel = "images/documents",
                                    itemCompleted = completed,
                                    itemTotal = total,
                                )
                            )
                        }
                    }
                        .getOrElse {
                            recordFailure("Attachment sync", it)
                            0
                        }
                } else 0
            retryableFailure.get()?.let { throw it }
            // Only a fully clean full-sync pass is trustworthy evidence that every endpoint's
            // cache now matches the server - a partial failure leaves some endpoints unreconciled,
            // so the next periodic run (a few hours away) should try a full pass again rather than
            // waiting out the entire interval.
            if (isFullSyncPass && failureCount.get() == 0) {
                settingsRepository.lastFullSyncAt = passStartedAt
            }
            OfflineSyncSummary(
                devices = devices,
                genericObjects = genericObjects,
                durableAttachments = durableAttachments,
                reconciliation = pendingResult.reconciliation,
            )
        }
        val warnings = syncIssueReporter.drain()
        when {
            result.isFailure ->
                settingsRepository.recordSyncIssue(
                    buildString {
                        append(result.exceptionOrNull()?.message ?: "Sync failed")
                        if (warnings.isNotEmpty()) {
                            append("\n")
                            append(warnings.joinToString("\n"))
                        }
                    }
                )
            warnings.isNotEmpty() -> settingsRepository.recordSyncIssue(warnings.joinToString("\n"))
            else -> {
                settingsRepository.clearSyncIssue()
                settingsRepository.recordSuccessfulSync()
            }
        }
        return result
    }

    private fun Throwable.isRetryableSyncFailure(): Boolean =
        generateSequence(this) { it.cause }
            .any { cause -> cause is IOException || cause is HttpException && cause.code() >= 500 }

    /**
     * Fetches only devices changed since the last watermark, unless [isFullSyncPass] or there's no
     * watermark yet (never synced before). Falls back to one full, unfiltered fetch if the filtered
     * attempt fails - a small number of NetBox deployments/proxies may not support
     * `last_updated__gte`. Returns whether the fetch that actually succeeded was a full one, i.e.
     * whether it's safe to prune devices this cache still has that the server no longer does.
     */
    private suspend fun syncDevicesIncrementally(
        isFullSyncPass: Boolean
    ): Pair<Result<Int>, Boolean> {
        val watermark = if (isFullSyncPass) null else deviceRepository.lastUpdatedWatermark()
        var result = deviceRepository.syncAll(lastUpdatedGte = watermark)
        var wasFullFetch = watermark == null
        if (result.isFailure && watermark != null) {
            result = deviceRepository.syncAll()
            wasFullFetch = true
        }
        return result to wasFullFetch
    }

    /** Same incremental/fallback/prune-eligibility contract as [syncDevicesIncrementally]. */
    private suspend fun syncModelIncrementally(
        endpointPath: String,
        isFullSyncPass: Boolean,
    ): Pair<Result<Int>, Boolean> {
        val watermark =
            if (isFullSyncPass) null else genericObjectRepository.lastUpdatedWatermark(endpointPath)
        val filters = watermark?.let { mapOf("last_updated__gte" to it) } ?: emptyMap()
        var result = genericObjectRepository.syncAll(endpointPath, filters = filters)
        var wasFullFetch = watermark == null
        if (result.isFailure && watermark != null) {
            result = genericObjectRepository.syncAll(endpointPath)
            wasFullFetch = true
        }
        return result to wasFullFetch
    }

    /**
     * Runs [action] over every item with at most [concurrency] running at once. The endpoints this
     * is used for (NetBox models, device types, rack elevations, attachment downloads) are
     * otherwise unrelated to each other, so overlapping their network round-trips is a meaningful
     * speedup - bounded so a small self-hosted NetBox instance isn't hit with more concurrent
     * requests than it can handle. Concurrent Room writes from within [action] are safe (Room's
     * WAL-backed connection serializes them), so no extra locking is needed there.
     */
    private suspend fun <T> Iterable<T>.syncConcurrently(
        concurrency: Int,
        action: suspend (T) -> Unit,
    ) = coroutineScope {
        val semaphore = Semaphore(concurrency.coerceAtLeast(1))
        map { item -> async { semaphore.withPermit { action(item) } } }.awaitAll()
    }

    private suspend fun syncAttachments(
        concurrency: Int,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): Int {
        imageAttachmentRepository.refreshAll("dcim.device").onFailure { error ->
            syncIssueReporter.report(
                "Image attachments for devices failed: ${error.message ?: "failed"}"
            )
        }

        val attachments = buildList {
            addAll(genericObjectRepository.cachedMediaAttachments())
            deviceTypeRepository.cachedAll().forEach { deviceType ->
                deviceType.frontImageUrl?.let {
                    add(OfflineAttachment(it, "device-type-${deviceType.id}-front"))
                }
                deviceType.rearImageUrl?.let {
                    add(OfflineAttachment(it, "device-type-${deviceType.id}-rear"))
                }
            }
            imageAttachmentRepository.cachedAll().forEach { attachment ->
                attachment.imageUrl?.let {
                    add(
                        OfflineAttachment(
                            it,
                            attachment.name?.takeIf(String::isNotBlank)
                                ?: attachment.display?.takeIf(String::isNotBlank)
                                ?: "image-attachment-${attachment.id}",
                        )
                    )
                }
            }
        }
            .distinctBy(OfflineAttachment::url)

        val downloaded = AtomicInteger(0)
        onProgress(0, attachments.size)
        attachments.syncConcurrently(concurrency) { attachment ->
            fileDownloadRepository
                .downloadToPersistent(attachment.url, attachment.filename)
                .onSuccess { onProgress(downloaded.incrementAndGet(), attachments.size) }
                .onFailure { error ->
                    Timber.w(error, "Couldn't persist offline attachment %s", attachment.url)
                    syncIssueReporter.report(
                        "Attachment ${attachment.filename} failed: ${error.message ?: "download failed"}"
                    )
                }
        }
        Timber.i("Synced %d durable attachments", downloaded.get())
        return downloaded.get()
    }
}
