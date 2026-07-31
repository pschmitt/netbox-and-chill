package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.api.NetBoxApi
import dev.pschmitt.netboxandchill.data.api.dto.DeviceTypeDto
import dev.pschmitt.netboxandchill.data.db.DeviceTypeDao
import dev.pschmitt.netboxandchill.data.db.DeviceTypeEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Cache-first, like [DeviceRepository] - only the front/rear stock-photo URLs are of interest here. */
@Singleton
class DeviceTypeRepository @Inject constructor(private val api: NetBoxApi, private val dao: DeviceTypeDao) {

    fun observeAll(): Flow<List<DeviceTypeEntity>> = dao.observeAll()

    fun observe(id: Int): Flow<DeviceTypeEntity?> = dao.observeById(id)

    suspend fun cachedAll(): List<DeviceTypeEntity> = dao.getAll()

    /** Fetches and caches [id] only if it isn't already cached - device-type photos rarely change. */
    suspend fun ensureCached(id: Int) {
        if (dao.getById(id) == null) refresh(id)
    }

    suspend fun refresh(id: Int): Result<DeviceTypeEntity> = runCatching {
        val entity = api.getDeviceType(id).toEntity()
        dao.upsert(entity)
        entity
    }
}

private fun DeviceTypeDto.toEntity(): DeviceTypeEntity =
    DeviceTypeEntity(
        id = id,
        model = model,
        frontImageUrl = frontImage,
        rearImageUrl = rearImage,
        syncedAt = System.currentTimeMillis(),
    )
