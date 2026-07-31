package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.api.GenericNetBoxApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/** A single global-search hit - just enough to render a result row and navigate into NBC-6's
 * generic detail screen (`Route.Generic(endpointPath, id)`), not a full cached object. */
data class SearchHit(val endpointPath: String, val id: Int, val display: String, val secondaryLine: String?)

/**
 * Cross-model search (NBC-13). NetBox has no global-search REST endpoint to call into - confirmed
 * against the real instance (netbox.brkn.lol, NetBox 4.5): `GET /api/extras/` lists no `search`
 * key, `GET /api/extras/search/` 404s, and the full `/api/schema/` OpenAPI document has zero paths
 * containing "search". What *does* work, also confirmed live: NetBox's per-model list endpoints
 * accept a free-text `?q=<term>` filter. So this fans a single search term out across a curated
 * set of endpoint paths in parallel via the same schema-free [GenericNetBoxApi.listObjects] the
 * generic list/detail screens already use, and merges the results - client-side fan-out instead of
 * a server-side global search.
 *
 * Deliberately not cache-backed: unlike [GenericObjectRepository], results here are transient (not
 * upserted into `NetBoxObjectEntity`) since the point is a live merge across many models for one
 * search term, not another sync path - tapping a result still lands on the normal cache-first
 * generic detail screen.
 */
@Singleton
class GlobalSearchRepository @Inject constructor(private val api: GenericNetBoxApi) {

    /** Runs [endpointPaths] in parallel, each filtered by `?q=<queryText>`. A single model failing
     * (unreachable, doesn't support `q`, ...) is logged and skipped rather than failing the whole
     * search - mirrors [DirectoryRepository.refresh]'s per-app `runCatching`. */
    suspend fun search(queryText: String, endpointPaths: List<String>, limitPerModel: Int = 15): List<SearchHit> =
        coroutineScope {
            endpointPaths
                .map { endpointPath -> async { searchOne(endpointPath, queryText, limitPerModel) } }
                .awaitAll()
                .flatten()
        }

    private suspend fun searchOne(endpointPath: String, queryText: String, limitPerModel: Int): List<SearchHit> =
        runCatching {
                api
                    .listObjects(endpointPath, mapOf("q" to queryText, "limit" to limitPerModel.toString()))
                    .results
                    .map { it.toSearchHit(endpointPath) }
            }
            .onFailure { Timber.w(it, "Global search failed for %s", endpointPath) }
            .getOrDefault(emptyList())

    private fun JsonObject.toSearchHit(endpointPath: String): SearchHit {
        val id = this["id"]?.jsonPrimitive?.intOrNull ?: 0
        val display =
            this["display"]?.jsonPrimitive?.contentOrNull ?: this["name"]?.jsonPrimitive?.contentOrNull ?: "#$id"
        val secondaryLine =
            (this["status"] as? JsonObject)?.get("label")?.jsonPrimitive?.contentOrNull
                ?: this["description"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        return SearchHit(endpointPath = endpointPath, id = id, display = display, secondaryLine = secondaryLine)
    }

    companion object {
        // The TODO's own suggested set (devices/sites/racks/ip-addresses/circuits) plus a few
        // equally common models - kept short so one search doesn't fan out to dozens of parallel
        // requests. GlobalSearchViewModel unions this with the user's pinned model paths so
        // anything explicitly starred in the sidebar is searchable too, not just this baseline.
        val BASELINE_ENDPOINT_PATHS =
            listOf(
                "api/dcim/devices/",
                "api/dcim/device-types/",
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
