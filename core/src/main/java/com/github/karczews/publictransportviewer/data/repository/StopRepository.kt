package com.github.karczews.publictransportviewer.data.repository

import com.github.karczews.publictransportviewer.data.db.dao.RouteDao
import com.github.karczews.publictransportviewer.data.db.dao.StopDao
import com.github.karczews.publictransportviewer.data.db.dao.StopTimeDao
import com.github.karczews.publictransportviewer.data.db.dao.TripDao
import com.github.karczews.publictransportviewer.data.db.entity.StopEntity
import com.github.karczews.publictransportviewer.data.model.StopDeparture
import com.github.karczews.publictransportviewer.data.model.TripUpdate
import com.github.karczews.publictransportviewer.data.model.VehicleType
import com.github.karczews.publictransportviewer.data.source.TripUpdateDataSource
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface StopRepository {
  suspend fun searchStops(query: String): List<StopEntity>

  fun observeDeparturesForStop(stopId: String): Flow<List<StopDeparture>>
}

class DefaultStopRepository(
  private val stopDao: StopDao,
  private val stopTimeDao: StopTimeDao,
  private val tripDao: TripDao,
  private val routeDao: RouteDao,
  private val tripUpdateDataSource: TripUpdateDataSource,
  private val clock: () -> Calendar = { Calendar.getInstance() },
) : StopRepository {

  private var lastTripUpdates: List<TripUpdate> = emptyList()

  override suspend fun searchStops(query: String): List<StopEntity> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    return stopDao.searchStopsByName(trimmed)
  }

  override fun observeDeparturesForStop(stopId: String): Flow<List<StopDeparture>> = flow {
    while (true) {
      val tripUpdates = fetchTripUpdatesOrCached()
      emit(buildDepartures(stopId, tripUpdates))
      delay(POLL_INTERVAL_MS)
    }
  }

  private suspend fun fetchTripUpdatesOrCached(): List<TripUpdate> =
    try {
      tripUpdateDataSource.getTripUpdates().also { lastTripUpdates = it }
    } catch (_: Exception) {
      lastTripUpdates
    }

  private suspend fun buildDepartures(
    stopId: String,
    tripUpdates: List<TripUpdate>,
  ): List<StopDeparture> {
    val stopTimes = stopTimeDao.getStopTimesForStop(stopId)
    if (stopTimes.isEmpty()) return emptyList()

    val nowSeconds = currentSecondsOfDay()
    val upperBound = nowSeconds + WINDOW_SECONDS

    val candidates = stopTimes.mapNotNull { st ->
      val depSeconds = parseGtfsTime(st.departureTime) ?: return@mapNotNull null
      if (depSeconds in nowSeconds..upperBound) st to depSeconds else null
    }
    if (candidates.isEmpty()) return emptyList()

    val tripIds = candidates.map { it.first.tripId }.distinct()
    val trips = tripIds.mapNotNull { id -> tripDao.getTripById(id)?.let { id to it } }.toMap()
    val routeIds = trips.values.map { it.routeId }.distinct()
    val routes = routeIds.mapNotNull { id -> routeDao.getRouteById(id)?.let { id to it } }.toMap()

    val delayByTripId = tripUpdates.associate { update ->
      val delay =
        update.stopTimeUpdates
          .firstOrNull { it.stopId == stopId }
          ?.let { it.departureDelaySeconds ?: it.arrivalDelaySeconds }
      update.tripId to delay
    }

    return candidates
      .mapNotNull { (st, depSeconds) ->
        val trip = trips[st.tripId] ?: return@mapNotNull null
        val route = routes[trip.routeId] ?: return@mapNotNull null
        StopDeparture(
          tripId = trip.tripId,
          routeId = route.routeId,
          routeShortName = route.routeShortName,
          tripHeadsign = trip.tripHeadsign,
          scheduledDepartureSecondsOfDay = depSeconds,
          delaySeconds = delayByTripId[trip.tripId],
          routeColor = route.routeColor,
          vehicleType =
            if (route.routeType == TRAM_ROUTE_TYPE) VehicleType.TRAM else VehicleType.BUS,
        )
      }
      .sortedBy { it.scheduledDepartureSecondsOfDay + (it.delaySeconds ?: 0) }
      .take(MAX_DEPARTURES)
  }

  private fun currentSecondsOfDay(): Int {
    val cal = clock()
    return cal.get(Calendar.HOUR_OF_DAY) * SECONDS_PER_HOUR +
      cal.get(Calendar.MINUTE) * SECONDS_PER_MINUTE +
      cal.get(Calendar.SECOND)
  }

  companion object {
    private const val POLL_INTERVAL_MS = 15_000L
    private const val WINDOW_SECONDS = 60 * 60
    private const val MAX_DEPARTURES = 30
    private const val SECONDS_PER_HOUR = 3600
    private const val SECONDS_PER_MINUTE = 60
    private const val TRAM_ROUTE_TYPE = 0
    private const val GTFS_TIME_PART_COUNT = 3

    fun parseGtfsTime(time: String): Int? {
      val parts = time.split(":")
      if (parts.size != GTFS_TIME_PART_COUNT) return null
      val h = parts[0].toIntOrNull() ?: return null
      val m = parts[1].toIntOrNull() ?: return null
      val s = parts[2].toIntOrNull() ?: return null
      return h * SECONDS_PER_HOUR + m * SECONDS_PER_MINUTE + s
    }
  }
}
