package dev.pschmitt.netboxandchill.data.db

import androidx.room.Entity

/** Durable generic-object mutation outbox and conflict snapshot (NBC-32/NBC-145). */
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
        /** A queued POST; [id] is a negative local-only cache ID until reconciliation. */
        const val CREATE_QUEUED = "create_queued"
    }
}
