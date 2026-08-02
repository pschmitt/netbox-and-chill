package dev.pschmitt.netboxandchill.data.schema

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Common, null-safe projection of the reference shape returned by NetBox serializers. */
data class NetBoxJsonReference(
    val id: Int,
    val url: String? = null,
    val display: String? = null,
)

fun JsonObject.jsonString(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

fun JsonObject.jsonInt(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

fun JsonObject.jsonReference(key: String): NetBoxJsonReference? =
    (this[key] as? JsonObject)?.let { value ->
        value.jsonInt("id")?.let { id ->
            NetBoxJsonReference(
                id = id,
                url = value.jsonString("url"),
                display = value.jsonString("display") ?: value.jsonString("name"),
            )
        }
    }

fun JsonElement.jsonContentOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull
