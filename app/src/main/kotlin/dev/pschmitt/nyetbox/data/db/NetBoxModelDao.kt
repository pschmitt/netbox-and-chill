package dev.pschmitt.nyetbox.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NetBoxModelDao {
    @Query(
        "SELECT * FROM netbox_models ORDER BY appLabel COLLATE NOCASE, modelLabel COLLATE NOCASE"
    )
    fun observeAll(): Flow<List<NetBoxModelEntity>>

    @Query("SELECT * FROM netbox_models WHERE endpointPath IN (:endpointPaths)")
    fun observeByPaths(endpointPaths: Set<String>): Flow<List<NetBoxModelEntity>>

    @Query("SELECT COUNT(*) FROM netbox_models") suspend fun count(): Int

    @Query("SELECT * FROM netbox_models") suspend fun getAll(): List<NetBoxModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(models: List<NetBoxModelEntity>)

    @Query("DELETE FROM netbox_models") suspend fun clear()
}
