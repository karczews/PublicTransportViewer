package com.github.karczews.publictransportviewer.data.repository

import com.github.karczews.publictransportviewer.data.db.dao.RouteDao
import com.github.karczews.publictransportviewer.data.db.dao.StopDao
import com.github.karczews.publictransportviewer.data.db.dao.StopTimeDao
import com.github.karczews.publictransportviewer.data.db.dao.TripDao
import com.github.karczews.publictransportviewer.data.db.entity.RouteEntity
import com.github.karczews.publictransportviewer.data.db.entity.StopEntity
import com.github.karczews.publictransportviewer.data.db.entity.StopTimeEntity
import com.github.karczews.publictransportviewer.data.db.entity.TripEntity
import com.github.karczews.publictransportviewer.data.model.ScheduleRelationship
import com.github.karczews.publictransportviewer.data.model.StopTimeUpdate
import com.github.karczews.publictransportviewer.data.model.TripUpdate
import com.github.karczews.publictransportviewer.data.model.VehicleType
import com.github.karczews.publictransportviewer.data.source.TripUpdateDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class DefaultStopRepositoryTest {

    private fun clockAt(hour: Int, minute: Int, second: Int = 0): () -> Calendar = {
        GregorianCalendar().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
        }
    }

    private val route = RouteEntity("R1", null, "10", "Route Ten", 0, "CE1124", null)
    private val trip = TripEntity("T1", "R1", "WD", "Chocianowice", 0, null)

    private fun stopTime(
        tripId: String = "T1",
        stopId: String = "S1",
        seq: Int = 1,
        departure: String = "12:30:00",
    ) = StopTimeEntity(tripId, seq, stopId, departure, departure, null, null)

    @Test
    fun `parseGtfsTime parses standard time`() {
        assertEquals(45000, DefaultStopRepository.parseGtfsTime("12:30:00"))
    }

    @Test
    fun `parseGtfsTime handles times past midnight`() {
        val result = DefaultStopRepository.parseGtfsTime("25:30:00")
        assertEquals(25 * 3600 + 30 * 60, result)
    }

    @Test
    fun `parseGtfsTime returns null for invalid format`() {
        assertNull(DefaultStopRepository.parseGtfsTime("invalid"))
        assertNull(DefaultStopRepository.parseGtfsTime("12:30"))
        assertNull(DefaultStopRepository.parseGtfsTime(""))
    }

    @Test
    fun `searchStops returns empty for blank query`() = runTest {
        val repo = buildRepo()
        val results = repo.searchStops("  ")
        assertEquals(0, results.size)
    }

    @Test
    fun `searchStops trims and delegates to DAO`() = runTest {
        val stop = StopEntity("S1", "Piotrkowska", 51.76, 19.46, null, 0, null)
        val repo = buildRepo(stops = listOf(stop))
        val results = repo.searchStops("  Piotrkowska  ")
        assertEquals(1, results.size)
        assertEquals("Piotrkowska", results[0].stopName)
    }

    @Test
    fun `builds departures within 60 minute window`() = runTest {
        val repo = buildRepo(
            stopTimes = listOf(
                stopTime(departure = "12:30:00"),
                stopTime(departure = "14:00:00", seq = 2),
            ),
            routes = mapOf("R1" to route),
            trips = mapOf("T1" to trip),
            clock = clockAt(12, 0),
        )

        val departures = repo.observeDeparturesForStop("S1").first()

        assertEquals(1, departures.size)
        assertEquals("10", departures[0].routeShortName)
        assertEquals("Chocianowice", departures[0].tripHeadsign)
        assertEquals(VehicleType.TRAM, departures[0].vehicleType)
    }

    @Test
    fun `excludes departures before current time`() = runTest {
        val repo = buildRepo(
            stopTimes = listOf(stopTime(departure = "08:00:00")),
            routes = mapOf("R1" to route),
            trips = mapOf("T1" to trip),
            clock = clockAt(12, 0),
        )

        val departures = repo.observeDeparturesForStop("S1").first()
        assertEquals(0, departures.size)
    }

    @Test
    fun `merges real-time delay from trip updates`() = runTest {
        val tripUpdate = TripUpdate(
            tripId = "T1",
            routeId = "R1",
            vehicleId = "V1",
            timestamp = 1000L,
            stopTimeUpdates = listOf(
                StopTimeUpdate(
                    stopSequence = 1,
                    stopId = "S1",
                    arrivalDelaySeconds = 120,
                    departureDelaySeconds = 150,
                    scheduleRelationship = ScheduleRelationship.SCHEDULED,
                )
            ),
        )
        val repo = buildRepo(
            stopTimes = listOf(stopTime(departure = "12:30:00")),
            routes = mapOf("R1" to route),
            trips = mapOf("T1" to trip),
            tripUpdates = listOf(tripUpdate),
            clock = clockAt(12, 0),
        )

        val departures = repo.observeDeparturesForStop("S1").first()

        assertEquals(1, departures.size)
        assertEquals(150, departures[0].delaySeconds)
    }

    @Test
    fun `falls back to arrival delay when departure delay is null`() = runTest {
        val tripUpdate = TripUpdate(
            tripId = "T1",
            routeId = "R1",
            vehicleId = null,
            timestamp = 1000L,
            stopTimeUpdates = listOf(
                StopTimeUpdate(
                    stopSequence = 1,
                    stopId = "S1",
                    arrivalDelaySeconds = 90,
                    departureDelaySeconds = null,
                    scheduleRelationship = ScheduleRelationship.SCHEDULED,
                )
            ),
        )
        val repo = buildRepo(
            stopTimes = listOf(stopTime(departure = "12:30:00")),
            routes = mapOf("R1" to route),
            trips = mapOf("T1" to trip),
            tripUpdates = listOf(tripUpdate),
            clock = clockAt(12, 0),
        )

        val departures = repo.observeDeparturesForStop("S1").first()
        assertEquals(90, departures[0].delaySeconds)
    }

    @Test
    fun `sorts departures by predicted time`() = runTest {
        val trip2 = TripEntity("T2", "R1", "WD", "Helenówek", 1, null)
        val tripUpdate = TripUpdate(
            tripId = "T1", routeId = "R1", vehicleId = null, timestamp = 1000L,
            stopTimeUpdates = listOf(
                StopTimeUpdate(1, "S1", null, 600, ScheduleRelationship.SCHEDULED)
            ),
        )
        val repo = buildRepo(
            stopTimes = listOf(
                stopTime(tripId = "T1", departure = "12:00:00", seq = 1),
                stopTime(tripId = "T2", departure = "12:05:00", seq = 2),
            ),
            routes = mapOf("R1" to route),
            trips = mapOf("T1" to trip, "T2" to trip2),
            tripUpdates = listOf(tripUpdate),
            clock = clockAt(11, 50),
        )

        val departures = repo.observeDeparturesForStop("S1").first()

        assertEquals(2, departures.size)
        assertEquals("Helenówek", departures[0].tripHeadsign)
        assertEquals("Chocianowice", departures[1].tripHeadsign)
    }

    @Test
    fun `returns empty when no stop times exist`() = runTest {
        val repo = buildRepo(clock = clockAt(12, 0))
        val departures = repo.observeDeparturesForStop("S1").first()
        assertEquals(0, departures.size)
    }

    @Test
    fun `includes route color in departure`() = runTest {
        val repo = buildRepo(
            stopTimes = listOf(stopTime(departure = "12:30:00")),
            routes = mapOf("R1" to route),
            trips = mapOf("T1" to trip),
            clock = clockAt(12, 0),
        )

        val departures = repo.observeDeparturesForStop("S1").first()
        assertEquals("CE1124", departures[0].routeColor)
    }

    private fun buildRepo(
        stops: List<StopEntity> = emptyList(),
        stopTimes: List<StopTimeEntity> = emptyList(),
        routes: Map<String, RouteEntity> = emptyMap(),
        trips: Map<String, TripEntity> = emptyMap(),
        tripUpdates: List<TripUpdate> = emptyList(),
        clock: () -> Calendar = clockAt(12, 0),
    ): DefaultStopRepository = DefaultStopRepository(
        stopDao = FakeStopDao(stops),
        stopTimeDao = FakeStopTimeDao(stopTimes),
        tripDao = FakeTripDao(trips),
        routeDao = FakeRouteDao(routes),
        tripUpdateDataSource = FakeTripUpdateDataSource(tripUpdates),
        clock = clock,
    )
}

private class FakeStopDao(private val stops: List<StopEntity>) : StopDao {
    override suspend fun getStopById(stopId: String) = stops.find { it.stopId == stopId }
    override suspend fun searchStopsByName(query: String) = stops.filter { query in it.stopName }
    override suspend fun insertAll(stops: List<StopEntity>) {}
    override suspend fun deleteAll() {}
}

private class FakeStopTimeDao(private val stopTimes: List<StopTimeEntity>) : StopTimeDao {
    override suspend fun getStopTimesForTrip(tripId: String) =
        stopTimes.filter { it.tripId == tripId }.sortedBy { it.stopSequence }
    override suspend fun getStopTimesForStop(stopId: String) =
        stopTimes.filter { it.stopId == stopId }.sortedBy { it.departureTime }
    override suspend fun insertAll(stopTimes: List<StopTimeEntity>) {}
    override suspend fun deleteAll() {}
}

private class FakeRouteDao(private val routes: Map<String, RouteEntity>) : RouteDao {
    override suspend fun getRouteById(routeId: String) = routes[routeId]
    override suspend fun getAllRoutes() = routes.values.toList()
    override suspend fun insertAll(routes: List<RouteEntity>) {}
    override suspend fun deleteAll() {}
}

private class FakeTripDao(private val trips: Map<String, TripEntity>) : TripDao {
    override suspend fun getTripById(tripId: String) = trips[tripId]
    override suspend fun getTripsByRouteId(routeId: String) =
        trips.values.filter { it.routeId == routeId }
    override suspend fun insertAll(trips: List<TripEntity>) {}
    override suspend fun deleteAll() {}
}

private class FakeTripUpdateDataSource(
    private val updates: List<TripUpdate>,
) : com.github.karczews.publictransportviewer.data.source.TripUpdateDataSource {
    override suspend fun getTripUpdates(): List<TripUpdate> = updates
}
