package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.db.DeviceEntity
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity
import dev.pschmitt.netboxandchill.data.db.RecentVisitDao
import dev.pschmitt.netboxandchill.data.db.RecentVisitEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class RecentVisitRepository @Inject constructor(private val dao: RecentVisitDao) {
    fun observeRecent(limit: Int = 12): Flow<List<RecentVisitEntity>> = dao.observeRecent(limit)

    suspend fun record(objectEntity: NetBoxObjectEntity) {
        record(
            endpointPath = objectEntity.endpointPath,
            id = objectEntity.id,
            display = objectEntity.display,
            secondaryLine = objectEntity.secondaryLine,
        )
    }

    suspend fun record(device: DeviceEntity) {
        record(
            endpointPath = "api/dcim/devices/",
            id = device.id,
            display = device.name,
            secondaryLine = device.statusLabel ?: device.siteName,
        )
    }

    private suspend fun record(
        endpointPath: String,
        id: Int,
        display: String,
        secondaryLine: String?,
    ) {
        dao.upsert(
            RecentVisitEntity(
                endpointPath = endpointPath,
                id = id,
                display = display,
                secondaryLine = secondaryLine,
                visitedAt = System.currentTimeMillis(),
            )
        )
        dao.prune()
    }
}
