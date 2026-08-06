package dev.pschmitt.nyetbox.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CableTraceDao {
    @Query(
        """
        SELECT * FROM cable_trace_segments
        WHERE traceEndpointPath = :traceEndpointPath AND traceObjectId = :traceObjectId
        ORDER BY segmentIndex ASC
        """
    )
    fun observe(traceEndpointPath: String, traceObjectId: Int): Flow<List<CableTraceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(segments: List<CableTraceEntity>)

    @Query(
        "DELETE FROM cable_trace_segments WHERE traceEndpointPath = :traceEndpointPath " +
            "AND traceObjectId = :traceObjectId"
    )
    suspend fun clear(traceEndpointPath: String, traceObjectId: Int)
}
