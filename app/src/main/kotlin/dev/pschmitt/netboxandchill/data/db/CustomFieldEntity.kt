package dev.pschmitt.netboxandchill.data.db

import androidx.room.Entity

@Entity(tableName = "custom_fields", primaryKeys = ["name"])
data class CustomFieldEntity(
    val name: String,
    val type: String,
    val label: String?,
    val groupName: String?,
    val weight: Int,
    val syncedAt: Long,
)
