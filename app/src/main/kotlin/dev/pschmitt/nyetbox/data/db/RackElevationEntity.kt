package dev.pschmitt.nyetbox.data.db

import androidx.room.Entity

/** One half-U slot from NetBox's rack elevation endpoint. */
@Entity(tableName = "rack_elevation", primaryKeys = ["rackId", "face", "slotName"])
data class RackElevationEntity(
    val rackId: Int,
    val face: String,
    val slotName: String,
    val position: Double,
    val deviceId: Int?,
    val deviceDisplay: String?,
    val occupied: Boolean,
    val syncedAt: Long,
)
