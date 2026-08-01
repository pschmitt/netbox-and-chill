package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.api.GenericNetBoxApi
import dev.pschmitt.netboxandchill.data.db.DeviceDao
import dev.pschmitt.netboxandchill.data.db.DeviceEntity
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectDao
import dev.pschmitt.netboxandchill.data.db.NetBoxObjectEntity
import dev.pschmitt.netboxandchill.data.db.RecentVisitEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import timber.log.Timber

/** A single global-search hit - just enough to render a result row and navigate into NBC-6's
 * generic detail screen (`Route.Generic(endpointPath, id)`), not a full cached object. */
data class SearchHit(val endpointPath: String, val id: Int, val display: String, val secondaryLine: String?)

fun recentVisitsToSearchHits(visits: List<RecentVisitEntity>): List<SearchHit> =
    visits.map { visit ->
        SearchHit(
            endpointPath = visit.endpointPath,
            id = visit.id,
            display = visit.display,
            secondaryLine = visit.secondaryLine,
        )
    }

/**
 * Cross-model search (NBC-13). **Cache-first**, matching every other repository in this app
 * (`AGENTS.md`: "reads come from Room, writes/refreshes come from the API") - this app's whole
 * point is working with no connectivity, so a search that only worked online would be a real
 * regression, not a first-pass shortcut. [observeCached] reads instantly from Room - across every
 * endpoint in `netbox_objects`, plus the typed `devices` table NBC-6 deliberately kept separate -
 * and works fully offline. [refresh] is a best-effort network pass that both broadens what's
 * findable right now *and* upserts hits back into `netbox_objects` via
 * [GenericObjectRepository.cacheSearchResults], so they're cached for next time too - a *refresh*
 * on top of the cache, not a parallel live-only path.
 *
 * NetBox has no global-search REST endpoint to call into for the refresh step - confirmed against
 * the real instance (netbox.brkn.lol, NetBox 4.5): `GET /api/extras/` lists no `search` key, `GET
 * /api/extras/search/` 404s, and the full `/api/schema/` OpenAPI document has zero paths
 * containing "search". What *does* work, also confirmed live: NetBox's per-model list endpoints
 * accept a free-text `?q=<term>` filter - used here as the refresh mechanism.
 */
@Singleton
class GlobalSearchRepository
@Inject
constructor(
    private val api: GenericNetBoxApi,
    private val netBoxObjectDao: NetBoxObjectDao,
    private val deviceDao: DeviceDao,
    private val genericObjectRepository: GenericObjectRepository,
) {

    /** Instant, offline-capable read - the primary result source, not scoped to
     * [BASELINE_ENDPOINT_PATHS]: anything ever cached under any endpoint is findable offline. */
    fun observeCached(queryText: String, limitPerSource: Int = 50): Flow<List<SearchHit>> =
        netBoxObjectDao.searchAll(queryText, limitPerSource).let { genericRows ->
            val genericHits = genericRows.map { rows -> rows.map { it.toSearchHit() } }
            val directDeviceHits =
                deviceDao.search(queryText).map { rows -> rows.take(limitPerSource).map { it.toSearchHit() } }
            val matchingDeviceTypes =
                genericRows.map { rows ->
                    rows
                        .filter { it.endpointPath == DEVICE_TYPES_ENDPOINT_PATH }
                        .associate { it.id to it.display }
                }
            val devicesOfMatchingTypes =
                combine(matchingDeviceTypes, deviceDao.observeAll()) { typeLabels, devices ->
                    devices
                        .filter { it.deviceTypeId in typeLabels.keys }
                        .map { device ->
                            device.toSearchHit(
                                secondaryLine =
                                    "Device type: ${typeLabels[device.deviceTypeId] ?: "matching type"}"
                            )
                        }
                }
            combine(genericHits, directDeviceHits, devicesOfMatchingTypes) { generic, direct, recursive ->
                generic + direct + recursive
            }
        }

    /** Best-effort live refresh: fans [endpointPaths] out in parallel via `?q=`, upserting
     * successful hits into `netbox_objects` so [observeCached] reflects them immediately and
     * they're offline-findable from now on. Devices are deliberately skipped here - `DeviceDao`'s
     * cache already gets a full periodic sync via `DeviceRepository`/`SyncWorker`, so it's already
     * comprehensive without a redundant `?q=` round trip. A single model failing (unreachable,
     * doesn't support `q`, offline entirely, ...) is logged and skipped rather than failing the
     * whole refresh, mirroring [DirectoryRepository.refresh]'s per-app `runCatching` - the caller
     * already has [observeCached]'s answer regardless of whether this succeeds. */
    suspend fun refresh(queryText: String, endpointPaths: List<String>, limitPerModel: Int = 15) {
        coroutineScope {
            endpointPaths
                .filter { it != DEVICES_ENDPOINT_PATH }
                .map { endpointPath -> async { refreshOne(endpointPath, queryText, limitPerModel) } }
                .awaitAll()
        }
    }

    private suspend fun refreshOne(endpointPath: String, queryText: String, limitPerModel: Int) {
        try {
            val results =
                api.listObjects(
                        endpointPath,
                        mapOf("q" to queryText, "limit" to limitPerModel.toString()),
                    )
                    .results
            genericObjectRepository.cacheSearchResults(endpointPath, results)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.w(error, "Global search refresh failed for %s", endpointPath)
        }
    }

    private fun NetBoxObjectEntity.toSearchHit() = SearchHit(endpointPath, id, display, secondaryLine)

    private fun DeviceEntity.toSearchHit(secondaryLine: String? = statusLabel ?: siteName) =
        SearchHit(endpointPath = DEVICES_ENDPOINT_PATH, id = id, display = name, secondaryLine = secondaryLine)

    companion object {
        private const val DEVICES_ENDPOINT_PATH = "api/dcim/devices/"
        private const val DEVICE_TYPES_ENDPOINT_PATH = "api/dcim/device-types/"

        // Baseline model set for the network refresh + result labeling - GlobalSearchViewModel
        // unions this with the user's pinned model paths so anything explicitly starred in the
        // sidebar is searchable too, not just this baseline. Kept short so one search doesn't fan
        // out to dozens of parallel requests; the *cached* read side has no such limit.
        val BASELINE_ENDPOINT_PATHS =
            listOf(
                DEVICES_ENDPOINT_PATH,
                DEVICE_TYPES_ENDPOINT_PATH,
                "api/dcim/sites/",
                "api/dcim/racks/",
                "api/ipam/ip-addresses/",
                "api/ipam/prefixes/",
                "api/circuits/circuits/",
                "api/virtualization/virtual-machines/",
                "api/tenancy/tenants/",
            )
    }
}

/** Ranks cached hits so exact and prefix matches are useful before alphabetical tie-breaking. */
fun rankSearchHits(queryText: String, hits: List<SearchHit>): List<SearchHit> {
    val query = queryText.trim().lowercase()
    if (query.isBlank()) return hits.distinctBy { it.endpointPath to it.id }
    return hits
        .distinctBy { it.endpointPath to it.id }
        .sortedWith(
            compareByDescending<SearchHit> { hit -> searchRelevance(query, hit) }
                .thenBy { it.display.lowercase() }
                .thenBy { it.endpointPath }
                .thenBy { it.id }
        )
}

private fun searchRelevance(query: String, hit: SearchHit): Int {
    val display = hit.display.trim().lowercase()
    val secondary = hit.secondaryLine.orEmpty().trim().lowercase()
    return when {
        display == query -> 400
        display.startsWith(query) -> 300
        display.contains(query) -> 200
        secondary == query -> 150
        secondary.startsWith(query) -> 125
        secondary.contains(query) -> 100
        else -> 0
    }
}
