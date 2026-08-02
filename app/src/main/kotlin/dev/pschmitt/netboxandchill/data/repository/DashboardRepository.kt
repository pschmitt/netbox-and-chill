package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.api.GenericNetBoxApi
import dev.pschmitt.netboxandchill.data.db.BookmarkDao
import dev.pschmitt.netboxandchill.data.db.BookmarkEntity
import dev.pschmitt.netboxandchill.data.db.DashboardStatDao
import dev.pschmitt.netboxandchill.data.db.DashboardStatEntity
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectDao
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity
import dev.pschmitt.netboxandchill.data.db.NewsItemEntity
import dev.pschmitt.netboxandchill.data.db.ObjectChangeDao
import dev.pschmitt.netboxandchill.data.db.ObjectChangeEntity
import dev.pschmitt.netboxandchill.data.schema.NetBoxEndpointCatalog
import dev.pschmitt.netboxandchill.data.schema.NetBoxRef
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/**
 * Cache-first data for the dashboard/home screen (NBC-9): NetBox's changelog, the signed-in user's
 * bookmarks, and a handful of simple object-count stat tiles. Mirrors [GenericObjectRepository]'s
 * cache-first shape (Room-backed Flow reads, refresh as a best-effort background update) rather
 * than a network-only screen. News is cached separately and fetched from the official NetBox Labs
 * RSS feed as an optional dashboard enhancement.
 */
@Singleton
class DashboardRepository
@Inject
constructor(
    private val api: GenericNetBoxApi,
    private val bookmarkDao: BookmarkDao,
    private val objectChangeDao: ObjectChangeDao,
    private val netBoxObjectDao: NetBoxObjectDao,
    private val statDao: DashboardStatDao,
    private val changeNotificationRepository: ChangeNotificationRepository,
    private val newsRepository: NewsRepository,
    private val json: Json,
) {
    fun observeBookmarks(): Flow<List<BookmarkEntity>> = bookmarkDao.observeAll()

    fun observeChangelog(): Flow<List<ObjectChangeEntity>> = objectChangeDao.observeAll()

    fun observeStats(): Flow<List<DashboardStatEntity>> = statDao.observeAll()

    fun observeNews(): Flow<List<NewsItemEntity>> = newsRepository.observeLatest()

    suspend fun fetchObjectChange(id: Int): Result<JsonObject> = runCatching {
        val cached =
            netBoxObjectDao
                .getById(OBJECT_CHANGE_CACHE_PATH, id)
                ?.let { runCatching { json.decodeFromString(JsonObject.serializer(), it.json) }.getOrNull() }
                ?.takeIf(JsonObject::hasChangeSnapshots)
        cached ?: api.getObject("$OBJECT_CHANGE_ENDPOINT$id/").also { cacheObjectChange(it) }
    }

    /**
     * Refreshes all three widgets independently - one being unreachable (e.g. bookmarks on a
     * pre-3.5 NetBox instance) shouldn't blank out the others; the first failure encountered (if
     * any) is surfaced so the UI can still show a "sync failed" message.
     */
    suspend fun refresh(): Result<Unit> {
        val failures =
            listOfNotNull(
                refreshBookmarks().exceptionOrNull(),
                refreshChangelog().exceptionOrNull(),
                refreshStats().exceptionOrNull(),
            )
        newsRepository.refresh().onFailure {
            // The external feed is optional; an outage must never mark inventory sync as failed.
            Timber.w(it, "Optional NetBox news refresh failed")
        }
        return if (failures.isEmpty()) Result.success(Unit) else Result.failure(failures.first())
    }

    suspend fun refreshBookmarks(): Result<Int> = runCatching {
        val page =
            api.listObjects(
                "api/extras/bookmarks/",
                mapOf("limit" to "50", "ordering" to "-created"),
            )
        val entities = page.results.mapNotNull { it.toBookmarkEntity() }
        bookmarkDao.replaceAll(entities)
        entities.size
    }

    suspend fun refreshChangelog(): Result<Int> = runCatching {
        val page =
            api.listObjects(
                "api/core/object-changes/",
                mapOf("limit" to "25", "ordering" to "-time"),
            )
        runCatching { changeNotificationRepository.process(page.results) }
            .onFailure { Timber.w(it, "Couldn't process NetBox change notifications") }
        val entities = page.results.mapNotNull { it.toObjectChangeEntity() }
        objectChangeDao.replaceAll(entities)
        cacheObjectChangeDetails(page.results)
        entities.size
    }

    private suspend fun cacheObjectChangeDetails(summaries: List<JsonObject>) {
        val details =
            summaries.mapNotNull { summary ->
                val id = summary["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val detail =
                    if (summary.hasChangeSnapshots()) {
                        summary
                    } else {
                        runCatching { api.getObject("$OBJECT_CHANGE_ENDPOINT$id/") }
                            .onFailure { Timber.w(it, "Couldn't cache object change %d", id) }
                            .getOrNull()
                    }
                detail?.takeIf(JsonObject::hasChangeSnapshots)?.toObjectChangeCacheEntity()
            }
        netBoxObjectDao.upsertAll(details)
    }

    private suspend fun cacheObjectChange(change: JsonObject) {
        change.toObjectChangeCacheEntity()?.let { netBoxObjectDao.upsert(it) }
    }

    suspend fun refreshStats(): Result<Int> = runCatching {
        val entities = STAT_ENDPOINTS.mapNotNull { (endpointPath, label) ->
            runCatching { api.listObjects(endpointPath, mapOf("limit" to "1")).count }
                .getOrNull()
                ?.let { count ->
                    DashboardStatEntity(endpointPath, label, count, System.currentTimeMillis())
                }
        }
        statDao.upsertAll(entities)
        entities.size
    }

    private fun JsonObject.toBookmarkEntity(): BookmarkEntity? {
        val id = this["id"]?.jsonPrimitive?.intOrNull ?: return null
        val objectType = this["object_type"]?.jsonPrimitive?.contentOrNull ?: ""
        val created = this["created"]?.jsonPrimitive?.contentOrNull ?: ""
        val target = this["object"] as? JsonObject
        val targetUrl = target?.get("url")?.jsonPrimitive?.contentOrNull
        val targetId = target?.get("id")?.jsonPrimitive?.intOrNull
        val targetDisplay = target?.get("display")?.jsonPrimitive?.contentOrNull
        val display =
            this["display"]?.jsonPrimitive?.contentOrNull ?: targetDisplay ?: "Bookmark #$id"
        val targetEndpointPath = targetUrl?.let(NetBoxRef::endpointFromDetailUrl)
        return BookmarkEntity(
            id = id,
            display = display,
            objectType = objectType,
            targetEndpointPath = targetEndpointPath,
            targetId = targetId,
            created = created,
            syncedAt = System.currentTimeMillis(),
        )
    }

    private fun JsonObject.toObjectChangeEntity(): ObjectChangeEntity? {
        val id = this["id"]?.jsonPrimitive?.intOrNull ?: return null
        val time = this["time"]?.jsonPrimitive?.contentOrNull ?: ""
        val userDisplay =
            (this["user"] as? JsonObject)?.get("display")?.jsonPrimitive?.contentOrNull
                ?: this["user_name"]?.jsonPrimitive?.contentOrNull
                ?: "Unknown"
        val actionObj = this["action"] as? JsonObject
        val actionValue = actionObj?.get("value")?.jsonPrimitive?.contentOrNull ?: ""
        val actionLabel = actionObj?.get("label")?.jsonPrimitive?.contentOrNull ?: actionValue
        val objectRepr = this["object_repr"]?.jsonPrimitive?.contentOrNull ?: "#$id"
        // Null for deletes - the changed object no longer exists to derive a reference from.
        val target = this["changed_object"] as? JsonObject
        val targetUrl = target?.get("url")?.jsonPrimitive?.contentOrNull
        val targetId = target?.get("id")?.jsonPrimitive?.intOrNull
        val targetEndpointPath = targetUrl?.let(NetBoxRef::endpointFromDetailUrl)
        return ObjectChangeEntity(
            id = id,
            time = time,
            userDisplay = userDisplay,
            actionValue = actionValue,
            actionLabel = actionLabel,
            objectRepr = objectRepr,
            targetEndpointPath = targetEndpointPath,
            targetId = targetId,
            syncedAt = System.currentTimeMillis(),
        )
    }

    private fun JsonObject.toObjectChangeCacheEntity(): NetBoxObjectEntity? {
        val id = this["id"]?.jsonPrimitive?.intOrNull ?: return null
        val objectRepr = this["object_repr"]?.jsonPrimitive?.contentOrNull ?: "#$id"
        val actionLabel =
            (this["action"] as? JsonObject)?.get("label")?.jsonPrimitive?.contentOrNull
        return NetBoxObjectEntity(
            endpointPath = OBJECT_CHANGE_CACHE_PATH,
            id = id,
            display = objectRepr,
            secondaryLine = actionLabel,
            json = json.encodeToString(JsonObject.serializer(), this),
            syncedAt = System.currentTimeMillis(),
        )
    }

    private companion object {
        const val OBJECT_CHANGE_ENDPOINT = "api/core/object-changes/"
        // Keep these records separate from the generic directory cache. A directory sync may see
        // the same endpoint as a normal object type and must not overwrite a full detail snapshot
        // with the list serializer's summary-only representation.
        const val OBJECT_CHANGE_CACHE_PATH = "__cache/object-changes/"

        // Kept small and cheap (`?limit=1`, only `count` is read, no full sync) - "a handful of
        // key models," not an exhaustive sweep of NetBox's data model. Picked models this app
        // already deals with elsewhere (typed Device/DeviceType caches from NBC-1/NBC-3).
        val STAT_ENDPOINTS =
            NetBoxEndpointCatalog.coreModels.take(4).map { it.endpointPath to it.label }
    }
}

private fun JsonObject.hasChangeSnapshots(): Boolean =
    containsKey("prechange_data") || containsKey("postchange_data")
