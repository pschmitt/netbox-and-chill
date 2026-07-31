package dev.pschmitt.netboxandchill.ui.generic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class EditFieldKind {
    STRING,
    LONG_TEXT,
    NUMBER,
    INTEGER,
    BOOLEAN,
    REFERENCE,
    CHOICE,
    MULTI_REFERENCE,
    MULTI_CHOICE,
}

data class EditOption(val value: String, val label: String)

/**
 * A field that can be edited and PATCHed back, e.g. via NBC-5's edit mode on the generic detail
 * screen. Reference and choice fields carry their current display label; their picker options are
 * loaded separately from the cache/NetBox OPTIONS metadata by [GenericDetailViewModel].
 */
data class EditableField(
    val key: String,
    val label: String,
    val kind: EditFieldKind,
    val value: String,
    val referenceEndpointPath: String? = null,
    val currentDisplay: String? = null,
    val customFieldName: String? = null,
)

fun selectedValuesFromJson(text: String): List<String> =
    runCatching {
        Json.decodeFromString(JsonArray.serializer(), text).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    }.getOrDefault(emptyList())

fun selectedValuesToJson(values: Collection<String>): String =
    JsonArray(values.map(::JsonPrimitive)).toString()
