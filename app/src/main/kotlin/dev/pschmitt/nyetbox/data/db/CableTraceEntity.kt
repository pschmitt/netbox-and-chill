package dev.pschmitt.nyetbox.data.db

import androidx.room.Entity

/**
 * One `[nearEnds, cable, farEnds]` segment from a NetBox cable-trace response
 * (`api/dcim/<component>/<id>/trace/`), keyed by the termination it was traced
 * from - [traceEndpointPath]/[traceObjectId] - since the trace endpoint lives on the termination,
 * not the cable itself. [json] is the raw serialized 3-element array for that segment.
 */
@Entity(
    tableName = "cable_trace_segments",
    primaryKeys = ["traceEndpointPath", "traceObjectId", "segmentIndex"],
)
data class CableTraceEntity(
    val traceEndpointPath: String,
    val traceObjectId: Int,
    val segmentIndex: Int,
    val json: String,
    val syncedAt: Long,
)
