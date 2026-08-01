package dev.pschmitt.netboxandchill.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingEditDao {
    @Query("SELECT * FROM pending_edits WHERE state = 'conflict' ORDER BY createdAt")
    fun observeConflicts(): Flow<List<PendingEditEntity>>

    @Query("SELECT COUNT(*) FROM pending_edits WHERE state = 'conflict'")
    fun observeConflictCount(): Flow<Int>

    @Query("SELECT * FROM pending_edits WHERE state = 'queued' ORDER BY createdAt")
    suspend fun getQueued(): List<PendingEditEntity>

    @Query("SELECT * FROM pending_edits WHERE endpointPath = :endpointPath AND id = :id LIMIT 1")
    suspend fun get(endpointPath: String, id: Int): PendingEditEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(edit: PendingEditEntity)

    @Query("DELETE FROM pending_edits WHERE endpointPath = :endpointPath AND id = :id")
    suspend fun delete(endpointPath: String, id: Int)
}
