package dev.pschmitt.netboxandchill.ui.generic

import dev.pschmitt.netboxandchill.data.schema.Humanize
import dev.pschmitt.netboxandchill.data.schema.NetBoxRef
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

private val SKIPPED_KEYS = setOf("id", "url", "display", "custom_fields")

// NetBox documents these specific fields as Markdown-enabled across (almost) every model -
// "description" is deliberately not included, it's plain short text, not Markdown.
private val MARKDOWN_KEYS = setOf("comments")

// Device-type stock photos are shown inline; other media URLs remain downloadable attachments.
private val IMAGE_KEYS = setOf("front_image", "rear_image")

// Keep copy actions focused on values users commonly transfer elsewhere, rather than adding one
// to every free-text field.
private val COPYABLE_KEYS = setOf("serial", "asset_tag", "primary_ip", "primary_ip4", "primary_ip6")

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
fun buildFieldRows(obj: JsonObject): List<FieldRow> = buildList {
    for ((key, value) in obj) {
        val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        when {
            key in SKIPPED_KEYS -> Unit
            key in MARKDOWN_KEYS ->
                text?.takeIf { it.isNotBlank() }?.let { add(FieldRow.Markdown(Humanize.label(key), it)) }
            key in IMAGE_KEYS && text != null && isMediaUrl(text) ->
                add(FieldRow.Image(Humanize.label(key), text))
            text != null && isMediaUrl(text) -> {
                val filename =
                    (obj["filename"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: text.substringAfterLast('/')
                add(FieldRow.FileAttachment(Humanize.label(key), text, filename))
            }
            else -> renderField(key, Humanize.label(key), value)?.let(::add)
        }
    }
    // custom_fields is a NetBox-instance-specific map of {field_name: value} - each one gets its
    // own row via the same generic rendering used for top-level fields, rather than being folded
    // into one flattened blob or dropped for non-primitive values (object/multi-select custom
    // fields).
    (obj["custom_fields"] as? JsonObject)?.let { customFields ->
        for ((key, value) in customFields) {
            renderField(key, Humanize.label(key), value)?.let(::add)
        }
    }
}

private fun renderField(key: String, label: String, value: JsonElement): FieldRow? =
    when (value) {
        is JsonNull -> null
        is JsonPrimitive -> renderPrimitive(key, label, value)
        is JsonObject -> renderObject(key, label, value)
        is JsonArray -> renderArray(label, value)
    }

private fun renderPrimitive(key: String, label: String, value: JsonPrimitive): FieldRow? {
    value.booleanOrNull?.let {
        return FieldRow.PlainText(label, if (it) "Yes" else "No")
    }
    val text = value.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
    if (value.isString && isHttpUrl(text)) return FieldRow.ExternalLink(label, text)
    return FieldRow.PlainText(label, text, copyable = key in COPYABLE_KEYS)
}

private fun isHttpUrl(text: String): Boolean =
    (text.startsWith("http://") || text.startsWith("https://")) && text.toHttpUrlOrNull() != null

/** NetBox-served uploaded files are always under a `/media/` path, regardless of app/plugin. */
private fun isMediaUrl(text: String): Boolean =
    isHttpUrl(text) && text.toHttpUrlOrNull()?.encodedPath?.contains("/media/") == true

private fun renderObject(key: String, label: String, value: JsonObject): FieldRow? {
    asRefTarget(value)?.let { return FieldRow.Reference(label, it, copyable = key in COPYABLE_KEYS) }
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
    val endpointPath = NetBoxRef.endpointFromDetailUrl(url) ?: return null
    return RefTarget(display, endpointPath, id)
}

/** The subset of [buildFieldRows]'s fields that can round-trip through a plain text/switch input
 * and PATCH back cleanly - see [EditableField]. */
fun buildEditableFields(obj: JsonObject): List<EditableField> =
    obj.mapNotNull { (key, value) ->
        if (key in EDIT_BLOCKLIST) return@mapNotNull null
        if (value !is JsonPrimitive || value is JsonNull) return@mapNotNull null
        // Fields like device-type's front_image/rear_image are absolute media URLs computed by
        // NetBox, not plain text - PATCHing one back as-is fails server-side ("The submitted data
        // was not a file."), and since buildPatchBody() sends every editable field's current
        // value (not just the ones actually changed), a single such field breaks the *entire* save
        // even when the user only meant to edit something else entirely. Confirmed live against a
        // real device type (Mi Pad 4, dcim/device-types/244) - "edit does not work at all" turned
        // out to be this, not a device-type-specific issue.
        if (value.isString) {
            val text = value.contentOrNull
            if (text != null && isMediaUrl(text)) return@mapNotNull null
        }
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
