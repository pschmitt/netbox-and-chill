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
import javax.inject.Inject
import javax.inject.Singleton
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

internal fun SyncProgress.notificationSubText(): String =
    buildList {
            add(
                "Step ${step.coerceIn(0, totalSteps.coerceAtLeast(1))} of ${totalSteps.coerceAtLeast(1)}"
            )
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

    suspend fun syncAll(onProgress: (SyncProgress) -> Unit = {}): Result<OfflineSyncSummary> =
        cacheDatabaseManager.withActiveServer {
            syncAllLocked(onProgress)
        }

    private suspend fun syncAllLocked(
        onProgress: (SyncProgress) -> Unit = {}
    ): Result<OfflineSyncSummary> {
        var retryableFailure: Throwable? = null
        var step = 0
        var totalSteps = 7 + if (settingsRepository.syncAttachmentsToDisk.value) 1 else 0

        fun reportProgress(message: String): SyncProgress {
            step++
            return SyncProgress(message, step, totalSteps).also(onProgress)
        }

        fun recordFailure(scope: String, error: Throwable) {
            syncIssueReporter.report(
                "$scope: ${error.message ?: error::class.simpleName ?: "failed"}"
            )
            if (error.isRetryableSyncFailure() && retryableFailure == null) retryableFailure = error
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
            val devices =
                deviceRepository.syncAll().getOrElse {
                    recordFailure("Device sync", it)
                    0
                }
            reportProgress("Syncing device types…")
            deviceRepository
                .cachedDevices()
                .asSequence()
                .mapNotNull { it.deviceTypeId }
                .distinct()
                .forEach { deviceTypeId ->
                    deviceTypeRepository.refresh(deviceTypeId).onFailure { error ->
                        recordFailure(
                            "Device type $deviceTypeId sync",
                            error,
                        )
                    }
                }
            reportProgress("Discovering NetBox models…")
            directoryRepository.refresh().onFailure { recordFailure("Directory sync", it) }

            var genericObjects = 0
            val models = directoryRepository.cachedModels()
            val topologyAvailable = models.any { it.appKey == TopologyRepository.PLUGIN_APP_KEY }
            totalSteps =
                7 +
                    models.size +
                    (if (settingsRepository.syncAttachmentsToDisk.value) 1 else 0) +
                    (if (topologyAvailable) 1 else 0)
            for (model in models) {
                reportProgress("Syncing ${model.modelLabel}…")
                genericObjectRepository
                    .syncAll(model.endpointPath)
                    .fold(
                        onSuccess = { genericObjects += it },
                        onFailure = { recordFailure("${model.endpointPath} sync", it) },
                    )
            }

            reportProgress("Syncing rack elevations…")
            genericObjectRepository.cachedObjects("api/dcim/racks/").forEach { rack ->
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
                            syncAttachments { completed, total ->
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
            retryableFailure?.let { throw it }
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

    private suspend fun syncAttachments(onProgress: (completed: Int, total: Int) -> Unit): Int {
        imageAttachmentRepository.refreshAll("dcim.device").onFailure { error ->
            syncIssueReporter.report(
                "Image attachments for devices failed: ${error.message ?: "failed"}"
            )
        }

        val attachments =
            buildList {
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

        var downloaded = 0
        onProgress(0, attachments.size)
        for (attachment in attachments) {
            fileDownloadRepository
                .downloadToPersistent(attachment.url, attachment.filename)
                .onSuccess {
                    downloaded++
                    onProgress(downloaded, attachments.size)
                }
                .onFailure { error ->
                    Timber.w(error, "Couldn't persist offline attachment %s", attachment.url)
                    syncIssueReporter.report(
                        "Attachment ${attachment.filename} failed: ${error.message ?: "download failed"}"
                    )
                }
        }
        Timber.i("Synced %d durable attachments", downloaded)
        return downloaded
    }
}
