package dev.pschmitt.netboxandchill.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ObjectChangeDao {
    @Query("SELECT * FROM object_changes ORDER BY time DESC") fun observeAll(): Flow<List<ObjectChangeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(changes: List<ObjectChangeEntity>)

    @Query("DELETE FROM object_changes") suspend fun clear()

    /** Only the most-recent page is ever fetched (see `DashboardRepository`) - clear+replace so
     * older rows scrolled out of that page don't linger forever. */
    @Transaction
    suspend fun replaceAll(changes: List<ObjectChangeEntity>) {
        clear()
        upsertAll(changes)
    }
}
