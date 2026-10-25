package com.github.karczews.publictransportviewer.ui.home

import com.github.karczews.publictransportviewer.data.model.LatLon
import com.github.karczews.publictransportviewer.data.model.RouteDisplayData
import com.github.karczews.publictransportviewer.data.model.RouteStop
import com.github.karczews.publictransportviewer.data.model.VehiclePosition
import com.github.karczews.publictransportviewer.data.model.VehicleStatus
import com.github.karczews.publictransportviewer.data.model.VehicleType
import com.github.karczews.publictransportviewer.data.repository.RouteDisplayRepository
import com.github.karczews.publictransportviewer.data.repository.VehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var vehicleFlow: MutableSharedFlow<List<VehiclePosition>>
    private lateinit var fakeVehicleRepo: FakeVehicleRepository
    private lateinit var fakeRouteDisplayRepo: FakeRouteDisplayRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        vehicleFlow = MutableSharedFlow()
        fakeVehicleRepo = FakeVehicleRepository(vehicleFlow)
        fakeRouteDisplayRepo = FakeRouteDisplayRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun tram(id: String = "V1", routeId: String = "10", tripId: String? = "T1") =
        VehiclePosition(
            vehicleId = id,
            routeId = routeId,
            tripId = tripId,
            label = routeId,
            latitude = 51.76,
            longitude = 19.46,
            bearing = 0f,
            speed = 0f,
            timestamp = 1000L,
            vehicleType = VehicleType.TRAM,
            currentStatus = VehicleStatus.IN_TRANSIT_TO,
            routeShortName = routeId,
        )

    private fun bus(id: String = "V2", routeId: String = "50", tripId: String = "T2") =
        VehiclePosition(
            vehicleId = id,
            routeId = routeId,
            tripId = tripId,
            label = routeId,
            latitude = 51.77,
            longitude = 19.47,
            bearing = 0f,
            speed = 0f,
            timestamp = 1000L,
            vehicleType = VehicleType.BUS,
            currentStatus = VehicleStatus.IN_TRANSIT_TO,
            routeShortName = routeId,
        )

    @Test
    fun `displays trams and buses from repository`() = runTest {
        val viewModel = HomeViewModel(fakeVehicleRepo, fakeRouteDisplayRepo)

        vehicleFlow.emit(listOf(tram(), bus()))
        advanceUntilIdle()

        val vehicles = viewModel.vehiclePositions.value
        assertEquals(2, vehicles.size)

        val tram = vehicles.find { it.vehicleType == VehicleType.TRAM }
        val bus = vehicles.find { it.vehicleType == VehicleType.BUS }
        assertNotNull(tram)
        assertNotNull(bus)
        assertEquals("10", tram!!.routeShortName)
        assertEquals("50", bus!!.routeShortName)
    }

    @Test
    fun `selecting vehicle sets selectedVehicleId`() = runTest {
        val viewModel = HomeViewModel(fakeVehicleRepo, fakeRouteDisplayRepo)
        val vehicle = tram()

        vehicleFlow.emit(listOf(vehicle))
        advanceUntilIdle()

        viewModel.onVehicleSelected(vehicle)
        advanceUntilIdle()

        assertEquals("V1", viewModel.selectedVehicleId.value)
    }

    @Test
    fun `selecting tram loads route display with polyline and stops`() = runTest {
        val routeData = RouteDisplayData(
            routeId = "10",
            routeShortName = "10",
            routeColor = 0xFF0000,
            polylinePoints = listOf(LatLon(51.76, 19.46), LatLon(51.77, 19.47)),
            stops = listOf(
                RouteStop("S1", "Piotrkowska", 51.76, 19.46),
                RouteStop("S2", "Centrum", 51.77, 19.47),
            ),
        )
        fakeRouteDisplayRepo.routeData = mapOf("T1" to routeData)

        val viewModel = HomeViewModel(fakeVehicleRepo, fakeRouteDisplayRepo)
        val vehicle = tram()

        vehicleFlow.emit(listOf(vehicle))
        advanceUntilIdle()

        viewModel.onVehicleSelected(vehicle)
        advanceUntilIdle()

        val route = viewModel.routeDisplay.value
        assertNotNull(route)
        assertEquals("10", route!!.routeShortName)
        assertEquals(2, route.polylinePoints.size)
        assertEquals(2, route.stops.size)
        assertEquals("Piotrkowska", route.stops[0].stopName)
    }

    @Test
    fun `clearSelection clears vehicle id and route display`() = runTest {
        fakeRouteDisplayRepo.routeData = mapOf(
            "T1" to RouteDisplayData("10", "10", 0, emptyList(), emptyList())
        )

        val viewModel = HomeViewModel(fakeVehicleRepo, fakeRouteDisplayRepo)
        val vehicle = tram()

        vehicleFlow.emit(listOf(vehicle))
        advanceUntilIdle()

        viewModel.onVehicleSelected(vehicle)
        advanceUntilIdle()
        assertNotNull(viewModel.selectedVehicleId.value)

        viewModel.clearSelection()
        advanceUntilIdle()

        assertNull(viewModel.selectedVehicleId.value)
        assertNull(viewModel.routeDisplay.value)
    }

    @Test
    fun `selecting same vehicle twice deselects it`() = runTest {
        val viewModel = HomeViewModel(fakeVehicleRepo, fakeRouteDisplayRepo)
        val vehicle = tram()

        vehicleFlow.emit(listOf(vehicle))
        advanceUntilIdle()

        viewModel.onVehicleSelected(vehicle)
        advanceUntilIdle()
        assertEquals("V1", viewModel.selectedVehicleId.value)

        viewModel.onVehicleSelected(vehicle)
        advanceUntilIdle()
        assertNull(viewModel.selectedVehicleId.value)
    }

    @Test
    fun `initially no vehicles are displayed`() = runTest {
        val viewModel = HomeViewModel(fakeVehicleRepo, fakeRouteDisplayRepo)
        advanceUntilIdle()

        assertTrue(viewModel.vehiclePositions.value.isEmpty())
        assertNull(viewModel.selectedVehicleId.value)
        assertNull(viewModel.routeDisplay.value)
    }

    @Test
    fun `route display is null for vehicle without tripId`() = runTest {
        val viewModel = HomeViewModel(fakeVehicleRepo, fakeRouteDisplayRepo)
        val vehicle = tram(tripId = null)

        vehicleFlow.emit(listOf(vehicle))
        advanceUntilIdle()

        viewModel.onVehicleSelected(vehicle)
        advanceUntilIdle()

        assertEquals("V1", viewModel.selectedVehicleId.value)
        assertNull(viewModel.routeDisplay.value)
    }

    @Test
    fun `vehicle positions update when flow emits new data`() = runTest {
        val viewModel = HomeViewModel(fakeVehicleRepo, fakeRouteDisplayRepo)

        vehicleFlow.emit(listOf(tram()))
        advanceUntilIdle()
        assertEquals(1, viewModel.vehiclePositions.value.size)

        vehicleFlow.emit(listOf(tram(), bus()))
        advanceUntilIdle()
        assertEquals(2, viewModel.vehiclePositions.value.size)
    }
}

private class FakeVehicleRepository(
    private val flow: Flow<List<VehiclePosition>>,
) : VehicleRepository {
    override fun observeVehiclePositions(): Flow<List<VehiclePosition>> = flow
}

private class FakeRouteDisplayRepository : RouteDisplayRepository {
    var routeData: Map<String, RouteDisplayData> = emptyMap()

    override suspend fun loadRouteForTrip(tripId: String): RouteDisplayData? = routeData[tripId]
}
