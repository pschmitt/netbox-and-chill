package dev.pschmitt.nyetbox.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache for NetBox's auto-generated changelog (`GET /api/core/object-changes/` - moved here from
 * `/api/extras/` in NetBox 4.0, confirmed against a real 4.5 instance) - see
 * [dev.pschmitt.nyetbox.data.repository.DashboardRepository]. [targetEndpointPath]/
 * [targetId] are null when the changed object no longer exists (a delete-action row has no
 * `changed_object` to derive them from) - such rows render non-navigable.
 */
@Entity(tableName = "object_changes")
data class ObjectChangeEntity(
    @PrimaryKey val id: Int,
    val time: String,
    val userDisplay: String,
    val actionValue: String,
    val actionLabel: String,
    val objectRepr: String,
    val targetEndpointPath: String?,
    val targetId: Int?,
    val syncedAt: Long,
)
