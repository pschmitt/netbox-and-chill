package dev.pschmitt.nyetbox.data.repository

import dev.pschmitt.nyetbox.data.api.GenericNetBoxApi
import dev.pschmitt.nyetbox.data.db.NetBoxObjectDao
import dev.pschmitt.nyetbox.data.db.NetBoxObjectEntity
import dev.pschmitt.nyetbox.data.schema.jsonInt
import dev.pschmitt.nyetbox.data.schema.jsonString
import dev.pschmitt.nyetbox.sync.SyncIssueReporter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

private const val CONTENT_TYPES_ENDPOINT_PATH = "api/contenttypes/content-types/"
private val JSON_FORM_KEYS = setOf("default", "related_object_filter")

/** Cache-first list/detail access for any NetBox object type, keyed by its endpoint path. */
data class CreateChoice(
    val value: String,
    val label: String,
    val frontImageUrl: String? = null,
    val rearImageUrl: String? = null,
    val searchFields: Map<String, String> = emptyMap(),
)

data class CreateFieldDefinition(
    val key: String,
    val label: String,
    val type: String,
    val required: Boolean,
    val defaultValue: JsonElement?,
    val choices: List<CreateChoice>,
    val referenceEndpointPath: String?,
    val customFieldName: String? = null,
    val markdown: Boolean = false,
    val multiple: Boolean = false,
    val helpText: String? = null,
)

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
        val source =
            if (query.isBlank()) dao.observeAll(endpointPath) else dao.search(endpointPath, query)
        if (filterKey == null || filterValue == null) return source
        return source.map { objects ->
            objects.filter { it.matchesRelation(json, filterKey, filterValue) }
        }
    }

    fun observeObject(endpointPath: String, id: Int): Flow<NetBoxObjectEntity?> =
        dao.observeById(endpointPath, id)

    fun observeAllObjects(): Flow<List<NetBoxObjectEntity>> = dao.observeAllObjects()

    suspend fun refreshObject(endpointPath: String, id: Int): Result<NetBoxObjectEntity> =
        runCatching {
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
                            Timber.w(
                                error,
                                "Skipping non-object response from %s during sync",
                                endpointPath,
                            )
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

    suspend fun cachedObjects(endpointPath: String): List<NetBoxObjectEntity> =
        dao.getAll(endpointPath)

    /**
     * Returns content-type choices from the cache for metadata such as custom-field `object_types`.
     * This deliberately does not query the server: an empty cache falls back to the form's
     * free-form JSON/list input, preserving offline-first behavior.
     */
    suspend fun cachedContentTypeChoices(): List<CreateChoice> =
        cachedObjects(CONTENT_TYPES_ENDPOINT_PATH)
            .mapNotNull { entity ->
                val objectJson =
                    runCatching {
                        json.decodeFromString(JsonObject.serializer(), entity.json)
                    }
                        .getOrNull() ?: return@mapNotNull null
                val appLabel = (objectJson["app_label"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val model = (objectJson["model"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                if (appLabel.isBlank() || model.isBlank()) return@mapNotNull null
                CreateChoice(
                    value = "$appLabel.$model",
                    label =
                        (objectJson["display"] as? JsonPrimitive)?.contentOrNull
                            ?: "$appLabel > $model",
                )
            }
            .distinctBy(CreateChoice::value)
            .sortedBy { it.label.lowercase() }

    suspend fun cachedMediaAttachments(): List<OfflineAttachment> =
        dao.getAll().flatMap { entity ->
            val objectJson =
                runCatching { json.decodeFromString(JsonObject.serializer(), entity.json) }
                    .getOrNull() ?: return@flatMap emptyList()
            objectJson.mediaAttachments()
        }

    /**
     * Upserts arbitrary already-fetched objects (e.g. [GlobalSearchRepository]'s `?q=` hits) into
     * the same cache [observeObjects] reads from, so a live search result is also offline-findable
     * from then on - reuses the same [toEntity] mapping [syncAll] uses, not a separate path.
     */
    suspend fun cacheSearchResults(endpointPath: String, objects: List<JsonObject>) {
        if (objects.isEmpty()) return
        dao.upsertAll(objects.map { it.toEntity(endpointPath) })
    }

    /** Writes a locally merged object into the same cache used by the detail screen. */
    suspend fun cacheLocalObject(endpointPath: String, objectJson: JsonObject) {
        dao.upsert(objectJson.toEntity(endpointPath))
    }

    suspend fun removeCachedObject(endpointPath: String, id: Int) {
        dao.delete(endpointPath, id)
    }

    suspend fun createFieldDefinitions(endpointPath: String): Result<List<CreateFieldDefinition>> =
        runCatching {
            val options = api.getObjectOptions(endpointPath)
            parseCreateFieldDefinitions(options)
        }

    suspend fun createObject(endpointPath: String, body: JsonObject): Result<JsonObject> =
        runCatching {
            api.createObject(endpointPath, body).also { cacheLocalObject(endpointPath, it) }
        }

    private fun JsonObject.toEntity(endpointPath: String): NetBoxObjectEntity {
        val id = jsonInt("id") ?: error("NetBox object at $endpointPath has no id")
        val display = jsonString("display") ?: jsonString("name") ?: "#$id"
        val secondaryLine =
            (this["status"] as? JsonObject)?.jsonString("label")
                ?: jsonString("description")?.takeIf { it.isNotBlank() }
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

internal fun parseCreateFieldDefinitions(response: JsonObject): List<CreateFieldDefinition> {
    val actions = response["actions"] as? JsonObject ?: return emptyList()
    // NetBox normally exposes POST on a collection OPTIONS response. A few deployments/proxies
    // only advertise the equivalent PUT serializer, however; its writable field metadata is still
    // useful for building the form, and the eventual POST will surface any actual permission
    // restriction instead of presenting a misleading empty screen.
    val postFields = (actions["POST"] ?: actions["PUT"]) as? JsonObject ?: return emptyList()
    return postFields.mapNotNull { (key, element) ->
        val definition = element as? JsonObject ?: return@mapNotNull null
        val rawType =
            (definition["type"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val type = if (rawType == "field" && key in JSON_FORM_KEYS) "json" else rawType
        val readOnly = (definition["read_only"] as? JsonPrimitive)?.booleanOrNull ?: false
        if (
            readOnly ||
                key in setOf("id", "url", "display", "display_url", "created", "last_updated")
        ) {
            return@mapNotNull null
        }
        val label = (definition["label"] as? JsonPrimitive)?.contentOrNull ?: key
        val required = (definition["required"] as? JsonPrimitive)?.booleanOrNull ?: false
        val choices =
            (definition["choices"] as? JsonArray).orEmpty().mapNotNull { choice ->
                val obj = choice as? JsonObject ?: return@mapNotNull null
                val value =
                    (obj["value"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val choiceLabel =
                    (obj["display_name"] as? JsonPrimitive)?.contentOrNull
                        ?: (obj["display"] as? JsonPrimitive)?.contentOrNull
                        ?: value
                CreateChoice(value, choiceLabel)
            }
        CreateFieldDefinition(
            key = key,
            label = label,
            type = type,
            required = required,
            defaultValue = definition["default"]?.takeUnless { it is JsonNull },
            choices = choices,
            referenceEndpointPath = createReferenceEndpoint(key, type),
            markdown = type in setOf("longtext", "markdown"),
            multiple =
                key == "object_types" ||
                    type in
                        setOf(
                            "multiple choice",
                            "multiple_choice",
                            "multiple-choice",
                            "multiple object",
                            "multiple_object",
                            "multiobject",
                            "multiselect",
                        ),
            helpText = (definition["help_text"] as? JsonPrimitive)?.contentOrNull,
        )
    }
}

/**
 * Minimal first-class forms for the two typed workflows if a restrictive API token cannot read
 * OPTIONS.
 */
internal fun fallbackCreateFieldDefinitions(endpointPath: String): List<CreateFieldDefinition> =
    when (endpointPath) {
        "api/dcim/devices/" ->
            listOf(
                CreateFieldDefinition("name", "Name", "string", false, null, emptyList(), null),
                CreateFieldDefinition(
                    "device_type",
                    "Device type",
                    "nested object",
                    true,
                    null,
                    emptyList(),
                    "api/dcim/device-types/",
                ),
                CreateFieldDefinition(
                    "role",
                    "Role",
                    "nested object",
                    true,
                    null,
                    emptyList(),
                    "api/dcim/device-roles/",
                ),
                CreateFieldDefinition(
                    "site",
                    "Site",
                    "nested object",
                    true,
                    null,
                    emptyList(),
                    "api/dcim/sites/",
                ),
                CreateFieldDefinition(
                    "serial",
                    "Serial number",
                    "string",
                    false,
                    null,
                    emptyList(),
                    null,
                ),
                CreateFieldDefinition(
                    "asset_tag",
                    "Asset tag",
                    "string",
                    false,
                    null,
                    emptyList(),
                    null,
                ),
                CreateFieldDefinition(
                    "status",
                    "Status",
                    "field",
                    false,
                    JsonPrimitive("active"),
                    emptyList(),
                    null,
                ),
                CreateFieldDefinition(
                    "comments",
                    "Comments",
                    "string",
                    false,
                    null,
                    emptyList(),
                    null,
                ),
            )
        "api/dcim/device-types/" ->
            listOf(
                CreateFieldDefinition("model", "Model", "string", true, null, emptyList(), null),
                CreateFieldDefinition(
                    "manufacturer",
                    "Manufacturer",
                    "nested object",
                    true,
                    null,
                    emptyList(),
                    "api/dcim/manufacturers/",
                ),
                CreateFieldDefinition("slug", "Slug", "slug", true, null, emptyList(), null),
                CreateFieldDefinition(
                    "description",
                    "Description",
                    "string",
                    false,
                    null,
                    emptyList(),
                    null,
                ),
            )
        in DEVICE_COMPONENT_ENDPOINTS ->
            listOf(
                CreateFieldDefinition(
                    "device",
                    "Device",
                    "nested object",
                    true,
                    null,
                    emptyList(),
                    "api/dcim/devices/",
                ),
                CreateFieldDefinition("name", "Name", "string", true, null, emptyList(), null),
                CreateFieldDefinition("label", "Label", "string", false, null, emptyList(), null),
                CreateFieldDefinition("type", "Type", "string", false, null, emptyList(), null),
            )
        else -> emptyList()
    }

private val DEVICE_COMPONENT_ENDPOINTS =
    setOf(
        "api/dcim/interfaces/",
        "api/dcim/front-ports/",
        "api/dcim/rear-ports/",
        "api/dcim/console-ports/",
        "api/dcim/power-ports/",
        "api/dcim/power-outlets/",
        "api/dcim/module-bays/",
        "api/dcim/device-bays/",
        "api/dcim/inventory-items/",
    )

private fun createReferenceEndpoint(key: String, type: String): String? {
    if (type != "nested object") return null
    return mapOf(
        "device" to "api/dcim/devices/",
        "device_type" to "api/dcim/device-types/",
        "manufacturer" to "api/dcim/manufacturers/",
        "site" to "api/dcim/sites/",
        "location" to "api/dcim/locations/",
        "rack" to "api/dcim/racks/",
        "role" to "api/dcim/device-roles/",
        "device_role" to "api/dcim/device-roles/",
        "platform" to "api/dcim/platforms/",
        "tenant" to "api/tenancy/tenants/",
        "cluster" to "api/virtualization/clusters/",
        "owner" to "api/tenancy/contacts/",
        "choice_set" to "api/extras/custom-field-choice-sets/",
    )[key]
}

internal fun buildCreateBody(
    fields: List<CreateFieldDefinition>,
    values: Map<String, String>,
): Result<JsonObject> {
    val missing =
        fields.filter { it.required && values[it.key].orEmpty().isBlank() }.map { it.label }
    if (missing.isNotEmpty())
        return Result.failure(IllegalArgumentException("Required: ${missing.joinToString(", ")}"))
    val body = linkedMapOf<String, JsonElement>()
    fields.forEach { field ->
        val value = values[field.key]?.trim().orEmpty()
        if (value.isEmpty()) return@forEach
        val jsonValue =
            when {
                field.multiple ->
                    runCatching { Json.decodeFromString(JsonArray.serializer(), value) }
                        .getOrElse {
                            JsonArray(
                                value
                                    .split(',')
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .map(::JsonPrimitive)
                            )
                        }
                field.type == "json" -> runCatching {
                        Json.decodeFromString(JsonElement.serializer(), value)
                    }
                        .getOrElse {
                            return Result.failure(
                                IllegalArgumentException("${field.label} must contain valid JSON")
                            )
                        }
                field.type == "boolean" -> JsonPrimitive(value.toBooleanStrictOrNull() ?: false)
                field.type == "integer" ->
                    value.toIntOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(value)
                field.type in setOf("decimal", "float") ->
                    value.toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(value)
                field.type in setOf("nested object", "object") ->
                    value.toIntOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(value)
                else -> JsonPrimitive(value)
            }
        if (field.customFieldName != null) {
            val customFields =
                (body["custom_fields"] as? JsonObject)?.toMutableMap() ?: linkedMapOf()
            customFields[field.customFieldName] = jsonValue
            body["custom_fields"] = JsonObject(customFields)
        } else {
            body[field.key] = jsonValue
        }
    }
    return Result.success(JsonObject(body))
}

private fun NetBoxObjectEntity.matchesRelation(
    parser: Json,
    relationKey: String,
    expectedId: Int,
): Boolean {
    val objectJson =
        runCatching { parser.decodeFromString(JsonObject.serializer(), json) }.getOrNull()
            ?: return false
    return when (val relation = objectJson[relationKey]) {
        is JsonObject -> relation["id"]?.jsonPrimitive?.intOrNull == expectedId
        is JsonArray ->
            relation.any { (it as? JsonObject)?.get("id")?.jsonPrimitive?.intOrNull == expectedId }
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
                    attachments.putIfAbsent(
                        value,
                        OfflineAttachment(value, filename.ifBlank { "attachment" }),
                    )
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
