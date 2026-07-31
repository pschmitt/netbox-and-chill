package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.api.GenericNetBoxApi
import dev.pschmitt.netboxandchill.data.db.BookmarkDao
import dev.pschmitt.netboxandchill.data.db.BookmarkEntity
import dev.pschmitt.netboxandchill.data.db.DashboardStatDao
import dev.pschmitt.netboxandchill.data.db.DashboardStatEntity
import dev.pschmitt.netboxandchill.data.db.ObjectChangeDao
import dev.pschmitt.netboxandchill.data.db.ObjectChangeEntity
import dev.pschmitt.netboxandchill.data.schema.NetBoxRef
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Cache-first data for the dashboard/home screen (NBC-9): NetBox's changelog, the signed-in
 * user's bookmarks, and a handful of simple object-count stat tiles. Mirrors
 * [GenericObjectRepository]'s cache-first shape (Room-backed Flow reads, refresh as a best-effort
 * background update) rather than a network-only screen.
 *
 * "NetBox news" (also asked for in the original NBC-9 ask) is deliberately out of scope - no API
 * source for it was found, see TODO.md.
 */
@Singleton
class DashboardRepository
@Inject
constructor(
    private val api: GenericNetBoxApi,
    private val bookmarkDao: BookmarkDao,
    private val objectChangeDao: ObjectChangeDao,
    private val statDao: DashboardStatDao,
) {
    fun observeBookmarks(): Flow<List<BookmarkEntity>> = bookmarkDao.observeAll()

    fun observeChangelog(): Flow<List<ObjectChangeEntity>> = objectChangeDao.observeAll()

    fun observeStats(): Flow<List<DashboardStatEntity>> = statDao.observeAll()

    /** Refreshes all three widgets independently - one being unreachable (e.g. bookmarks on a
     * pre-3.5 NetBox instance) shouldn't blank out the others; the first failure encountered (if
     * any) is surfaced so the UI can still show a "sync failed" message. */
    suspend fun refresh(): Result<Unit> {
        val failures =
            listOfNotNull(
                refreshBookmarks().exceptionOrNull(),
                refreshChangelog().exceptionOrNull(),
                refreshStats().exceptionOrNull(),
            )
        return if (failures.isEmpty()) Result.success(Unit) else Result.failure(failures.first())
    }

    suspend fun refreshBookmarks(): Result<Int> = runCatching {
        val page =
            api.listObjects("api/extras/bookmarks/", mapOf("limit" to "50", "ordering" to "-created"))
        val entities = page.results.mapNotNull { it.toBookmarkEntity() }
        bookmarkDao.replaceAll(entities)
        entities.size
    }

    suspend fun refreshChangelog(): Result<Int> = runCatching {
        val page =
            api.listObjects("api/core/object-changes/", mapOf("limit" to "25", "ordering" to "-time"))
        val entities = page.results.mapNotNull { it.toObjectChangeEntity() }
        objectChangeDao.replaceAll(entities)
        entities.size
    }

    suspend fun refreshStats(): Result<Int> = runCatching {
        val entities =
            STAT_ENDPOINTS.mapNotNull { (endpointPath, label) ->
                runCatching { api.listObjects(endpointPath, mapOf("limit" to "1")).count }
                    .getOrNull()
                    ?.let { count -> DashboardStatEntity(endpointPath, label, count, System.currentTimeMillis()) }
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

    private companion object {
        // Kept small and cheap (`?limit=1`, only `count` is read, no full sync) - "a handful of
        // key models," not an exhaustive sweep of NetBox's data model. Picked models this app
        // already deals with elsewhere (typed Device/DeviceType caches from NBC-1/NBC-3).
        val STAT_ENDPOINTS =
            listOf(
                "api/dcim/devices/" to "Devices",
                "api/dcim/device-types/" to "Device Types",
                "api/dcim/sites/" to "Sites",
                "api/dcim/racks/" to "Racks",
            )
    }
}
