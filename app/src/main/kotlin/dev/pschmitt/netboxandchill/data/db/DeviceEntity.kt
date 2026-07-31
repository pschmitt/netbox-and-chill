package dev.pschmitt.netboxandchill.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val url: String,
    val statusValue: String?,
    val statusLabel: String?,
    val siteName: String?,
    val rackName: String?,
    val position: Double?,
    val roleName: String?,
    val manufacturerName: String?,
    val deviceTypeModel: String?,
    val serial: String?,
    val assetTag: String?,
    val primaryIp: String?,
    val comments: String?,
    val lastUpdated: String?,
    val syncedAt: Long,
)
