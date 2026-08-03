package dev.pschmitt.nyetbox.data.db

import androidx.room.Entity

/** A small durable breadcrumb for the global-search landing page. */
@Entity(tableName = "recent_visits", primaryKeys = ["endpointPath", "id"])
data class RecentVisitEntity(
    val endpointPath: String,
    val id: Int,
    val display: String,
    val secondaryLine: String?,
    val visitedAt: Long,
)
