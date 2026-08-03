package dev.pschmitt.nyetbox.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_attachments")
data class ImageAttachmentEntity(
    @PrimaryKey val id: Int,
    val objectType: String,
    val objectId: Int,
    val name: String?,
    /** Server-derived filename (e.g. from the upload) used when [name] is blank. */
    val display: String?,
    val imageUrl: String?,
    val description: String?,
    val imageHeight: Int?,
    val imageWidth: Int?,
    /** ISO-8601 timestamps as returned by NetBox - formatted for display where shown. */
    val created: String?,
    val lastUpdated: String?,
    val syncedAt: Long,
)
