package dev.pschmitt.netboxandchill.ui.generic

import dev.pschmitt.netboxandchill.data.schema.Humanize
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ConflictField(
    val key: String,
    val label: String,
    val base: String,
    val local: String,
    val server: String,
)

enum class ConflictChoice {
    LOCAL,
    SERVER,
}

private val CONFLICT_METADATA = setOf("id", "url", "display", "last_updated")

/** Builds a compact three-way diff for the field-level conflict resolver. */
fun buildConflictFields(base: JsonObject, local: JsonObject, server: JsonObject): List<ConflictField> {
    val keys = (base.keys + local.keys + server.keys).filterNot { it in CONFLICT_METADATA }.toSortedSet()
    return keys.mapNotNull { key ->
        val baseValue = renderValue(base[key])
        val localValue = renderValue(local[key])
        val serverValue = renderValue(server[key])
        if (baseValue == localValue && baseValue == serverValue) {
            null
        } else {
            ConflictField(key, Humanize.label(key), baseValue, localValue, serverValue)
        }
    }
}

private fun renderValue(value: JsonElement?): String =
    when (value) {
        null, JsonNull -> "—"
        is JsonPrimitive -> value.contentOrNull ?: value.toString()
        else -> value.toString()
    }
