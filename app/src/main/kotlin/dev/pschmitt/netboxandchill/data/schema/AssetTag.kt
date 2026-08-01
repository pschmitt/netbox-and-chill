package dev.pschmitt.netboxandchill.data.schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class AssetTagState(val hasField: Boolean, val value: String?)

fun JsonObject.assetTagState(): AssetTagState {
    val element = get("asset_tag") ?: return AssetTagState(hasField = false, value = null)
    val value = (element as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
    return AssetTagState(hasField = true, value = value)
}

fun assetTagStateFromRawJson(raw: String): AssetTagState =
    runCatching { Json.parseToJsonElement(raw) as? JsonObject }
        .getOrNull()
        ?.assetTagState()
        ?: AssetTagState(hasField = false, value = null)
