package dev.pschmitt.nyetbox.data.repository

import dev.pschmitt.nyetbox.data.api.GenericNetBoxApi
import dev.pschmitt.nyetbox.data.db.BookmarkDao
import dev.pschmitt.nyetbox.data.db.BookmarkEntity
import dev.pschmitt.nyetbox.data.db.DashboardStatDao
import dev.pschmitt.nyetbox.data.db.DashboardStatEntity
import dev.pschmitt.nyetbox.data.db.NetBoxObjectDao
import dev.pschmitt.nyetbox.data.db.NetBoxObjectEntity
import dev.pschmitt.nyetbox.data.db.NewsItemEntity
import dev.pschmitt.nyetbox.data.db.ObjectChangeDao
import dev.pschmitt.nyetbox.data.db.ObjectChangeEntity
import dev.pschmitt.nyetbox.data.schema.NetBoxEndpointCatalog
import dev.pschmitt.nyetbox.data.schema.jsonInt
import dev.pschmitt.nyetbox.data.schema.jsonString
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

    /** Cached changes for one object; opening a detail page never needs a live changelog lookup. */
    fun observeChangelog(endpointPath: String, id: Int): Flow<List<ObjectChangeEntity>> =
        objectChangeDao.observeForTarget(endpointPath, id)

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
        val entities =
            page.results.mapNotNull { it.toBookmarkEntity(syncedAt = System.currentTimeMillis()) }
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
        val entities =
            page.results.mapNotNull {
                it.toObjectChangeEntity(syncedAt = System.currentTimeMillis())
            }
        objectChangeDao.replaceAll(entities)
        cacheObjectChangeDetails(page.results)
        entities.size
    }

    private suspend fun cacheObjectChangeDetails(summaries: List<JsonObject>) {
        val details =
            summaries.mapNotNull { summary ->
                val id = summary.jsonInt("id") ?: return@mapNotNull null
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

    private fun JsonObject.toObjectChangeCacheEntity(): NetBoxObjectEntity? {
        val id = jsonInt("id") ?: return null
        val objectRepr = jsonString("object_repr") ?: "#$id"
        val actionLabel = (this["action"] as? JsonObject)?.jsonString("label")
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
