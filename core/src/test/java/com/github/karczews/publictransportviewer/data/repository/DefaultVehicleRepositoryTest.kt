package com.github.karczews.publictransportviewer.data.repository

import com.github.karczews.publictransportviewer.data.db.dao.RouteDao
import com.github.karczews.publictransportviewer.data.db.dao.TripDao
import com.github.karczews.publictransportviewer.data.db.entity.RouteEntity
import com.github.karczews.publictransportviewer.data.db.entity.TripEntity
import com.github.karczews.publictransportviewer.data.model.VehiclePosition
import com.github.karczews.publictransportviewer.data.model.VehicleStatus
import com.github.karczews.publictransportviewer.data.model.VehicleType
import com.github.karczews.publictransportviewer.data.source.VehicleDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultVehicleRepositoryTest {

    private fun vehicle(
        vehicleId: String = "V1",
        routeId: String = "1",
        tripId: String? = "T1",
        vehicleType: VehicleType = VehicleType.TRAM,
    ) = VehiclePosition(
        vehicleId = vehicleId,
        routeId = routeId,
        tripId = tripId,
        label = routeId,
        latitude = 51.76,
        longitude = 19.46,
        bearing = 0f,
        speed = 0f,
        timestamp = 1000L,
        vehicleType = vehicleType,
        currentStatus = VehicleStatus.IN_TRANSIT_TO,
    )

    @Test
    fun `enriches vehicle with route name and color from Room`() = runTest {
        val route = RouteEntity(
            routeId = "1",
            agencyId = null,
            routeShortName = "1",
            routeLongName = "Route One",
            routeType = 0,
            routeColor = "CE1124",
            routeTextColor = null,
        )
        val trip = TripEntity(
            tripId = "T1",
            routeId = "1",
            serviceId = "WD",
            tripHeadsign = "Chocianowice IKEA",
            directionId = 0,
            shapeId = null,
        )
        val dataSource = FakeVehicleDataSource(listOf(vehicle()))
        val routeDao = VehicleTestRouteDao(mapOf("1" to route))
        val tripDao = VehicleTestTripDao(mapOf("T1" to trip))

        val repo = DefaultVehicleRepository(dataSource, routeDao, tripDao)
        val positions = repo.observeVehiclePositions().first()

        assertEquals(1, positions.size)
        assertEquals("1", positions[0].routeShortName)
        assertEquals("CE1124", positions[0].routeColor)
        assertEquals("Chocianowice IKEA", positions[0].tripHeadsign)
        assertEquals(VehicleType.TRAM, positions[0].vehicleType)
    }

    @Test
    fun `sets vehicle type from route_type 0 as TRAM`() = runTest {
        val route = RouteEntity("1", null, "1", "R", 0, null, null)
        val dataSource = FakeVehicleDataSource(listOf(vehicle(vehicleType = VehicleType.BUS)))
        val repo = DefaultVehicleRepository(
            dataSource,
            VehicleTestRouteDao(mapOf("1" to route)),
            VehicleTestTripDao(emptyMap()),
        )

        val positions = repo.observeVehiclePositions().first()
        assertEquals(VehicleType.TRAM, positions[0].vehicleType)
    }

    @Test
    fun `sets vehicle type from route_type 3 as BUS`() = runTest {
        val route = RouteEntity("50", null, "50", "R", 3, null, null)
        val dataSource = FakeVehicleDataSource(listOf(vehicle(routeId = "50")))
        val repo = DefaultVehicleRepository(
            dataSource,
            VehicleTestRouteDao(mapOf("50" to route)),
            VehicleTestTripDao(emptyMap()),
        )

        val positions = repo.observeVehiclePositions().first()
        assertEquals(VehicleType.BUS, positions[0].vehicleType)
    }

    @Test
    fun `leaves vehicle unenriched when route not in Room`() = runTest {
        val dataSource = FakeVehicleDataSource(listOf(vehicle(routeId = "999")))
        val repo = DefaultVehicleRepository(
            dataSource,
            VehicleTestRouteDao(emptyMap()),
            VehicleTestTripDao(emptyMap()),
        )

        val positions = repo.observeVehiclePositions().first()
        assertNull(positions[0].routeShortName)
        assertNull(positions[0].routeColor)
    }

    @Test
    fun `returns cached positions on data source failure`() = runTest {
        val dataSource = FakeVehicleDataSource(listOf(vehicle()))
        val repo = DefaultVehicleRepository(
            dataSource,
            VehicleTestRouteDao(emptyMap()),
            VehicleTestTripDao(emptyMap()),
        )

        val first = repo.observeVehiclePositions().first()
        assertEquals(1, first.size)

        dataSource.shouldThrow = true
        val second = repo.observeVehiclePositions().first()
        assertEquals(1, second.size)
    }
}

private class FakeVehicleDataSource(
    private var positions: List<VehiclePosition>,
) : com.github.karczews.publictransportviewer.data.source.VehicleDataSource {
    var shouldThrow = false

    override suspend fun getVehiclePositions(): List<VehiclePosition> {
        if (shouldThrow) throw RuntimeException("Network error")
        return positions
    }
}

private class VehicleTestRouteDao(private val routes: Map<String, RouteEntity>) : RouteDao {
    override suspend fun getRouteById(routeId: String): RouteEntity? = routes[routeId]
    override suspend fun getAllRoutes(): List<RouteEntity> = routes.values.toList()
    override suspend fun insertAll(routes: List<RouteEntity>) {}
    override suspend fun deleteAll() {}
}

private class VehicleTestTripDao(private val trips: Map<String, TripEntity>) : TripDao {
    override suspend fun getTripById(tripId: String): TripEntity? = trips[tripId]
    override suspend fun getTripsByRouteId(routeId: String): List<TripEntity> =
        trips.values.filter { it.routeId == routeId }
    override suspend fun insertAll(trips: List<TripEntity>) {}
    override suspend fun deleteAll() {}
}
