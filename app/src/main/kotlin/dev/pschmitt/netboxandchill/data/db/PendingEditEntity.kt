package dev.pschmitt.netboxandchill.data.db

import androidx.room.Entity

/** Durable generic-object edit outbox and conflict snapshot (NBC-32). */
@Entity(tableName = "pending_edits", primaryKeys = ["endpointPath", "id"])
data class PendingEditEntity(
    val endpointPath: String,
    val id: Int,
    val baseJson: String,
    val localJson: String,
    val patchJson: String,
    val state: String,
    val serverJson: String?,
    val createdAt: Long,
) {
    companion object {
        const val QUEUED = "queued"
        const val CONFLICT = "conflict"
    }
}
