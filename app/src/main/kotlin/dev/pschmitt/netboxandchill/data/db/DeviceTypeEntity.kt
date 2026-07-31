package dev.pschmitt.netboxandchill.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_types")
data class DeviceTypeEntity(
    @PrimaryKey val id: Int,
    val model: String?,
    val frontImageUrl: String?,
    val rearImageUrl: String?,
    val syncedAt: Long,
)
