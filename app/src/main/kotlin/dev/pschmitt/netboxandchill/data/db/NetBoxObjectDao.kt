package dev.pschmitt.netboxandchill.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NetBoxObjectDao {
    @Query("SELECT * FROM netbox_objects WHERE endpointPath = :endpointPath ORDER BY display COLLATE NOCASE")
    fun observeAll(endpointPath: String): Flow<List<NetBoxObjectEntity>>

    @Query(
        """
        SELECT * FROM netbox_objects
        WHERE endpointPath = :endpointPath AND display LIKE '%' || :query || '%'
        ORDER BY display COLLATE NOCASE
        """
    )
    fun search(endpointPath: String, query: String): Flow<List<NetBoxObjectEntity>>

    @Query("SELECT * FROM netbox_objects WHERE endpointPath = :endpointPath AND id = :id")
    fun observeById(endpointPath: String, id: Int): Flow<NetBoxObjectEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(objects: List<NetBoxObjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(obj: NetBoxObjectEntity)

    @Query("SELECT COUNT(*) FROM netbox_objects WHERE endpointPath = :endpointPath")
    suspend fun count(endpointPath: String): Int
}
