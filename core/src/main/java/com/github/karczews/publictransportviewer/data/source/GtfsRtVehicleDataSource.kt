package com.github.karczews.publictransportviewer.data.source

import com.github.karczews.publictransportviewer.data.model.VehiclePosition
import com.github.karczews.publictransportviewer.data.model.VehicleStatus
import com.github.karczews.publictransportviewer.data.model.VehicleType
import com.github.karczews.publictransportviewer.data.network.GtfsRtApi
import com.google.transit.realtime.GtfsRealtime

interface VehicleDataSource {
    suspend fun getVehiclePositions(): List<VehiclePosition>
}

class GtfsRtVehicleDataSource(private val api: GtfsRtApi) : VehicleDataSource {

    override suspend fun getVehiclePositions(): List<VehiclePosition> {
        val feedMessage = api.fetchVehiclePositions().getOrThrow()
        return feedMessage.entityList
            .filter { it.hasVehicle() }
            .map { entity -> mapToVehiclePosition(entity.vehicle) }
    }

    private fun mapToVehiclePosition(
        vehicle: GtfsRealtime.VehiclePosition,
    ): VehiclePosition {
        val position = vehicle.position
        val trip = if (vehicle.hasTrip()) vehicle.trip else null
        val descriptor = if (vehicle.hasVehicle()) vehicle.vehicle else null
        val routeId = trip?.routeId.orEmpty()

        return VehiclePosition(
            vehicleId = descriptor?.id ?: vehicle.hashCode().toString(),
            routeId = routeId,
            tripId = trip?.tripId,
            label = descriptor?.label ?: routeId,
            latitude = position.latitude.toDouble(),
            longitude = position.longitude.toDouble(),
            bearing = if (position.hasBearing()) position.bearing else 0f,
            speed = if (position.hasSpeed()) position.speed else 0f,
            timestamp = vehicle.timestamp,
            vehicleType = inferVehicleType(routeId),
            currentStatus = mapStatus(vehicle.currentStatus),
            stopId = if (vehicle.hasStopId()) vehicle.stopId else null,
        )
    }

    private fun inferVehicleType(routeId: String): VehicleType {
        val routeNum = routeId.toIntOrNull()
        return if (routeNum != null && routeNum in 1..46) VehicleType.TRAM else VehicleType.BUS
    }

    private fun mapStatus(
        status: GtfsRealtime.VehiclePosition.VehicleStopStatus,
    ): VehicleStatus = when (status) {
        GtfsRealtime.VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO -> VehicleStatus.IN_TRANSIT_TO
        GtfsRealtime.VehiclePosition.VehicleStopStatus.STOPPED_AT -> VehicleStatus.STOPPED_AT
        GtfsRealtime.VehiclePosition.VehicleStopStatus.INCOMING_AT -> VehicleStatus.INCOMING_AT
    }
}
