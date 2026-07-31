package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.api.GenericNetBoxApi
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectDao
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity
import dev.pschmitt.netboxandchill.sync.SyncIssueReporter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/** Cache-first list/detail access for any NetBox object type, keyed by its endpoint path. */
@Singleton
class GenericObjectRepository
@Inject
constructor(
    private val api: GenericNetBoxApi,
    private val dao: NetBoxObjectDao,
    private val json: Json,
    private val syncIssueReporter: SyncIssueReporter,
) {

    fun observeObjects(
        endpointPath: String,
        query: String,
        filterKey: String? = null,
        filterValue: Int? = null,
    ): Flow<List<NetBoxObjectEntity>> {
        val source = if (query.isBlank()) dao.observeAll(endpointPath) else dao.search(endpointPath, query)
        if (filterKey == null || filterValue == null) return source
        return source.map { objects -> objects.filter { it.matchesRelation(json, filterKey, filterValue) } }
    }

    fun observeObject(endpointPath: String, id: Int): Flow<NetBoxObjectEntity?> =
        dao.observeById(endpointPath, id)

    suspend fun refreshObject(endpointPath: String, id: Int): Result<NetBoxObjectEntity> = runCatching {
        val entity = api.getObject("$endpointPath$id/").toEntity(endpointPath)
        dao.upsert(entity)
        entity
    }

    suspend fun syncAll(
        endpointPath: String,
        pageSize: Int = 200,
        filters: Map<String, String> = emptyMap(),
    ): Result<Int> = runCatching {
        var offset = 0
        var total = 0
        while (true) {
            val page =
                api.listObjects(
                    endpointPath,
                    buildMap {
                        put("limit", pageSize.toString())
                        put("offset", offset.toString())
                        putAll(filters)
                    },
                )
            if (page.results.isEmpty()) break
            var skippedCount = 0
            val entities =
                page.results.mapNotNull { objectJson ->
                    runCatching { objectJson.toEntity(endpointPath) }
                        .onFailure { error ->
                            skippedCount++
                            Timber.w(error, "Skipping non-object response from %s during sync", endpointPath)
                        }
                        .getOrNull()
                }
            // A collection made entirely of non-ID summaries (for example NetBox's
            // core/background-queues endpoint) is not an inventory object type and is safely
            // ignored. A partial mix is suspicious and remains visible as a sync warning.
            if (skippedCount > 0 && entities.isNotEmpty()) {
                syncIssueReporter.report(
                    "Skipped $skippedCount record(s) from $endpointPath because they have no numeric id"
                )
            }
            dao.upsertAll(entities)
            total += entities.size
            offset += pageSize
            if (page.next == null) break
        }
        Timber.i("Synced %d objects from %s", total, endpointPath)
        total
    }

    suspend fun cachedCount(endpointPath: String): Int = dao.count(endpointPath)

    suspend fun cachedObjects(endpointPath: String): List<NetBoxObjectEntity> = dao.getAll(endpointPath)

    suspend fun cachedMediaAttachments(): List<OfflineAttachment> =
        dao.getAll().flatMap { entity ->
            val objectJson = runCatching { json.decodeFromString(JsonObject.serializer(), entity.json) }.getOrNull()
                ?: return@flatMap emptyList()
            objectJson.mediaAttachments()
        }

    /** Upserts arbitrary already-fetched objects (e.g. [GlobalSearchRepository]'s `?q=` hits) into
     * the same cache [observeObjects] reads from, so a live search result is also offline-findable
     * from then on - reuses the same [toEntity] mapping [syncAll] uses, not a separate path. */
    suspend fun cacheSearchResults(endpointPath: String, objects: List<JsonObject>) {
        if (objects.isEmpty()) return
        dao.upsertAll(objects.map { it.toEntity(endpointPath) })
    }

    /** Writes a locally merged object into the same cache used by the detail screen. */
    suspend fun cacheLocalObject(endpointPath: String, objectJson: JsonObject) {
        dao.upsert(objectJson.toEntity(endpointPath))
    }

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

private fun NetBoxObjectEntity.matchesRelation(parser: Json, relationKey: String, expectedId: Int): Boolean {
    val objectJson = runCatching { parser.decodeFromString(JsonObject.serializer(), json) }.getOrNull() ?: return false
    return when (val relation = objectJson[relationKey]) {
        is JsonObject -> relation["id"]?.jsonPrimitive?.intOrNull == expectedId
        is JsonArray -> relation.any { (it as? JsonObject)?.get("id")?.jsonPrimitive?.intOrNull == expectedId }
        is JsonPrimitive -> relation.intOrNull == expectedId
        else -> false
    }
}

data class OfflineAttachment(val url: String, val filename: String)

internal fun JsonObject.mediaAttachments(): List<OfflineAttachment> {
    val attachments = linkedMapOf<String, OfflineAttachment>()

    fun visit(element: JsonElement, fallbackName: String?) {
        when (element) {
            is JsonPrimitive -> {
                val value = element.contentOrNull
                if (value != null && value.startsWith("http") && "/media/" in value) {
                    val filename =
                        fallbackName?.takeIf { '.' in it && '/' !in it }
                            ?: value.substringAfterLast('/').substringBefore('?')
                    attachments.putIfAbsent(value, OfflineAttachment(value, filename.ifBlank { "attachment" }))
                }
            }
            is JsonObject ->
                element.forEach { (key, child) ->
                    visit(child, (element["filename"] as? JsonPrimitive)?.contentOrNull ?: key)
                }
            is JsonArray -> element.forEach { child -> visit(child, fallbackName) }
        }
    }

    val filename = (this["filename"] as? JsonPrimitive)?.contentOrNull
    for ((key, value) in this) visit(value, filename ?: key)
    return attachments.values.toList()
}
