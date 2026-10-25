package com.github.karczews.publictransportviewer.data.model

data class TripUpdate(
    val tripId: String,
    val routeId: String?,
    val vehicleId: String?,
    val timestamp: Long,
    val stopTimeUpdates: List<StopTimeUpdate>,
)

data class StopTimeUpdate(
    val stopSequence: Int?,
    val stopId: String?,
    val arrivalDelaySeconds: Int?,
    val departureDelaySeconds: Int?,
    val scheduleRelationship: ScheduleRelationship,
)

enum class ScheduleRelationship {
    SCHEDULED,
    SKIPPED,
    NO_DATA,
    UNSCHEDULED,
}
