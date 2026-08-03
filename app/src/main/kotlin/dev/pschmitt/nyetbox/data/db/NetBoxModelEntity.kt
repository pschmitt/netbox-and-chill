package dev.pschmitt.nyetbox.data.db

import androidx.room.Entity

/** A single discovered NetBox object type, e.g. app="dcim", key="racks", label="Racks". */
@Entity(tableName = "netbox_models", primaryKeys = ["appKey", "modelKey"])
data class NetBoxModelEntity(
    val appKey: String,
    val appLabel: String,
    val modelKey: String,
    val modelLabel: String,
    // Relative path, e.g. "api/dcim/racks/" - never an absolute URL, so it survives a base-URL
    // change untouched (see DynamicBaseUrlInterceptor).
    val endpointPath: String,
)
