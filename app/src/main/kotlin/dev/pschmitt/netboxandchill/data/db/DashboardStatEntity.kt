package dev.pschmitt.netboxandchill.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache for a handful of simple `count` stat tiles on the dashboard (NBC-9) - one row per NetBox
 * list endpoint checked, e.g. `api/dcim/devices/` -> 382. See
 * [dev.pschmitt.netboxandchill.data.repository.DashboardRepository].
 */
@Entity(tableName = "dashboard_stats")
data class DashboardStatEntity(
    @PrimaryKey val endpointPath: String,
    val label: String,
    val count: Int,
    val syncedAt: Long,
)
