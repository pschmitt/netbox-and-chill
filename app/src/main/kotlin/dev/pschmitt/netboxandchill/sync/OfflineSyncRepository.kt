package dev.pschmitt.netboxandchill.sync

import dev.pschmitt.netboxandchill.data.repository.DeviceRepository
import dev.pschmitt.netboxandchill.data.repository.DeviceTypeRepository
import dev.pschmitt.netboxandchill.data.repository.DirectoryRepository
import dev.pschmitt.netboxandchill.data.repository.FileDownloadRepository
import dev.pschmitt.netboxandchill.data.repository.GenericObjectRepository
import dev.pschmitt.netboxandchill.data.repository.ImageAttachmentRepository
import dev.pschmitt.netboxandchill.data.repository.OfflineAttachment
import dev.pschmitt.netboxandchill.data.repository.PendingEditRepository
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException
import retrofit2.HttpException
import timber.log.Timber

data class OfflineSyncSummary(
    val devices: Int,
    val genericObjects: Int,
    val durableAttachments: Int,
)

/** Coordinates the complete cache-first sync used by manual and background refreshes. */
@Singleton
class OfflineSyncRepository
@Inject
constructor(
    private val deviceRepository: DeviceRepository,
    private val directoryRepository: DirectoryRepository,
    private val genericObjectRepository: GenericObjectRepository,
    private val deviceTypeRepository: DeviceTypeRepository,
    private val imageAttachmentRepository: ImageAttachmentRepository,
    private val fileDownloadRepository: FileDownloadRepository,
    private val settingsRepository: SettingsRepository,
    private val pendingEditRepository: PendingEditRepository,
    private val syncIssueReporter: SyncIssueReporter,
) {

    suspend fun syncAll(onProgress: (String) -> Unit = {}): Result<OfflineSyncSummary> {
        var retryableFailure: Throwable? = null

        fun recordFailure(scope: String, error: Throwable) {
            syncIssueReporter.report("$scope: ${error.message ?: error::class.simpleName ?: "failed"}")
            if (error.isRetryableSyncFailure() && retryableFailure == null) retryableFailure = error
        }

        val result =
            runCatching {
            // Resolve queued edits before the normal cache refresh can replace their local view.
            onProgress("Uploading queued edits…")
            pendingEditRepository.syncPending()
            onProgress("Syncing devices…")
            val devices =
                deviceRepository.syncAll().getOrElse {
                    recordFailure("Device sync", it)
                    0
                }
            onProgress("Discovering NetBox models…")
            directoryRepository.refresh().onFailure { recordFailure("Directory sync", it) }

            var genericObjects = 0
            for (model in directoryRepository.cachedModels()) {
                onProgress("Syncing ${model.modelLabel}…")
                genericObjectRepository.syncAll(model.endpointPath).fold(
                    onSuccess = { genericObjects += it },
                    onFailure = { recordFailure("${model.endpointPath} sync", it) },
                )
            }

            val durableAttachments =
                if (settingsRepository.syncAttachmentsToDisk.value) {
                    onProgress("Downloading cached images and documents…")
                    runCatching { syncAttachments() }
                        .getOrElse {
                            recordFailure("Attachment sync", it)
                            0
                        }
                }
                else 0
            retryableFailure?.let { throw it }
                OfflineSyncSummary(devices, genericObjects, durableAttachments)
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
            else -> settingsRepository.clearSyncIssue()
        }
        return result
    }

    private fun Throwable.isRetryableSyncFailure(): Boolean =
        generateSequence(this) { it.cause }.any { cause ->
            cause is IOException || cause is HttpException && cause.code() >= 500
        }

    private suspend fun syncAttachments(): Int {
        val devices = deviceRepository.cachedDevices()
        for (device in devices) {
            device.deviceTypeId?.let { deviceTypeId ->
                deviceTypeRepository
                    .refresh(deviceTypeId)
                    .onFailure { error ->
                        syncIssueReporter.report(
                            "Device type $deviceTypeId refresh failed: ${error.message ?: "failed"}"
                        )
                    }
            }
            imageAttachmentRepository
                .refresh("dcim.device", device.id)
                .onFailure { error ->
                    syncIssueReporter.report(
                        "Image attachments for device ${device.id} failed: ${error.message ?: "failed"}"
                    )
                }
        }

        val attachments = buildList {
            addAll(genericObjectRepository.cachedMediaAttachments())
            deviceTypeRepository.cachedAll().forEach { deviceType ->
                deviceType.frontImageUrl?.let { add(OfflineAttachment(it, "device-type-${deviceType.id}-front")) }
                deviceType.rearImageUrl?.let { add(OfflineAttachment(it, "device-type-${deviceType.id}-rear")) }
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
        }.distinctBy(OfflineAttachment::url)

        var downloaded = 0
        for (attachment in attachments) {
            fileDownloadRepository
                .downloadToPersistent(attachment.url, attachment.filename)
                .onSuccess { downloaded++ }
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
