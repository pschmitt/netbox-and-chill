package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.api.GenericNetBoxApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Fetches an object's Journal entries (`/api/extras/journal-entries/`) - NetBox's free-form
 * timestamped notes attached to any object via a GenericForeignKey, distinct from the
 * auto-generated changelog.
 *
 * Filtering requires the target's content type as an `app_label.model` string (e.g.
 * `"dcim.device"`), which NetBox doesn't expose on the object itself. Rather than hardcoding every
 * model's content type, this resolves it by matching our discovered `endpointPath` (e.g.
 * `"api/dcim/devices/"`) against the real, server-provided choice list for the
 * `assigned_object_type` field (from journal-entries' own `OPTIONS` response) - the DRF router's
 * plural URL segment ("devices") is turned into candidate singular forms and matched against the
 * app's real content-type models ("device").
 */
@Singleton
class JournalEntryRepository @Inject constructor(private val api: GenericNetBoxApi) {

    private var contentTypeChoicesCache: List<String>? = null

    suspend fun fetchJournalEntries(endpointPath: String, objectId: Int): Result<List<JsonObject>> = runCatching {
        val assignedObjectType = resolveAssignedObjectType(endpointPath) ?: return@runCatching emptyList()
        api
            .listObjects(
                "api/extras/journal-entries/",
                mapOf(
                    "assigned_object_type" to assignedObjectType,
                    "assigned_object_id" to objectId.toString(),
                    "ordering" to "-created",
                ),
            )
            .results
    }

    private suspend fun resolveAssignedObjectType(endpointPath: String): String? {
        val segments = endpointPath.trim('/').split('/')
        if (segments.size < 3) return null
        // Plugin models nest one level deeper (api/plugins/<plugin>/<model>/) - the plugin's own
        // key is the closer (if imperfect) proxy for its content-type app_label than "plugins".
        val appKey = if (segments[1] == "plugins" && segments.size >= 4) segments[2] else segments[1]
        val modelKey = segments.last()
        val normalized = modelKey.replace("-", "").replace("_", "").lowercase()
        val candidates =
            buildSet {
                add(normalized)
                add(normalized.removeSuffix("s"))
                if (normalized.endsWith("es")) add(normalized.dropLast(2))
                if (normalized.endsWith("ies")) add(normalized.dropLast(3) + "y")
            }
        return contentTypeChoices().firstOrNull { choice ->
            val parts = choice.split(".", limit = 2)
            parts.size == 2 && parts[0] == appKey && parts[1] in candidates
        }
    }

    private suspend fun contentTypeChoices(): List<String> =
        contentTypeChoicesCache
            ?: runCatching {
                    val options = api.getJournalEntryOptions()
                    options["actions"]
                        ?.jsonObject
                        ?.get("PUT")
                        ?.jsonObject
                        ?.get("assigned_object_type")
                        ?.jsonObject
                        ?.get("choices")
                        ?.jsonArray
                        ?.mapNotNull { (it as? JsonObject)?.get("value")?.jsonPrimitive?.contentOrNull }
                        .orEmpty()
                }
                .getOrDefault(emptyList())
                .also { contentTypeChoicesCache = it }
}
