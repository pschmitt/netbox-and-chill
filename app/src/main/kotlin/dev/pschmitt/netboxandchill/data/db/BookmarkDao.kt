package dev.pschmitt.netboxandchill.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY created DESC") fun observeAll(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(bookmarks: List<BookmarkEntity>)

    @Query("DELETE FROM bookmarks") suspend fun clear()

    /** Bookmarks are a small, fully-fetched set each refresh - clear+replace rather than a plain
     * upsert so a bookmark removed server-side actually disappears from the cache too. */
    @Transaction
    suspend fun replaceAll(bookmarks: List<BookmarkEntity>) {
        clear()
        upsertAll(bookmarks)
    }
}
