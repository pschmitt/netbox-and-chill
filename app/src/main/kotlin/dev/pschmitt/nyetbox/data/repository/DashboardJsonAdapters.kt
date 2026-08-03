package dev.pschmitt.nyetbox.data.repository

import dev.pschmitt.nyetbox.data.db.BookmarkEntity
import dev.pschmitt.nyetbox.data.db.ObjectChangeEntity
import dev.pschmitt.nyetbox.data.schema.NetBoxRef
import dev.pschmitt.nyetbox.data.schema.jsonInt
import dev.pschmitt.nyetbox.data.schema.jsonReference
import dev.pschmitt.nyetbox.data.schema.jsonString
import kotlinx.serialization.json.JsonObject

/** Common dashboard list-row projections, kept independent from the repository lifecycle. */
internal fun JsonObject.toBookmarkEntity(syncedAt: Long): BookmarkEntity? {
    val id = jsonInt("id") ?: return null
    val target = jsonReference("object")
    return BookmarkEntity(
        id = id,
        display = jsonString("display") ?: target?.display ?: "Bookmark #$id",
        objectType = jsonString("object_type") ?: "",
        targetEndpointPath = target?.url?.let(NetBoxRef::endpointFromDetailUrl),
        targetId = target?.id,
        created = jsonString("created") ?: "",
        syncedAt = syncedAt,
    )
}

internal fun JsonObject.toObjectChangeEntity(syncedAt: Long): ObjectChangeEntity? {
    val id = jsonInt("id") ?: return null
    val action = this["action"] as? JsonObject
    val target = jsonReference("changed_object")
    return ObjectChangeEntity(
        id = id,
        time = jsonString("time") ?: "",
        userDisplay = jsonReference("user")?.display ?: jsonString("user_name") ?: "Unknown",
        actionValue = action?.jsonString("value") ?: "",
        actionLabel = action?.jsonString("label") ?: action?.jsonString("value") ?: "",
        objectRepr = jsonString("object_repr") ?: "#$id",
        targetEndpointPath = target?.url?.let(NetBoxRef::endpointFromDetailUrl),
        targetId = target?.id,
        syncedAt = syncedAt,
    )
}
