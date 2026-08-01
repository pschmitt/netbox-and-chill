package dev.pschmitt.netboxandchill.ui.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.repository.CustomFieldDefinition
import dev.pschmitt.netboxandchill.data.repository.CustomFieldRepository
import dev.pschmitt.netboxandchill.data.repository.DashboardRepository
import dev.pschmitt.netboxandchill.data.schema.Humanize
import dev.pschmitt.netboxandchill.ui.navigation.Route
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

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
)

data class ObjectChangeDiffUi(
    val objectRepr: String,
    val actionLabel: String,
    val userDisplay: String,
    val time: String,
    val rows: List<DiffRow>,
)

@HiltViewModel
class ObjectChangeDiffViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DashboardRepository,
    private val customFieldRepository: CustomFieldRepository,
) :
    ViewModel() {

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
            repository
                .fetchObjectChange(route.changeId)
                .onSuccess {
                    val definitions = customFieldRepository.observeDefinitions().first()
                    _diff.value = it.toDiffUi(definitions)
                }
                .onFailure { _errorMessage.value = it.message ?: "Couldn't load this change" }
            _isLoading.value = false
        }
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    private fun JsonObject.toDiffUi(
        customFieldDefinitions: List<CustomFieldDefinition>
    ): ObjectChangeDiffUi {
        val pre = this["prechange_data"] as? JsonObject
        val post = this["postchange_data"] as? JsonObject
        val actionObj = this["action"] as? JsonObject
        return ObjectChangeDiffUi(
            objectRepr = this["object_repr"]?.jsonContentOrNull() ?: "#${route.changeId}",
            actionLabel = actionObj?.get("label")?.jsonContentOrNull() ?: "Changed",
            userDisplay =
                (this["user"] as? JsonObject)?.get("display")?.jsonContentOrNull()
                    ?: this["user_name"]?.jsonContentOrNull()
                    ?: "Unknown",
            time = this["time"]?.jsonContentOrNull() ?: "",
            rows = buildDiffRows(pre, post, customFieldDefinitions),
        )
    }
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
    val ordinaryRows =
        ordinaryKeys.mapNotNull { key ->
            val before = pre?.get(key)?.diffString()
            val after = post?.get(key)?.diffString()
            if (before == after) null else DiffRow(Humanize.label(key), before, after)
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
                            String.CASE_INSENSITIVE_ORDER.compare(leftGroup, rightGroup)
                                .takeIf { it != 0 }
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
                        label = definition?.label?.takeIf { it.isNotBlank() } ?: Humanize.label(key),
                        before = before,
                        after = after,
                        section =
                            definition?.group?.trim()?.takeIf { it.isNotBlank() }
                                ?: "Custom fields",
                        markdown = definition?.isMarkdown() == true,
                    )
                }
            }
    return ordinaryRows + customRows
}

/**
 * Best-effort human-readable rendering of one changelog snapshot value - primitives print as plain
 * text, nested objects/arrays (e.g. a FK reference or a tag list) fall back to their raw JSON since
 * there's no schema here to render them more richly, unlike
 * [dev.pschmitt.netboxandchill.ui.generic.GenericFieldRenderer].
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
            listOf("display", "label", "name", "value")
                .firstNotNullOfOrNull { key ->
                    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                }
                ?: prettyJson.encodeToString(JsonElement.serializer(), this)
        is JsonArray ->
            mapNotNull { element ->
                    when (element) {
                        is JsonPrimitive -> element.contentOrNull
                        is JsonObject ->
                            listOf("display", "label", "name", "value")
                                .firstNotNullOfOrNull { key ->
                                    (element[key] as? JsonPrimitive)?.contentOrNull
                                }
                        else -> null
                    }
                }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?: prettyJson.encodeToString(JsonElement.serializer(), this)
    }

private fun CustomFieldDefinition.isMarkdown(): Boolean =
    type.lowercase() in setOf("text", "longtext", "markdown")

private val prettyJson = Json { prettyPrint = true }

private fun JsonElement.jsonContentOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull
