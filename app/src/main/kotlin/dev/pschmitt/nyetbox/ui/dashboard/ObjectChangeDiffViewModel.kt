package dev.pschmitt.nyetbox.ui.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.nyetbox.data.repository.CustomFieldDefinition
import dev.pschmitt.nyetbox.data.repository.CustomFieldRepository
import dev.pschmitt.nyetbox.data.repository.DashboardRepository
import dev.pschmitt.nyetbox.data.repository.DeviceRepository
import dev.pschmitt.nyetbox.data.repository.DeviceTypeRepository
import dev.pschmitt.nyetbox.data.repository.GenericObjectRepository
import dev.pschmitt.nyetbox.data.schema.Humanize
import dev.pschmitt.nyetbox.data.schema.NetBoxRef
import dev.pschmitt.nyetbox.ui.navigation.Route
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

/**
 * One field that differs between a changelog entry's before/after snapshots - either side may be
 * null: absent entirely for a create (no "before"), or a delete (no "after"), or genuinely absent
 * from that particular snapshot (e.g. a field added by a later NetBox version).
 */
data class DiffRow(
    val label: String,
    val before: String?,
    val after: String?,
    val section: String? = null,
    val markdown: Boolean = false,
    val fieldKey: String? = null,
    val beforeReference: DiffReference? = null,
    val afterReference: DiffReference? = null,
)

data class DiffReference(val endpointPath: String, val id: Int)

data class ChangeImage(val label: String, val url: String)

data class ObjectChangeDiffUi(
    val objectRepr: String,
    val actionLabel: String,
    val userDisplay: String,
    val time: String,
    val rows: List<DiffRow>,
    val targetEndpointPath: String? = null,
    val targetId: Int? = null,
    val deviceTypeImages: List<ChangeImage> = emptyList(),
)

@HiltViewModel
class ObjectChangeDiffViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DashboardRepository,
    private val customFieldRepository: CustomFieldRepository,
    private val genericObjectRepository: GenericObjectRepository,
    private val deviceRepository: DeviceRepository,
    private val deviceTypeRepository: DeviceTypeRepository,
) : ViewModel() {

    private val route: Route.ObjectChangeDiff = savedStateHandle.toRoute()

    private val _diff = MutableStateFlow<ObjectChangeDiffUi?>(null)
    val diff: StateFlow<ObjectChangeDiffUi?> = _diff.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.fetchObjectChange(route.changeId)
            result.getOrNull()?.let {
                val definitions = customFieldRepository.observeDefinitions().first()
                _diff.value = it.toDiffUi(definitions)
            }
            result.exceptionOrNull()?.let {
                _errorMessage.value = it.message ?: "Couldn't load this change"
            }
            _isLoading.value = false
        }
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    private suspend fun JsonObject.toDiffUi(
        customFieldDefinitions: List<CustomFieldDefinition>
    ): ObjectChangeDiffUi {
        val pre = this["prechange_data"] as? JsonObject
        val post = this["postchange_data"] as? JsonObject
        val actionObj = this["action"] as? JsonObject
        val rawRows = buildDiffRows(pre, post, customFieldDefinitions)
        val cachedDisplays = mutableMapOf<String, Map<Int, String>>()
        val rows =
            resolveLinkedDiffRows(this, rawRows) { endpointPath, id ->
                cachedDisplays
                    .getOrPut(endpointPath) {
                        // Historical diffs stay cache-only: a missing relation never blocks on
                        // a network request and its raw ID remains visible.
                        genericObjectRepository.cachedObjects(endpointPath).associate {
                            it.id to it.display
                        }
                    }[id]
            }
        val target = this["changed_object"] as? JsonObject
        val targetEndpointPath =
            target?.get("url")?.jsonContentOrNull()?.let(NetBoxRef::endpointFromDetailUrl)
                ?: objectTypeEndpoint(this["changed_object_type"]?.jsonContentOrNull())
        val targetId =
            target?.get("id")?.jsonPrimitive?.intOrNull
                ?: this["changed_object_id"]?.jsonPrimitive?.intOrNull
        return ObjectChangeDiffUi(
            objectRepr = this["object_repr"]?.jsonContentOrNull() ?: "#" + route.changeId,
            actionLabel = actionObj?.get("label")?.jsonContentOrNull() ?: "Changed",
            userDisplay =
                (this["user"] as? JsonObject)?.get("display")?.jsonContentOrNull()
                    ?: this["user_name"]?.jsonContentOrNull()
                    ?: "Unknown",
            time = this["time"]?.jsonContentOrNull() ?: "",
            rows = rows,
            targetEndpointPath = targetEndpointPath,
            targetId = targetId,
            deviceTypeImages = cachedDeviceTypeImages(targetEndpointPath, targetId),
        )
    }

    private suspend fun cachedDeviceTypeImages(
        targetEndpointPath: String?,
        targetId: Int?,
    ): List<ChangeImage> {
        if (targetEndpointPath != DEVICES_ENDPOINT_PATH || targetId == null) return emptyList()
        val device = deviceRepository.observeDevice(targetId).first() ?: return emptyList()
        val deviceTypeId = device.deviceTypeId ?: return emptyList()
        val deviceType = deviceTypeRepository.observe(deviceTypeId).first() ?: return emptyList()
        return buildList {
            deviceType.frontImageUrl?.takeIf(String::isNotBlank)?.let { url ->
                add(ChangeImage("Front", url))
            }
            deviceType.rearImageUrl?.takeIf(String::isNotBlank)?.let { url ->
                add(ChangeImage("Rear", url))
            }
        }
    }
}

private const val DEVICES_ENDPOINT_PATH = "api/dcim/devices/"

private fun objectTypeEndpoint(objectType: String?): String? =
    when (objectType) {
        "dcim.device" -> DEVICES_ENDPOINT_PATH
        "dcim.devicetype",
        "dcim.device_type" -> "api/dcim/device-types/"
        "dcim.site" -> "api/dcim/sites/"
        "dcim.rack" -> "api/dcim/racks/"
        "ipam.ipaddress" -> "api/ipam/ip-addresses/"
        "tenancy.tenant" -> "api/tenancy/tenants/"
        "virtualization.virtualmachine" -> "api/virtualization/virtual-machines/"
        else -> null
    }

/**
 * Internal rather than private so a unit test can exercise the diffing logic directly, same pattern
 * as `GenericFieldRenderer.buildFieldRows`.
 */
internal fun buildDiffRows(
    pre: JsonObject?,
    post: JsonObject?,
    customFieldDefinitions: List<CustomFieldDefinition> = emptyList(),
): List<DiffRow> {
    val ordinaryKeys =
        (pre?.keys.orEmpty() + post?.keys.orEmpty())
            .toSet()
            .filterNot { it == "custom_fields" }
            .sorted()
    val ordinaryRows = ordinaryKeys.mapNotNull { key ->
        val before = pre?.get(key)?.diffString()
        val after = post?.get(key)?.diffString()
        if (before == after) null else DiffRow(Humanize.label(key), before, after, fieldKey = key)
    }

    val definitions = customFieldDefinitions.associateBy { it.name }
    val beforeCustomFields = pre?.get("custom_fields") as? JsonObject
    val afterCustomFields = post?.get("custom_fields") as? JsonObject
    val customKeys =
        (beforeCustomFields?.keys.orEmpty() + afterCustomFields?.keys.orEmpty()).toSet()
    val customRows =
        customKeys
            .sortedWith(
                Comparator { left, right ->
                    val leftDefinition = definitions[left]
                    val rightDefinition = definitions[right]
                    val leftGroup = leftDefinition?.group?.trim().orEmpty()
                    val rightGroup = rightDefinition?.group?.trim().orEmpty()
                    when {
                        leftGroup.isNotBlank() && rightGroup.isBlank() -> -1
                        leftGroup.isBlank() && rightGroup.isNotBlank() -> 1
                        else ->
                            String.CASE_INSENSITIVE_ORDER.compare(leftGroup, rightGroup).takeIf {
                                it != 0
                            }
                                ?: (leftDefinition?.weight ?: Int.MAX_VALUE)
                                    .compareTo(rightDefinition?.weight ?: Int.MAX_VALUE)
                                    .takeIf { it != 0 }
                                ?: String.CASE_INSENSITIVE_ORDER.compare(
                                    leftDefinition?.label ?: Humanize.label(left),
                                    rightDefinition?.label ?: Humanize.label(right),
                                )
                    }
                }
            )
            .mapNotNull { key ->
                val definition = definitions[key]
                val before = beforeCustomFields?.get(key)?.diffString(definition)
                val after = afterCustomFields?.get(key)?.diffString(definition)
                if (before == after) {
                    null
                } else {
                    DiffRow(
                        label =
                            definition?.label?.takeIf { it.isNotBlank() } ?: Humanize.label(key),
                        before = before,
                        after = after,
                        section =
                            definition?.group?.trim()?.takeIf { it.isNotBlank() }
                                ?: "Custom fields",
                        markdown = definition?.isMarkdown() == true,
                        fieldKey = "custom_fields.$key",
                    )
                }
            }
    return ordinaryRows + customRows
}

/**
 * Replaces cached foreign-key IDs in changelog rows with related-object display values. NetBox
 * object-change snapshots store relations as integers, unlike normal detail responses which include
 * id/url/display summaries. The resolver is a suspend lambda so this transformation is
 * straightforward to test without Room or Hilt.
 */
internal suspend fun resolveLinkedDiffRows(
    change: JsonObject,
    rows: List<DiffRow>,
    resolveDisplay: suspend (endpointPath: String, id: Int) -> String?,
): List<DiffRow> {
    val pre = change["prechange_data"] as? JsonObject
    val post = change["postchange_data"] as? JsonObject
    val objectType = change["changed_object_type"]?.jsonContentOrNull()
    return rows.map { row ->
        val fieldKey = row.fieldKey ?: return@map row
        val snapshotKey = fieldKey.snapshotKey()
        val endpointPath =
            referenceEndpointForDiffField(
                objectType,
                fieldKey,
                snapshotValue(pre, fieldKey, snapshotKey),
                snapshotValue(post, fieldKey, snapshotKey),
            ) ?: return@map row
        val beforeElement = snapshotValue(pre, fieldKey, snapshotKey)
        val afterElement = snapshotValue(post, fieldKey, snapshotKey)
        row.copy(
            before = resolveDiffReferenceValue(row.before, endpointPath, resolveDisplay),
            after = resolveDiffReferenceValue(row.after, endpointPath, resolveDisplay),
            beforeReference =
                referenceFromSnapshot(beforeElement)?.let { DiffReference(endpointPath, it) },
            afterReference =
                referenceFromSnapshot(afterElement)?.let { DiffReference(endpointPath, it) },
        )
    }
}

private fun referenceFromSnapshot(value: JsonElement?): Int? =
    when (value) {
        is JsonPrimitive -> value.intOrNull
        is JsonObject -> value["id"]?.jsonPrimitive?.intOrNull
        else -> null
    }

private suspend fun resolveDiffReferenceValue(
    value: String?,
    endpointPath: String,
    resolveDisplay: suspend (endpointPath: String, id: Int) -> String?,
): String? {
    if (value == null) return null
    value.toIntOrNull()?.let { id ->
        return resolveDisplay(endpointPath, id) ?: value
    }
    val numericParts = value.split(',').map(String::trim)
    if (numericParts.size > 1 && numericParts.all { it.toIntOrNull() != null }) {
        val resolvedParts = buildList {
            for (part in numericParts) {
                val id = part.toInt()
                add(resolveDisplay(endpointPath, id) ?: part)
            }
        }
        return resolvedParts.joinToString(", ")
    }
    val parsed = runCatching { Json.parseToJsonElement(value) }.getOrNull()
    return when (parsed) {
        is JsonObject -> {
            val id = parsed["id"]?.jsonPrimitive?.intOrNull
            if (id == null) value else resolveDisplay(endpointPath, id) ?: value
        }
        is JsonArray -> {
            val resolvedElements = buildList {
                for (element in parsed) {
                    val id = (element as? JsonObject)?.get("id")?.jsonPrimitive?.intOrNull
                    add(
                        if (id == null) element.toString()
                        else resolveDisplay(endpointPath, id) ?: id.toString()
                    )
                }
            }
            resolvedElements.joinToString(", ")
        }
        else -> value
    }
}

private fun snapshotValue(
    snapshot: JsonObject?,
    fieldKey: String,
    snapshotKey: String,
): JsonElement? =
    if (fieldKey.startsWith("custom_fields.")) {
        (snapshot?.get("custom_fields") as? JsonObject)?.get(snapshotKey)
    } else {
        snapshot?.get(snapshotKey)
    }

private fun String.snapshotKey(): String = removePrefix("custom_fields.")

private fun referenceEndpointForDiffField(
    objectType: String?,
    fieldKey: String,
    before: JsonElement?,
    after: JsonElement?,
): String? {
    val snapshotEndpoint =
        sequenceOf(before, after)
            .mapNotNull { (it as? JsonObject)?.get("url")?.jsonContentOrNull() }
            .mapNotNull(NetBoxRef::endpointFromDetailUrl)
            .firstOrNull()
    if (snapshotEndpoint != null) return snapshotEndpoint

    val key = fieldKey.removePrefix("custom_fields.").removeSuffix("_id")
    return OBJECT_REFERENCE_ENDPOINTS[objectType.orEmpty()]?.get(key)
        ?: COMMON_REFERENCE_ENDPOINTS[key]
}

private val COMMON_REFERENCE_ENDPOINTS =
    mapOf(
        "device_type" to "api/dcim/device-types/",
        "manufacturer" to "api/dcim/manufacturers/",
        "site" to "api/dcim/sites/",
        "location" to "api/dcim/locations/",
        "rack" to "api/dcim/racks/",
        "platform" to "api/dcim/platforms/",
        "tenant" to "api/tenancy/tenants/",
        "cluster" to "api/virtualization/clusters/",
        "owner" to "api/tenancy/contacts/",
        "primary_ip4" to "api/ipam/ip-addresses/",
        "primary_ip6" to "api/ipam/ip-addresses/",
        "oob_ip" to "api/ipam/ip-addresses/",
        "device" to "api/dcim/devices/",
        "module" to "api/dcim/modules/",
        "cable" to "api/dcim/cables/",
        "front_port" to "api/dcim/front-ports/",
        "rear_port" to "api/dcim/rear-ports/",
        "power_port" to "api/dcim/power-ports/",
        "power_outlet" to "api/dcim/power-outlets/",
        "console_port" to "api/dcim/console-ports/",
        "vlan" to "api/ipam/vlans/",
        "untagged_vlan" to "api/ipam/vlans/",
        "tagged_vlans" to "api/ipam/vlans/",
        "vrf" to "api/ipam/vrfs/",
        "prefix" to "api/ipam/prefixes/",
        "provider" to "api/circuits/providers/",
        "circuit" to "api/circuits/circuits/",
        "virtual_chassis" to "api/dcim/virtual-chassis/",
        "tags" to "api/extras/tags/",
    )

private val OBJECT_REFERENCE_ENDPOINTS =
    mapOf(
        "dcim.device" to (COMMON_REFERENCE_ENDPOINTS + ("role" to "api/dcim/device-roles/")),
        "dcim.rack" to (COMMON_REFERENCE_ENDPOINTS + ("role" to "api/dcim/rack-roles/")),
        "dcim.interface" to
            (COMMON_REFERENCE_ENDPOINTS +
                mapOf(
                    "parent" to "api/dcim/interfaces/",
                    "bridge" to "api/dcim/interfaces/",
                    "lag" to "api/dcim/interfaces/",
                )),
        "dcim.devicetype" to
            (COMMON_REFERENCE_ENDPOINTS + ("default_platform" to "api/dcim/platforms/")),
        "dcim.device_type" to
            (COMMON_REFERENCE_ENDPOINTS + ("default_platform" to "api/dcim/platforms/")),
        "ipam.ipaddress" to COMMON_REFERENCE_ENDPOINTS,
        "virtualization.virtualmachine" to COMMON_REFERENCE_ENDPOINTS,
        "circuits.circuit" to
            (COMMON_REFERENCE_ENDPOINTS +
                mapOf(
                    "circuit_type" to "api/circuits/circuit-types/",
                    "provider_account" to "api/circuits/provider-accounts/",
                )),
    )

/**
 * Best-effort human-readable rendering of one changelog snapshot value - primitives print as plain
 * text, nested objects/arrays (e.g. a FK reference or a tag list) fall back to their raw JSON since
 * there's no schema here to render them more richly, unlike
 * [dev.pschmitt.nyetbox.ui.generic.GenericFieldRenderer].
 */
private fun JsonElement.diffString(definition: CustomFieldDefinition? = null): String? =
    when (this) {
        is JsonNull -> null
        is JsonPrimitive ->
            when {
                booleanOrNull != null -> if (booleanOrNull == true) "Enabled" else "Disabled"
                else -> contentOrNull ?: content
            }
        is JsonObject ->
            listOf("display", "label", "name", "value").firstNotNullOfOrNull { key ->
                (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            } ?: prettyJson.encodeToString(JsonElement.serializer(), this)
        is JsonArray ->
            mapNotNull { element ->
                    when (element) {
                        is JsonPrimitive -> element.contentOrNull
                        is JsonObject ->
                            listOf("display", "label", "name", "value").firstNotNullOfOrNull { key
                                ->
                                (element[key] as? JsonPrimitive)?.contentOrNull
                            }
                        else -> null
                    }
                }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ") ?: prettyJson.encodeToString(JsonElement.serializer(), this)
    }

private fun CustomFieldDefinition.isMarkdown(): Boolean =
    type.lowercase() in setOf("text", "longtext", "markdown")

private val prettyJson = Json { prettyPrint = true }

private fun JsonElement.jsonContentOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull
