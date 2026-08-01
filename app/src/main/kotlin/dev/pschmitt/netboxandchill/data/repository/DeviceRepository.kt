package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.api.NetBoxApi
import dev.pschmitt.netboxandchill.data.api.dto.DeviceDto
import dev.pschmitt.netboxandchill.data.db.DeviceDao
import dev.pschmitt.netboxandchill.data.db.DeviceEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * Cache-first: reads always come from Room; [syncAll]/[refreshDevice] pull from the API and upsert.
 */
@Singleton
class DeviceRepository @Inject constructor(private val api: NetBoxApi, private val dao: DeviceDao) {

    fun observeDevices(query: String): Flow<List<DeviceEntity>> =
        if (query.isBlank()) dao.observeAll() else dao.search(query)

    fun observeDevice(id: Int): Flow<DeviceEntity?> = dao.observeById(id)

    suspend fun refreshDevice(id: Int): Result<DeviceEntity> = runCatching {
        val entity = api.getDevice(id).toEntity()
        dao.upsert(entity)
        entity
    }

    /**
     * Resolves a scanner asset-tag payload from Room first, then performs a narrow best-effort API
     * search.
     */
    suspend fun findByAssetTag(assetTag: String): DeviceEntity? {
        val trimmed = assetTag.trim()
        val withoutPrefix = trimmed.removePrefix("#")
        dao.getByAssetTag(trimmed, withoutPrefix)?.let {
            return it
        }
        return runCatching {
                api.listDevices(limit = 50, search = withoutPrefix)
                    .results
                    .firstOrNull { normalizeAssetTag(it.assetTag) == normalizeAssetTag(trimmed) }
                    ?.toEntity()
                    ?.also { dao.upsert(it) }
            }
            .getOrNull()
    }

    /** Full paginated sync of every device NetBox knows about. */
    suspend fun syncAll(pageSize: Int = 200): Result<Int> = runCatching {
        var offset = 0
        var total = 0
        while (true) {
            val page = api.listDevices(limit = pageSize, offset = offset)
            if (page.results.isEmpty()) break
            dao.upsertAll(page.results.map { it.toEntity() })
            total += page.results.size
            offset += pageSize
            if (page.next == null) break
        }
        Timber.i("Synced %d devices", total)
        total
    }

    suspend fun cachedDeviceCount(): Int = dao.count()

    suspend fun cachedDevices(): List<DeviceEntity> = dao.getAll()
}

private fun DeviceDto.toEntity(): DeviceEntity =
    DeviceEntity(
        id = id,
        name = name ?: display ?: "Device $id",
        url = url ?: "",
        statusValue = status?.value,
        statusLabel = status?.label,
        siteName = site?.display ?: site?.name,
        siteId = site?.id,
        rackName = rack?.display ?: rack?.name,
        rackId = rack?.id,
        position = position,
        roleName = effectiveRole?.display ?: effectiveRole?.name,
        manufacturerName = deviceType?.manufacturer?.name,
        deviceTypeModel = deviceType?.model,
        deviceTypeId = deviceType?.id,
        serial = serial,
        assetTag = assetTag,
        primaryIp = primaryIp?.address ?: primaryIp?.display,
        comments = comments,
        lastUpdated = lastUpdated,
        syncedAt = System.currentTimeMillis(),
        customFieldsJson = customFields?.toString(),
        primaryIpId = primaryIp?.id,
    )

private fun normalizeAssetTag(value: String?): String =
    value.orEmpty().trim().removePrefix("#").lowercase()
