package com.github.karczews.publictransportviewer.data.repository

import com.github.karczews.publictransportviewer.data.db.dao.RouteDao
import com.github.karczews.publictransportviewer.data.db.dao.TripDao
import com.github.karczews.publictransportviewer.data.model.VehiclePosition
import com.github.karczews.publictransportviewer.data.model.VehicleType
import com.github.karczews.publictransportviewer.data.source.VehicleDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

interface VehicleRepository {
    fun observeVehiclePositions(): Flow<List<VehiclePosition>>
}

class DefaultVehicleRepository(
    private val dataSource: VehicleDataSource,
    private val routeDao: RouteDao,
    private val tripDao: TripDao,
) : VehicleRepository {

    private var lastKnownPositions: List<VehiclePosition> = emptyList()

    override fun observeVehiclePositions(): Flow<List<VehiclePosition>> = flow {
        while (true) {
            val positions =
                try {
                    val raw = dataSource.getVehiclePositions()
                    enrichPositions(raw).also { lastKnownPositions = it }
                } catch (_: Exception) {
                    lastKnownPositions
                }
            emit(positions)
            delay(POLL_INTERVAL_MS)
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    private suspend fun enrichPositions(
        positions: List<VehiclePosition>,
    ): List<VehiclePosition> {
        if (positions.isEmpty()) return emptyList()
        val routeIds = positions.map { it.routeId }.distinct()
        val routes =
            if (routeIds.isEmpty()) emptyMap() else routeDao.getRoutesByIds(routeIds).associateBy { it.routeId }

        val tripIds = positions.mapNotNull { it.tripId }.distinct()
        val trips = if (tripIds.isEmpty()) emptyMap() else tripDao.getTripsByIds(tripIds).associateBy { it.tripId }

        return positions.map { vehicle ->
            val route = routes[vehicle.routeId]
            val trip = vehicle.tripId?.let { trips[it] }
            if (route != null) {
                vehicle.copy(
                    routeShortName = route.routeShortName,
                    routeColor = route.routeColor,
                    tripHeadsign = trip?.tripHeadsign ?: vehicle.tripHeadsign,
                    vehicleType =
                        when (route.routeType) {
                            0 -> VehicleType.TRAM
                            else -> VehicleType.BUS
                        },
                )
            } else {
                vehicle
            }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
