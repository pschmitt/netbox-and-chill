package dev.pschmitt.netboxandchill.sync

import dev.pschmitt.netboxandchill.data.repository.DeviceRepository
import dev.pschmitt.netboxandchill.data.repository.DeviceTypeRepository
import dev.pschmitt.netboxandchill.data.repository.DirectoryRepository
import dev.pschmitt.netboxandchill.data.repository.FileDownloadRepository
import dev.pschmitt.netboxandchill.data.repository.GenericObjectRepository
import dev.pschmitt.netboxandchill.data.repository.ImageAttachmentRepository
import dev.pschmitt.netboxandchill.data.repository.OfflineAttachment
import dev.pschmitt.netboxandchill.data.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
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
) {

    suspend fun syncAll(): Result<OfflineSyncSummary> =
        runCatching {
            val devices = deviceRepository.syncAll().getOrThrow()
            directoryRepository.refresh().getOrThrow()

            var genericObjects = 0
            for (model in directoryRepository.cachedModels()) {
                genericObjects += genericObjectRepository.syncAll(model.endpointPath).getOrThrow()
            }

            val durableAttachments =
                if (settingsRepository.syncAttachmentsToDisk.value) syncAttachments()
                else 0
            OfflineSyncSummary(devices, genericObjects, durableAttachments)
        }

    private suspend fun syncAttachments(): Int {
        val devices = deviceRepository.cachedDevices()
        for (device in devices) {
            device.deviceTypeId?.let { deviceTypeRepository.refresh(it).getOrThrow() }
            imageAttachmentRepository.refresh("dcim.device", device.id).getOrThrow()
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
                .getOrThrow()
            downloaded++
        }
        Timber.i("Synced %d durable attachments", downloaded)
        return downloaded
    }
}
