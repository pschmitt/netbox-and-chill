package dev.pschmitt.nyetbox.data.repository

import dev.pschmitt.nyetbox.data.api.GenericNetBoxApi
import dev.pschmitt.nyetbox.data.db.CableTraceDao
import dev.pschmitt.nyetbox.data.db.CableTraceEntity
import dev.pschmitt.nyetbox.data.schema.NetBoxRef
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Cache-first access to NetBox's cable-path trace endpoints (`api/dcim/<component>/<id>/trace/`),
 * used to render a cable's "Trace" tab. The trace lives on a *termination* (interface, console
 * port, ...), not the cable itself, so [refresh]/[observe] take the termination's endpointPath+id
 * rather than the cable's - see [cableTraceStartTarget] for resolving that termination from a
 * cable's own JSON.
 */
@Singleton
class CableTraceRepository
@Inject
constructor(private val api: GenericNetBoxApi, private val dao: CableTraceDao) {
    private val parser = Json { ignoreUnknownKeys = true }

    fun observe(endpointPath: String, objectId: Int): Flow<List<CableTraceEntity>> =
        dao.observe(endpointPath, objectId)

    /** Refreshes the cached trace while leaving the existing Room snapshot intact on failure. */
    suspend fun refresh(endpointPath: String, objectId: Int): Result<Int> = runCatching {
        val segments = api.getJsonArray("$endpointPath$objectId/trace/")
        val entities =
            segments.mapIndexedNotNull { index, element ->
                (element as? JsonArray)?.let {
                    CableTraceEntity(
                        traceEndpointPath = endpointPath,
                        traceObjectId = objectId,
                        segmentIndex = index,
                        json = parser.encodeToString(JsonArray.serializer(), it),
                        syncedAt = System.currentTimeMillis(),
                    )
                }
            }
        dao.clear(endpointPath, objectId)
        dao.upsertAll(entities)
        entities.size
    }
}

/**
 * Front/rear ports are the two sides of a passthrough panel (patch panel, KVM, USB hub, ...) - confirmed
 * against a live NetBox 4.5 instance, `/api/dcim/front-ports/<id>/trace/` and
 * `/api/dcim/rear-ports/<id>/trace/` both 404 regardless of the port's own cabling, unlike every
 * other termination type (interface, console/power port, circuit termination, ...). A cable landing
 * on one of these has to be traced from its *other* termination instead.
 */
private val UNTRACEABLE_TERMINATION_TYPES = setOf("dcim.frontport", "dcim.rearport")

/**
 * The termination to trace from - whichever of the cable's first A-side or first B-side termination
 * actually supports NetBox's trace endpoint (see [UNTRACEABLE_TERMINATION_TYPES]), preferring the A
 * side when both do. Returns the termination's own (endpointPath, id), which is what NetBox's trace
 * endpoint is keyed on - or null if neither side is traceable (e.g. a pure patch-panel-to-patch-panel
 * cable), which is a legitimate "nothing to show" rather than a sync failure.
 *
 * Each entry in `a_terminations`/`b_terminations` is a `GenericObjectSerializer`-shaped wrapper -
 * `{object_type, object_id, object}` - where `object` is the actual nested termination (interface,
 * console port, ...). Confirmed against a live NetBox 4.x instance: NOT the standalone
 * `CableTerminationSerializer` shape (`termination_type`/`termination_id`/`termination`) that
 * `/api/dcim/cable-terminations/` uses - that shape doesn't appear here.
 */
internal fun cableTraceStartTarget(cable: JsonObject): Pair<String, Int>? {
    val candidates =
        listOfNotNull(
            (cable["a_terminations"] as? JsonArray)?.firstOrNull() as? JsonObject,
            (cable["b_terminations"] as? JsonArray)?.firstOrNull() as? JsonObject,
        )
    val termination =
        candidates.firstOrNull { wrapper ->
            (wrapper["object_type"] as? JsonPrimitive)?.contentOrNull !in
                UNTRACEABLE_TERMINATION_TYPES
        } ?: return null
    val obj = termination["object"] as? JsonObject ?: return null
    val id = (obj["id"] as? JsonPrimitive)?.intOrNull ?: return null
    val url = (obj["url"] as? JsonPrimitive)?.contentOrNull ?: return null
    val endpointPath = NetBoxRef.endpointFromDetailUrl(url) ?: return null
    return endpointPath to id
}
