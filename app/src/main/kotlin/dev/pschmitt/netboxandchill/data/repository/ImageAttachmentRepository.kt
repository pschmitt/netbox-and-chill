package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.api.NetBoxApi
import dev.pschmitt.netboxandchill.data.api.dto.ImageAttachmentDto
import dev.pschmitt.netboxandchill.data.db.ImageAttachmentDao
import dev.pschmitt.netboxandchill.data.db.ImageAttachmentEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Cache-first, like [DeviceRepository] - `objectType` is a NetBox `app_label.model` string (e.g. `dcim.device`). */
@Singleton
class ImageAttachmentRepository
@Inject
constructor(private val api: NetBoxApi, private val dao: ImageAttachmentDao) {

    fun observeFor(objectType: String, objectId: Int): Flow<List<ImageAttachmentEntity>> =
        dao.observeFor(objectType, objectId)

    suspend fun refresh(objectType: String, objectId: Int): Result<List<ImageAttachmentEntity>> = runCatching {
        val entities =
            api.listImageAttachments(objectType, objectId).results.map { it.toEntity(objectType, objectId) }
        dao.clearFor(objectType, objectId)
        dao.upsertAll(entities)
        entities
    }
}

private fun ImageAttachmentDto.toEntity(fallbackObjectType: String, fallbackObjectId: Int): ImageAttachmentEntity =
    ImageAttachmentEntity(
        id = id,
        objectType = objectType ?: fallbackObjectType,
        objectId = this.objectId ?: fallbackObjectId,
        name = name,
        display = display,
        imageUrl = image,
        description = description,
        imageHeight = imageHeight,
        imageWidth = imageWidth,
        created = created,
        lastUpdated = lastUpdated,
        syncedAt = System.currentTimeMillis(),
    )
