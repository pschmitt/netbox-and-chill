package dev.pschmitt.nyetbox.data.repository

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One human-readable field/value pair that caused a linked choice to match. */
data class ChoiceSearchMatch(val fieldKey: String, val fieldValue: String)

/**
 * Builds a cache-backed search index from an arbitrary NetBox object.
 *
 * Both the top-level relation summary (for example `manufacturer`) and recursively discovered
 * child values (for example `manufacturer.name` or `custom_fields.purchase_info`) are retained.
 * This keeps the picker generic: it does not need to know which fields a particular NetBox model
 * or plugin exposes.
 */
internal fun JsonObject.createChoiceSearchFields(): Map<String, String> {
    val fields = linkedMapOf<String, String>()
    entries.forEach { (key, element) ->
        if (key !in CHOICE_SEARCH_METADATA_KEYS) {
            collectChoiceSearchFields(fields, key, element)
        }
    }
    return fields
}

private fun collectChoiceSearchFields(
    fields: MutableMap<String, String>,
    path: String,
    element: JsonElement,
) {
    when (element) {
        is JsonPrimitive -> element.contentOrNull?.takeIf(String::isNotBlank)?.let {
            appendChoiceSearchValue(fields, path, it)
        }
        is JsonObject -> {
            val summary =
                CHOICE_SEARCH_DISPLAY_KEYS
                    .asSequence()
                    .mapNotNull { key ->
                        (element[key] as? JsonPrimitive)
                            ?.contentOrNull
                            ?.takeIf(String::isNotBlank)
                    }
                    .distinct()
                    .joinToString(" ")
            if (summary.isNotBlank()) appendChoiceSearchValue(fields, path, summary)
            element.entries.forEach { (key, child) ->
                if (key !in CHOICE_SEARCH_METADATA_KEYS) {
                    collectChoiceSearchFields(fields, "$path.$key", child)
                }
            }
        }
        is JsonArray -> {
            element.forEach { child ->
                when (child) {
                    is JsonObject -> child.entries.forEach { (key, nested) ->
                        if (key !in CHOICE_SEARCH_METADATA_KEYS) {
                            collectChoiceSearchFields(fields, "$path.$key", nested)
                        }
                    }
                    else -> collectChoiceSearchFields(fields, path, child)
                }
            }
        }
    }
}

private fun appendChoiceSearchValue(
    fields: MutableMap<String, String>,
    key: String,
    value: String,
) {
    val existing = fields[key]
    fields[key] =
        if (existing.isNullOrBlank() || existing == value) value
        else "$existing $value"
}

/** Returns the fields which match a free-text or `field:value` linked-choice query. */
fun choiceSearchMatches(
    label: String,
    value: String,
    searchFields: Map<String, String>,
    query: String,
): List<ChoiceSearchMatch> {
    val rawQuery = query.lowercase().trimStart()
    val normalizedQuery = rawQuery.trim()
    if (normalizedQuery.isBlank()) return emptyList()

    val separator = rawQuery.indexOfFirst { it.isWhitespace() || it == ':' }
    if (separator > 0) {
        val requestedField = rawQuery.substring(0, separator)
        val fieldKey =
            searchFields.keys.firstOrNull { key ->
                key.lowercase() == requestedField ||
                    choiceSearchFieldLabel(key).lowercase() == requestedField ||
                    key.substringAfterLast('.').lowercase() == requestedField
            }
        if (fieldKey != null) {
            val fieldQuery = rawQuery.substring(separator + 1).trim()
            val fieldValue = searchFields[fieldKey].orEmpty()
            if (fieldQuery.isBlank()) {
                return listOf(ChoiceSearchMatch(fieldKey, fieldValue))
            }
            return if (fieldValue.lowercase().contains(fieldQuery)) {
                listOf(ChoiceSearchMatch(fieldKey, fieldValue))
            } else {
                emptyList()
            }
        }
    }

    return buildList {
        if (label.lowercase().contains(normalizedQuery)) {
            add(ChoiceSearchMatch("name", label))
        }
        if (value.lowercase().contains(normalizedQuery)) {
            add(ChoiceSearchMatch("id", value))
        }
        searchFields.forEach { (fieldKey, fieldValue) ->
            if (fieldValue.lowercase().contains(normalizedQuery)) {
                add(ChoiceSearchMatch(fieldKey, fieldValue))
            }
        }
    }
}

fun choiceSearchHint(
    label: String,
    value: String,
    searchFields: Map<String, String>,
    query: String,
): String? =
    choiceSearchMatches(label, value, searchFields, query)
        .firstOrNull()
        ?.let { match ->
            "${choiceSearchFieldLabel(match.fieldKey)}: ${compactSearchMatchValue(match.fieldValue)}"
        }
        ?.takeIf(String::isNotBlank)
        ?.let { if (it.length > MAX_CHOICE_SEARCH_HINT_LENGTH) it.take(MAX_CHOICE_SEARCH_HINT_LENGTH - 1) + "…" else it }

internal fun compactSearchMatchValue(value: String): String {
    val seen = mutableSetOf<String>()
    return value
        .split(Regex("\\s+"))
        .filter { token -> token.isNotBlank() && seen.add(token.lowercase()) }
        .joinToString(" ")
}

internal fun choiceSearchFieldLabel(key: String): String =
    key.split('.')
        .joinToString(" / ") { segment ->
            segment.replace('_', ' ').replaceFirstChar { it.titlecase() }
        }

private val CHOICE_SEARCH_METADATA_KEYS =
    setOf("id", "url", "display_url", "created", "last_updated")

private val CHOICE_SEARCH_DISPLAY_KEYS =
    listOf("display", "name", "label", "model", "slug", "description")

private const val MAX_CHOICE_SEARCH_HINT_LENGTH = 120
