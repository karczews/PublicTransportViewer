package com.github.karczews.publictransportviewer.data.source

import com.github.karczews.publictransportviewer.data.model.ScheduleRelationship
import com.github.karczews.publictransportviewer.data.model.StopTimeUpdate
import com.github.karczews.publictransportviewer.data.model.TripUpdate
import com.github.karczews.publictransportviewer.data.network.GtfsRtApi
import com.google.transit.realtime.GtfsRealtime

interface TripUpdateDataSource {
    suspend fun getTripUpdates(): List<TripUpdate>
}

class GtfsRtTripUpdateDataSource(private val api: GtfsRtApi) : TripUpdateDataSource {

    override suspend fun getTripUpdates(): List<TripUpdate> {
        val feedMessage = api.fetchTripUpdates().getOrThrow()
        return feedMessage.entityList
            .filter { it.hasTripUpdate() }
            .map { entity -> mapTripUpdate(entity.tripUpdate) }
    }

    private fun mapTripUpdate(update: GtfsRealtime.TripUpdate): TripUpdate {
        val trip = update.trip
        val vehicle = if (update.hasVehicle()) update.vehicle else null
        return TripUpdate(
            tripId = trip.tripId.orEmpty(),
            routeId = if (trip.hasRouteId()) trip.routeId else null,
            vehicleId = vehicle?.id,
            timestamp = if (update.hasTimestamp()) update.timestamp else 0L,
            stopTimeUpdates = update.stopTimeUpdateList.map { mapStopTimeUpdate(it) },
        )
    }

    private fun mapStopTimeUpdate(
        stu: GtfsRealtime.TripUpdate.StopTimeUpdate,
    ): StopTimeUpdate = StopTimeUpdate(
        stopSequence = if (stu.hasStopSequence()) stu.stopSequence else null,
        stopId = if (stu.hasStopId()) stu.stopId else null,
        arrivalDelaySeconds = if (stu.hasArrival() && stu.arrival.hasDelay()) stu.arrival.delay else null,
        departureDelaySeconds = if (stu.hasDeparture() && stu.departure.hasDelay()) stu.departure.delay else null,
        scheduleRelationship = mapScheduleRelationship(stu.scheduleRelationship),
    )

    private fun mapScheduleRelationship(
        sr: GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship,
    ): ScheduleRelationship = when (sr) {
        GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SCHEDULED -> ScheduleRelationship.SCHEDULED
        GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED -> ScheduleRelationship.SKIPPED
        GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.NO_DATA -> ScheduleRelationship.NO_DATA
        GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.UNSCHEDULED -> ScheduleRelationship.UNSCHEDULED
    }
}
