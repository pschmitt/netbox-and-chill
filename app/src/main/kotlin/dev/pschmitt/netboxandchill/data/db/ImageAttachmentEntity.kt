package dev.pschmitt.netboxandchill.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_attachments")
data class ImageAttachmentEntity(
    @PrimaryKey val id: Int,
    val objectType: String,
    val objectId: Int,
    val name: String?,
    val imageUrl: String?,
    val syncedAt: Long,
)
