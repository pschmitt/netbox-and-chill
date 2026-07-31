package dev.pschmitt.netboxandchill.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentVisitDao {
    @Query("SELECT * FROM recent_visits ORDER BY visitedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 12): Flow<List<RecentVisitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(visit: RecentVisitEntity)

    @Query(
        "DELETE FROM recent_visits WHERE visitedAt < " +
            "(SELECT visitedAt FROM recent_visits ORDER BY visitedAt DESC LIMIT 1 OFFSET :keep)"
    )
    suspend fun prune(keep: Int = 50)
}
