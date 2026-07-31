package dev.pschmitt.netboxandchill.ui.generic

import dev.pschmitt.netboxandchill.data.schema.Humanize
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val SKIPPED_KEYS = setOf("id", "url", "display")

// Meta/system fields NetBox manages itself - not user-editable, or too complex to round-trip as
// plain text yet (custom_fields needs its own per-field-type handling, not a blanket text field).
private val EDIT_BLOCKLIST =
    setOf("id", "url", "display", "display_url", "created", "last_updated", "custom_fields")

/**
 * Turns a raw NetBox object (any type) into a generic list of fields to render, without needing
 * any type-specific knowledge: nested objects with `id`+`url` are treated as references to another
 * object (tappable, navigable to that object's own generic detail screen - this is also how tags
 * end up as tappable chips, since NetBox tags are real objects with their own detail view too).
 */
fun buildFieldRows(obj: JsonObject): List<FieldRow> =
    obj.mapNotNull { (key, value) ->
        if (key in SKIPPED_KEYS) null else renderField(Humanize.label(key), value)
    }

private fun renderField(label: String, value: JsonElement): FieldRow? =
    when (value) {
        is JsonNull -> null
        is JsonPrimitive -> renderPrimitive(label, value)
        is JsonObject -> renderObject(label, value)
        is JsonArray -> renderArray(label, value)
    }

private fun renderPrimitive(label: String, value: JsonPrimitive): FieldRow? {
    value.booleanOrNull?.let {
        return FieldRow.PlainText(label, if (it) "Yes" else "No")
    }
    val text = value.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
    return FieldRow.PlainText(label, text)
}

private fun renderObject(label: String, value: JsonObject): FieldRow? {
    asRefTarget(value)?.let { return FieldRow.Reference(label, it) }
    // Choice-style field, e.g. status: {"value": "active", "label": "Active"}.
    val choiceLabel = (value["label"] as? JsonPrimitive)?.contentOrNull
    if (choiceLabel != null) return FieldRow.PlainText(label, choiceLabel)
    // Unrecognized nested object (e.g. weight: {value, unit}) - best-effort flatten.
    val flattened =
        value.entries.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { "$k: $it" } }
    return flattened.takeIf { it.isNotEmpty() }?.let { FieldRow.PlainText(label, it.joinToString(", ")) }
}

private fun renderArray(label: String, value: JsonArray): FieldRow? {
    if (value.isEmpty()) return null
    val refs = value.mapNotNull { (it as? JsonObject)?.let(::asRefTarget) }
    if (refs.size == value.size) return FieldRow.ReferenceList(label, refs)
    val chips =
        value.mapNotNull {
            when (it) {
                is JsonPrimitive -> it.contentOrNull
                is JsonObject ->
                    (it["display"] as? JsonPrimitive)?.contentOrNull
                        ?: (it["name"] as? JsonPrimitive)?.contentOrNull
                else -> null
            }
        }
    return chips.takeIf { it.isNotEmpty() }?.let { FieldRow.ChipList(label, it) }
}

private fun asRefTarget(value: JsonObject): RefTarget? {
    val id = (value["id"] as? JsonPrimitive)?.intOrNull ?: return null
    val url = (value["url"] as? JsonPrimitive)?.contentOrNull ?: return null
    val display =
        (value["display"] as? JsonPrimitive)?.contentOrNull
            ?: (value["name"] as? JsonPrimitive)?.contentOrNull
            ?: "#$id"
    val endpointPath = listEndpointFromDetailUrl(url) ?: return null
    return RefTarget(display, endpointPath, id)
}

/** "https://host/api/dcim/sites/3/" -> "api/dcim/sites/" (strips the trailing id segment). */
private fun listEndpointFromDetailUrl(detailUrl: String): String? {
    val path = detailUrl.toHttpUrlOrNull()?.encodedPath ?: return null
    val trimmed = path.trim('/')
    val lastSlash = trimmed.lastIndexOf('/')
    if (lastSlash < 0) return null
    return trimmed.substring(0, lastSlash + 1)
}

/** The subset of [buildFieldRows]'s fields that can round-trip through a plain text/switch input
 * and PATCH back cleanly - see [EditableField]. */
fun buildEditableFields(obj: JsonObject): List<EditableField> =
    obj.mapNotNull { (key, value) ->
        if (key in EDIT_BLOCKLIST) return@mapNotNull null
        if (value !is JsonPrimitive || value is JsonNull) return@mapNotNull null
        val kind =
            when {
                !value.isString && (value.content == "true" || value.content == "false") -> EditFieldKind.BOOLEAN
                !value.isString && value.doubleOrNull != null -> EditFieldKind.NUMBER
                else -> EditFieldKind.STRING
            }
        EditableField(key, Humanize.label(key), kind, value.contentOrNull ?: "")
    }

/** Converts edited text back to the JSON type NetBox expects for that field, for the PATCH body. */
fun EditFieldKind.toJsonPrimitive(text: String): JsonPrimitive =
    when (this) {
        EditFieldKind.STRING -> JsonPrimitive(text)
        EditFieldKind.NUMBER -> text.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(text)
        EditFieldKind.BOOLEAN -> JsonPrimitive(text.toBooleanStrictOrNull() ?: false)
    }

fun buildPatchBody(edits: Map<String, Pair<EditFieldKind, String>>): JsonObject =
    JsonObject(edits.mapValues { (_, kindAndValue) -> kindAndValue.first.toJsonPrimitive(kindAndValue.second) })
