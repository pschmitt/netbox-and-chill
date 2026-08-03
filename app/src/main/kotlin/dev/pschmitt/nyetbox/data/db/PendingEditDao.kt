package dev.pschmitt.nyetbox.data.db

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

    @Query(
        "SELECT * FROM pending_edits WHERE state IN ('queued', 'create_queued', 'delete_queued') ORDER BY createdAt"
    )
    fun observeQueuedMutations(): Flow<List<PendingEditEntity>>

    @Query(
        "SELECT COUNT(*) FROM pending_edits WHERE state IN ('queued', 'create_queued', 'delete_queued')"
    )
    fun observeQueuedMutationCount(): Flow<Int>

    @Query(
        "SELECT * FROM pending_edits WHERE state IN ('queued', 'create_queued', 'delete_queued') ORDER BY createdAt"
    )
    suspend fun getQueuedMutations(): List<PendingEditEntity>

    @Query("SELECT * FROM pending_edits WHERE state = 'queued' ORDER BY createdAt")
    suspend fun getQueuedEdits(): List<PendingEditEntity>

    /** Offline creates share the durable outbox table with edits to avoid a schema migration. */
    @Query("SELECT * FROM pending_edits WHERE state = 'create_queued' ORDER BY createdAt")
    suspend fun getQueuedCreates(): List<PendingEditEntity>

    @Query("SELECT * FROM pending_edits WHERE state = 'delete_queued' ORDER BY createdAt")
    suspend fun getQueuedDeletes(): List<PendingEditEntity>

    @Query("SELECT * FROM pending_edits WHERE endpointPath = :endpointPath AND id = :id LIMIT 1")
    suspend fun get(endpointPath: String, id: Int): PendingEditEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(edit: PendingEditEntity)

    @Query("DELETE FROM pending_edits WHERE endpointPath = :endpointPath AND id = :id")
    suspend fun delete(endpointPath: String, id: Int)
}
