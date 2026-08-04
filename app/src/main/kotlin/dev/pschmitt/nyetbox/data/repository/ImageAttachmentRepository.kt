package dev.pschmitt.nyetbox.data.repository

import dev.pschmitt.nyetbox.data.api.NetBoxApi
import dev.pschmitt.nyetbox.data.api.dto.ImageAttachmentDto
import dev.pschmitt.nyetbox.data.db.ImageAttachmentDao
import dev.pschmitt.nyetbox.data.db.ImageAttachmentEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Cache-first, like [DeviceRepository] - `objectType` is a NetBox `app_label.model` string (e.g.
 * `dcim.device`).
 */
@Singleton
class ImageAttachmentRepository
@Inject
constructor(private val api: NetBoxApi, private val dao: ImageAttachmentDao) {

    fun observeFor(objectType: String, objectId: Int): Flow<List<ImageAttachmentEntity>> =
        dao.observeFor(objectType, objectId)

    suspend fun cachedAll(): List<ImageAttachmentEntity> = dao.getAll()

    suspend fun refresh(objectType: String, objectId: Int): Result<List<ImageAttachmentEntity>> =
        runCatching {
            val entities =
                api.listImageAttachments(objectType, objectId).results.map {
                    it.toEntity(objectType, objectId)
                }
            dao.clearFor(objectType, objectId)
            dao.upsertAll(entities)
            entities
        }

    /** Refreshes every attachment for an object type with one paginated collection walk. */
    suspend fun refreshAll(objectType: String): Result<List<ImageAttachmentEntity>> = runCatching {
        val entities = mutableListOf<ImageAttachmentEntity>()
        var offset = 0
        while (true) {
            val page = api.listImageAttachments(objectType, limit = 200, offset = offset)
            page.results.forEach { attachment ->
                attachment.objectId?.let { objectId ->
                    entities += attachment.toEntity(objectType, objectId)
                }
            }
            if (page.next == null || page.results.isEmpty()) break
            offset += page.results.size
        }
        dao.clearForObjectType(objectType)
        dao.upsertAll(entities)
        entities
    }
}

private fun ImageAttachmentDto.toEntity(
    fallbackObjectType: String,
    fallbackObjectId: Int,
): ImageAttachmentEntity =
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
