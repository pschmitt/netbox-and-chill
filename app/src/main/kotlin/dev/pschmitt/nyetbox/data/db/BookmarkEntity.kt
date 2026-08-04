package dev.pschmitt.nyetbox.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache for the signed-in user's NetBox bookmarks (`GET /api/extras/bookmarks/`, NetBox 3.5+) - see
 * [dev.pschmitt.nyetbox.data.repository.DashboardRepository]. [targetEndpointPath]/ [targetId] are
 * derived from the bookmarked object's own `url` (same "id"+"url" reference shape NBC-6's generic
 * field renderer already knows how to navigate to), so they're nullable in the unlikely case a
 * bookmark's target object couldn't be resolved.
 */
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: Int,
    val display: String,
    val objectType: String,
    val targetEndpointPath: String?,
    val targetId: Int?,
    val created: String,
    val syncedAt: Long,
)
