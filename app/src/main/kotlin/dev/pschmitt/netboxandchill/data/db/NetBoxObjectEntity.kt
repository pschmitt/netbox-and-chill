package dev.pschmitt.netboxandchill.data.db

import androidx.room.Entity

/**
 * Generic cache for any NetBox object (any endpoint, not just devices) - the raw API response is
 * kept as JSON and rendered field-by-field at the UI layer (see GenericDetailScreen) rather than
 * mapped into a typed entity per object type, since covering NetBox's full data model with a
 * typed entity per model would mean hand-writing 100+ near-duplicate entities.
 */
@Entity(tableName = "netbox_objects", primaryKeys = ["endpointPath", "id"])
data class NetBoxObjectEntity(
    val endpointPath: String,
    val id: Int,
    val display: String,
    val secondaryLine: String?,
    val json: String,
    val syncedAt: Long,
)
