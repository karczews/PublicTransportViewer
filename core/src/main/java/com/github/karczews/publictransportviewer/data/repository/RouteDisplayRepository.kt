package com.github.karczews.publictransportviewer.data.repository

import com.github.karczews.publictransportviewer.data.db.dao.RouteDao
import com.github.karczews.publictransportviewer.data.db.dao.ShapePointDao
import com.github.karczews.publictransportviewer.data.db.dao.StopDao
import com.github.karczews.publictransportviewer.data.db.dao.StopTimeDao
import com.github.karczews.publictransportviewer.data.db.dao.TripDao
import com.github.karczews.publictransportviewer.data.model.LatLon
import com.github.karczews.publictransportviewer.data.model.RouteDisplayData
import com.github.karczews.publictransportviewer.data.model.RouteStop
import androidx.core.graphics.toColorInt

interface RouteDisplayRepository {
    suspend fun loadRouteForTrip(tripId: String): RouteDisplayData?
}

class DefaultRouteDisplayRepository(
    private val tripDao: TripDao,
    private val routeDao: RouteDao,
    private val shapePointDao: ShapePointDao,
    private val stopTimeDao: StopTimeDao,
    private val stopDao: StopDao,
) : RouteDisplayRepository {

    override suspend fun loadRouteForTrip(tripId: String): RouteDisplayData? {
        val trip = tripDao.getTripById(tripId) ?: return null
        val route = routeDao.getRouteById(trip.routeId) ?: return null

        val polylinePoints = trip.shapeId?.let { shapeId ->
            shapePointDao.getShapePoints(shapeId).map { LatLon(it.shapePtLat, it.shapePtLon) }
        } ?: emptyList()

        val stopTimes = stopTimeDao.getStopTimesForTrip(tripId)
        val stopIds = stopTimes.map { it.stopId }.distinct()
        val stopsById = stopIds.mapNotNull { id -> stopDao.getStopById(id)?.let { id to it } }.toMap()

        val routeStops = stopTimes.mapNotNull { st ->
            val stop = stopsById[st.stopId] ?: return@mapNotNull null
            RouteStop(
                stopId = stop.stopId,
                stopName = stop.stopName,
                lat = stop.stopLat,
                lon = stop.stopLon,
            )
        }.distinctBy { it.stopId }

        val colorInt = parseRouteColor(route.routeColor)

        return RouteDisplayData(
            routeId = route.routeId,
            routeShortName = route.routeShortName,
            routeColor = colorInt,
            polylinePoints = polylinePoints,
            stops = routeStops,
        )
    }

    private fun parseRouteColor(hex: String?): Int {
        if (hex.isNullOrBlank()) return DEFAULT_COLOR
        return runCatching {
            val cleaned = hex.removePrefix("#")
            "#$cleaned".toColorInt()
        }.getOrElse { DEFAULT_COLOR }
    }

    companion object {
        private const val DEFAULT_COLOR = 0xFF1565C0.toInt()
    }
}
