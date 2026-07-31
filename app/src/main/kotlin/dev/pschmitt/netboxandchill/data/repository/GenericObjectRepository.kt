package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.api.GenericNetBoxApi
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectDao
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/** Cache-first list/detail access for any NetBox object type, keyed by its endpoint path. */
@Singleton
class GenericObjectRepository
@Inject
constructor(private val api: GenericNetBoxApi, private val dao: NetBoxObjectDao, private val json: Json) {

    fun observeObjects(endpointPath: String, query: String): Flow<List<NetBoxObjectEntity>> =
        if (query.isBlank()) dao.observeAll(endpointPath) else dao.search(endpointPath, query)

    fun observeObject(endpointPath: String, id: Int): Flow<NetBoxObjectEntity?> =
        dao.observeById(endpointPath, id)

    suspend fun refreshObject(endpointPath: String, id: Int): Result<NetBoxObjectEntity> = runCatching {
        val entity = api.getObject("$endpointPath$id/").toEntity(endpointPath)
        dao.upsert(entity)
        entity
    }

    suspend fun syncAll(endpointPath: String, pageSize: Int = 200): Result<Int> = runCatching {
        var offset = 0
        var total = 0
        while (true) {
            val page =
                api.listObjects(
                    endpointPath,
                    mapOf("limit" to pageSize.toString(), "offset" to offset.toString()),
                )
            if (page.results.isEmpty()) break
            dao.upsertAll(page.results.map { it.toEntity(endpointPath) })
            total += page.results.size
            offset += pageSize
            if (page.next == null) break
        }
        Timber.i("Synced %d objects from %s", total, endpointPath)
        total
    }

    suspend fun cachedCount(endpointPath: String): Int = dao.count(endpointPath)

    private fun JsonObject.toEntity(endpointPath: String): NetBoxObjectEntity {
        val id = this["id"]?.jsonPrimitive?.intOrNull ?: error("NetBox object at $endpointPath has no id")
        val display =
            this["display"]?.jsonPrimitive?.contentOrNull
                ?: this["name"]?.jsonPrimitive?.contentOrNull
                ?: "#$id"
        val secondaryLine =
            (this["status"] as? JsonObject)?.get("label")?.jsonPrimitive?.contentOrNull
                ?: this["description"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        return NetBoxObjectEntity(
            endpointPath = endpointPath,
            id = id,
            display = display,
            secondaryLine = secondaryLine,
            json = json.encodeToString(JsonObject.serializer(), this),
            syncedAt = System.currentTimeMillis(),
        )
    }
}
