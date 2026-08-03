package dev.pschmitt.nyetbox.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomFieldDao {
    @Query(
        "SELECT * FROM custom_fields ORDER BY groupName COLLATE NOCASE, weight, label COLLATE NOCASE, name COLLATE NOCASE"
    )
    fun observeAll(): Flow<List<CustomFieldEntity>>

    @Query("SELECT name FROM custom_fields WHERE type = 'markdown' ORDER BY name")
    fun observeMarkdownNames(): Flow<List<String>>

    @Query("DELETE FROM custom_fields WHERE name = :name")
    suspend fun delete(name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(fields: List<CustomFieldEntity>)

    @Query("DELETE FROM custom_fields") suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(fields: List<CustomFieldEntity>) {
        deleteAll()
        upsertAll(fields)
    }
}
