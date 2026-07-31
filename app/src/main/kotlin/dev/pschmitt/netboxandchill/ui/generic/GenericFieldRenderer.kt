package dev.pschmitt.netboxandchill.ui.generic

import dev.pschmitt.netboxandchill.data.repository.CustomFieldDefinition
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

private val USER_REFERENCE_KEYS = setOf("created_by", "last_updated_by")

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
fun buildFieldRows(
    obj: JsonObject,
    markdownCustomFieldNames: Set<String> = emptySet(),
    endpointPath: String? = null,
): List<FieldRow> =
    buildFieldRows(
        obj,
        markdownCustomFieldNames.map { CustomFieldDefinition(it, "markdown", null, null, Int.MAX_VALUE) },
        endpointPath,
    )

fun buildFieldRows(
    obj: JsonObject,
    customFieldDefinitions: List<CustomFieldDefinition>,
    endpointPath: String? = null,
): List<FieldRow> = buildList {
    for ((key, value) in obj) {
        val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        when {
            key in SKIPPED_KEYS -> Unit
            key.endsWith("_display") && key.removeSuffix("_display") in USER_REFERENCE_KEYS -> Unit
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
            else -> {
                val count = (value as? JsonPrimitive)?.intOrNull
                val target = countTargetFor(key, obj, endpointPath)
                if (count != null && target != null) {
                    add(FieldRow.Count(Humanize.label(key), count.toString(), target))
                } else if (key in USER_REFERENCE_KEYS) {
                    val display =
                        (obj["${key}_display"] as? JsonPrimitive)?.contentOrNull
                            ?: userReferenceDisplay(value)
                            ?: value.toString().takeIf { it.isNotBlank() }
                    display?.let { add(FieldRow.PlainText(Humanize.label(key), it)) }
                } else {
                    renderField(key, Humanize.label(key), value)?.let(::add)
                }
            }
        }
    }
    // custom_fields is a NetBox-instance-specific map of {field_name: value} - each one gets its
    // own row via the same generic rendering used for top-level fields, rather than being folded
    // into one flattened blob or dropped for non-primitive values (object/multi-select custom
    // fields).
    (obj["custom_fields"] as? JsonObject)?.let { customFields ->
        val definitions = customFieldDefinitions.associateBy { it.name }
        val sortedFields =
            customFields.entries.withIndex().sortedWith { left, right ->
                val leftDefinition = definitions[left.value.key]
                val rightDefinition = definitions[right.value.key]
                val leftGroup = leftDefinition?.group?.trim().orEmpty()
                val rightGroup = rightDefinition?.group?.trim().orEmpty()
                when {
                    leftGroup.isNotBlank() && rightGroup.isBlank() -> -1
                    leftGroup.isBlank() && rightGroup.isNotBlank() -> 1
                    leftGroup.isBlank() -> left.index.compareTo(right.index)
                    else ->
                        String.CASE_INSENSITIVE_ORDER.compare(leftGroup, rightGroup).takeIf { it != 0 }
                            ?: (leftDefinition?.weight ?: Int.MAX_VALUE).compareTo(
                                rightDefinition?.weight ?: Int.MAX_VALUE
                            ).takeIf { it != 0 }
                            ?: String.CASE_INSENSITIVE_ORDER.compare(
                                leftDefinition?.label ?: Humanize.label(left.value.key),
                                rightDefinition?.label ?: Humanize.label(right.value.key),
                            )
                }
            }.map { it.value }
        var activeGroup: String? = null
        for ((key, value) in sortedFields) {
            val definition = definitions[key]
            val group = definition?.group?.trim()?.takeIf { it.isNotBlank() }
            if (group != null && group != activeGroup) add(FieldRow.CustomGroup(group))
            activeGroup = group
            val label = definition?.label?.takeIf { it.isNotBlank() } ?: Humanize.label(key)
            if (definition?.type == "markdown") {
                (value as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                    add(FieldRow.Markdown(label, it))
                }
            } else {
                renderField(key, label, value)?.let(::add)
            }
        }
    }
}

private fun countTargetFor(key: String, obj: JsonObject, endpointPath: String?): CountTarget? {
    val parentId = (obj["id"] as? JsonPrimitive)?.intOrNull ?: return null
    val definition =
        when (endpointPath) {
            "api/dcim/locations/", "api/dcim/sites/" ->
                when (key) {
                    "rack_count" -> CountTarget("api/dcim/racks/", "Racks", "location", parentId)
                    "device_count" -> CountTarget("api/dcim/devices/", "Devices", "location", parentId)
                    "prefix_count" -> CountTarget("api/ipam/prefixes/", "Prefixes", "scope", parentId)
                    else -> null
                }
            else -> null
        }
    return definition
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

/** Visible URL text omits the repeated scheme/host; callers still retain the original URL. */
fun shortenDisplayedUrl(url: String): String {
    val parsed = url.toHttpUrlOrNull() ?: return url
    return buildString {
        append(parsed.encodedPath.ifBlank { "/" })
        parsed.encodedQuery?.let { append('?').append(it) }
        parsed.encodedFragment?.let { append('#').append(it) }
    }
}

/** NetBox-served uploaded files are always under a `/media/` path, regardless of app/plugin. */
private fun isMediaUrl(text: String): Boolean =
    isHttpUrl(text) && text.toHttpUrlOrNull()?.encodedPath?.contains("/media/") == true

private fun renderObject(key: String, label: String, value: JsonObject): FieldRow? {
    if (key in USER_REFERENCE_KEYS) {
        userReferenceDisplay(value)?.let { return FieldRow.PlainText(label, it) }
    }
    asRefTarget(value)?.let { return FieldRow.Reference(label, it, copyable = key in COPYABLE_KEYS) }
    // Choice-style field, e.g. status: {"value": "active", "label": "Active"}.
    val choiceLabel = (value["label"] as? JsonPrimitive)?.contentOrNull
    if (choiceLabel != null) return FieldRow.PlainText(label, choiceLabel)
    // Unrecognized nested object (e.g. weight: {value, unit}) - best-effort flatten.
    val flattened =
        value.entries.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { "$k: $it" } }
    return flattened.takeIf { it.isNotEmpty() }?.let { FieldRow.PlainText(label, it.joinToString(", ")) }
}

private fun userReferenceDisplay(value: JsonElement): String? {
    val user =
        when (value) {
            is JsonObject -> (value["user"] as? JsonObject) ?: value
            else -> return (value as? JsonPrimitive)?.contentOrNull
        }
    listOf("display", "username", "name", "full_name", "email").forEach { key ->
        (user[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
    }
    val firstName = (user["first_name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val lastName = (user["last_name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    return "$firstName $lastName".trim().takeIf { it.isNotBlank() }
        ?: (user["id"] as? JsonPrimitive)?.contentOrNull
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

/** The fields that can round-trip through the generic edit form and PATCH cleanly. */
fun buildEditableFields(obj: JsonObject): List<EditableField> =
    obj.mapNotNull { (key, value) ->
        if (key in EDIT_BLOCKLIST) return@mapNotNull null
        when (value) {
            is JsonNull -> null
            is JsonObject -> {
                asRefTarget(value)?.let { target ->
                    return@mapNotNull EditableField(
                        key = key,
                        label = Humanize.label(key),
                        kind = EditFieldKind.REFERENCE,
                        value = target.id.toString(),
                        referenceEndpointPath = target.endpointPath,
                        currentDisplay = target.display,
                    )
                }
                val choiceValue = (value["value"] as? JsonPrimitive)?.contentOrNull
                val choiceLabel = (value["label"] as? JsonPrimitive)?.contentOrNull
                if (choiceValue != null && choiceLabel != null) {
                    EditableField(
                        key = key,
                        label = Humanize.label(key),
                        kind = EditFieldKind.CHOICE,
                        value = choiceValue,
                        currentDisplay = choiceLabel,
                    )
                } else null
            }
            is JsonPrimitive -> {
                // Fields like device-type's front_image/rear_image are absolute media URLs
                // computed by NetBox, not plain text - PATCHing one back as-is fails server-side
                // ("The submitted data was not a file.").
                if (value.isString && value.contentOrNull?.let(::isMediaUrl) == true) return@mapNotNull null
                val kind =
                    when {
                        !value.isString && (value.content == "true" || value.content == "false") ->
                            EditFieldKind.BOOLEAN
                        !value.isString && value.doubleOrNull != null -> EditFieldKind.NUMBER
                        else -> EditFieldKind.STRING
                    }
                EditableField(key, Humanize.label(key), kind, value.contentOrNull ?: "")
            }
            is JsonArray -> null
        }
    }

/** Converts edited text back to the JSON type NetBox expects for that field. */
fun EditFieldKind.toJsonElement(text: String): JsonElement =
    when (this) {
        EditFieldKind.STRING,
        EditFieldKind.CHOICE -> JsonPrimitive(text)
        EditFieldKind.NUMBER -> text.toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(text)
        EditFieldKind.BOOLEAN -> JsonPrimitive(text.toBooleanStrictOrNull() ?: false)
        EditFieldKind.REFERENCE -> text.toIntOrNull()?.let(::JsonPrimitive) ?: JsonNull
    }

/** Kept as a convenience for callers/tests that only handle primitive field kinds. */
fun EditFieldKind.toJsonPrimitive(text: String): JsonPrimitive =
    (toJsonElement(text) as? JsonPrimitive) ?: JsonPrimitive(text)

fun buildPatchBody(edits: Map<String, Pair<EditFieldKind, String>>): JsonObject =
    JsonObject(edits.mapValues { (_, kindAndValue) -> kindAndValue.first.toJsonElement(kindAndValue.second) })
