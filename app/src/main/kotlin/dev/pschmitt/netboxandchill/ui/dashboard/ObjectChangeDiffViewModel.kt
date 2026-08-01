package dev.pschmitt.netboxandchill.ui.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.netboxandchill.data.repository.DashboardRepository
import dev.pschmitt.netboxandchill.data.schema.Humanize
import dev.pschmitt.netboxandchill.ui.navigation.Route
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * One field that differs between a changelog entry's before/after snapshots - either side may be
 * null: absent entirely for a create (no "before"), or a delete (no "after"), or genuinely absent
 * from that particular snapshot (e.g. a field added by a later NetBox version).
 */
data class DiffRow(val label: String, val before: String?, val after: String?)

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
constructor(savedStateHandle: SavedStateHandle, private val repository: DashboardRepository) :
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
                .onSuccess { _diff.value = it.toDiffUi() }
                .onFailure { _errorMessage.value = it.message ?: "Couldn't load this change" }
            _isLoading.value = false
        }
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    private fun JsonObject.toDiffUi(): ObjectChangeDiffUi {
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
            rows = buildDiffRows(pre, post),
        )
    }
}

/**
 * Internal rather than private so a unit test can exercise the diffing logic directly, same pattern
 * as `GenericFieldRenderer.buildFieldRows`.
 */
internal fun buildDiffRows(pre: JsonObject?, post: JsonObject?): List<DiffRow> {
    val keys = (pre?.keys.orEmpty() + post?.keys.orEmpty()).toSet().sorted()
    return keys.mapNotNull { key ->
        val before = pre?.get(key)?.diffString()
        val after = post?.get(key)?.diffString()
        if (before == after) null else DiffRow(Humanize.label(key), before, after)
    }
}

/**
 * Best-effort human-readable rendering of one changelog snapshot value - primitives print as plain
 * text, nested objects/arrays (e.g. a FK reference or a tag list) fall back to their raw JSON since
 * there's no schema here to render them more richly, unlike
 * [dev.pschmitt.netboxandchill.ui.generic.GenericFieldRenderer].
 */
private fun JsonElement.diffString(): String? =
    when (this) {
        is JsonNull -> null
        is JsonPrimitive -> contentOrNull ?: content
        else -> toString()
    }

private fun JsonElement.jsonContentOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull
