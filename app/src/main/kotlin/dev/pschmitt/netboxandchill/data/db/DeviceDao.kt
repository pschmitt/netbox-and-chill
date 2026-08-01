package dev.pschmitt.netboxandchill.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<DeviceEntity>>

    @Query(
        """
        SELECT * FROM devices
        WHERE name LIKE '%' || :query || '%'
           OR serial LIKE '%' || :query || '%'
           OR assetTag LIKE '%' || :query || '%'
           OR primaryIp LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE
        """
    )
    fun search(query: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id") fun observeById(id: Int): Flow<DeviceEntity?>

    @Query("SELECT * FROM devices WHERE id = :id") suspend fun getById(id: Int): DeviceEntity?

    @Query(
        """
        SELECT * FROM devices
        WHERE lower(assetTag) = lower(:assetTag)
           OR lower(assetTag) = lower(:assetTagWithoutPrefix)
        LIMIT 1
        """
    )
    suspend fun getByAssetTag(assetTag: String, assetTagWithoutPrefix: String): DeviceEntity?

    @Query("SELECT * FROM devices") suspend fun getAll(): List<DeviceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(devices: List<DeviceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(device: DeviceEntity)

    @Query("DELETE FROM devices") suspend fun clear()

    @Query("SELECT COUNT(*) FROM devices") suspend fun count(): Int
}
