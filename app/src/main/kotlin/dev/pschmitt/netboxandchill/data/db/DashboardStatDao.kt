package dev.pschmitt.netboxandchill.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DashboardStatDao {
    @Query("SELECT * FROM dashboard_stats ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<DashboardStatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(stats: List<DashboardStatEntity>)
}
