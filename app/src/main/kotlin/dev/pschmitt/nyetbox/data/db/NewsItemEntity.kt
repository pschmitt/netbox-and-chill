package dev.pschmitt.nyetbox.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cached item from the public NetBox Labs RSS feed, used by the dashboard news section. */
@Entity(tableName = "news_items")
data class NewsItemEntity(
    @PrimaryKey val guid: String,
    val title: String,
    val link: String,
    val summary: String?,
    val publishedAt: Long,
    val syncedAt: Long,
)
