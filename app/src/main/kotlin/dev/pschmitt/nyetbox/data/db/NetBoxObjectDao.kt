package dev.pschmitt.nyetbox.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NetBoxObjectDao {
    @Query(
        "SELECT * FROM netbox_objects WHERE endpointPath = :endpointPath ORDER BY display COLLATE NOCASE"
    )
    fun observeAll(endpointPath: String): Flow<List<NetBoxObjectEntity>>

    @Query(
        """
        SELECT * FROM netbox_objects
        WHERE endpointPath = :endpointPath AND display LIKE '%' || :query || '%'
        ORDER BY display COLLATE NOCASE
        """
    )
    fun search(endpointPath: String, query: String): Flow<List<NetBoxObjectEntity>>

    @Query(
        """
        SELECT * FROM netbox_objects
        WHERE endpointPath = :endpointPath
          AND (
              display LIKE '%' || :query || '%'
              OR secondaryLine LIKE '%' || :query || '%'
              OR json LIKE '%' || :query || '%'
          )
        ORDER BY display COLLATE NOCASE
        LIMIT :limit
        """
    )
    fun searchAllInEndpoint(
        endpointPath: String,
        query: String,
        limit: Int,
    ): Flow<List<NetBoxObjectEntity>>

    @Query("SELECT * FROM netbox_objects WHERE endpointPath = :endpointPath AND id = :id")
    fun observeById(endpointPath: String, id: Int): Flow<NetBoxObjectEntity?>

    @Query("SELECT * FROM netbox_objects ORDER BY endpointPath, display COLLATE NOCASE")
    fun observeAllObjects(): Flow<List<NetBoxObjectEntity>>

    @Query("SELECT * FROM netbox_objects WHERE endpointPath = :endpointPath AND id = :id")
    suspend fun getById(endpointPath: String, id: Int): NetBoxObjectEntity?

    @Query(
        "SELECT * FROM netbox_objects WHERE endpointPath = :endpointPath ORDER BY display COLLATE NOCASE"
    )
    suspend fun getAll(endpointPath: String): List<NetBoxObjectEntity>

    /**
     * Cross-endpoint search (NBC-13's global search) - unlike [search], not scoped to one model,
     * since the whole point is finding a match regardless of which endpoint it's cached under.
     */
    @Query(
        """
        SELECT * FROM netbox_objects
        WHERE display LIKE '%' || :query || '%'
           OR secondaryLine LIKE '%' || :query || '%'
           OR json LIKE '%' || :query || '%'
        ORDER BY display COLLATE NOCASE
        LIMIT :limit
        """
    )
    fun searchAll(query: String, limit: Int): Flow<List<NetBoxObjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(objects: List<NetBoxObjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(obj: NetBoxObjectEntity)

    @Query("DELETE FROM netbox_objects WHERE endpointPath = :endpointPath AND id = :id")
    suspend fun delete(endpointPath: String, id: Int)

    @Query("SELECT COUNT(*) FROM netbox_objects WHERE endpointPath = :endpointPath")
    suspend fun count(endpointPath: String): Int

    @Query("SELECT COUNT(*) FROM netbox_objects") suspend fun countAll(): Int

    @Query("SELECT * FROM netbox_objects") suspend fun getAll(): List<NetBoxObjectEntity>
}
