package com.github.karczews.publictransportviewer.ui.stops

import com.github.karczews.publictransportviewer.data.db.entity.StopEntity
import com.github.karczews.publictransportviewer.data.model.StopDeparture
import com.github.karczews.publictransportviewer.data.model.VehicleType
import com.github.karczews.publictransportviewer.data.repository.StopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class StopsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val stop1 = StopEntity("S1", "Piotrkowska Centrum", 51.76, 19.46, "1234", 0, null)
    private val stop2 = StopEntity("S2", "Politechnika", 51.75, 19.45, "5678", 0, null)

    private val departures = listOf(
        StopDeparture("T1", "R1", "10", "Chocianowice", 45000, 120, "CE1124", VehicleType.TRAM),
        StopDeparture("T2", "R2", "50A", "Retkinia", 45300, null, null, VehicleType.BUS),
    )

    @Test
    fun `search returns matching stops`() = runTest {
        val repo = FakeStopRepository(stops = listOf(stop1, stop2))
        val viewModel = StopsViewModel(repo)

        viewModel.onQueryChange("Piotrkowska")
        advanceTimeBy(350)
        advanceUntilIdle()

        val stops = viewModel.stops.value
        assertEquals(1, stops.size)
        assertEquals("Piotrkowska Centrum", stops[0].stopName)
    }

    @Test
    fun `search returns empty for no match`() = runTest {
        val repo = FakeStopRepository(stops = listOf(stop1))
        val viewModel = StopsViewModel(repo)

        viewModel.onQueryChange("Nonexistent")
        advanceTimeBy(350)
        advanceUntilIdle()

        assertTrue(viewModel.stops.value.isEmpty())
    }

    @Test
    fun `blank query returns empty and clears selection`() = runTest {
        val repo = FakeStopRepository(stops = listOf(stop1), departures = departures)
        val viewModel = StopsViewModel(repo)

        viewModel.onQueryChange("Piotrkowska")
        advanceTimeBy(350)
        advanceUntilIdle()

        viewModel.onStopSelected(stop1)
        advanceUntilIdle()
        assertNotNull(viewModel.selectedStop.value)

        viewModel.onQueryChange("  ")
        advanceUntilIdle()

        assertNull(viewModel.selectedStop.value)
        assertTrue(viewModel.departures.value.isEmpty())
    }

    @Test
    fun `selecting stop loads departures`() = runTest {
        val repo = FakeStopRepository(stops = listOf(stop1), departures = departures)
        val viewModel = StopsViewModel(repo)

        viewModel.onStopSelected(stop1)
        advanceUntilIdle()

        assertEquals(stop1, viewModel.selectedStop.value)
        assertEquals(2, viewModel.departures.value.size)
        assertEquals("10", viewModel.departures.value[0].routeShortName)
        assertEquals("50A", viewModel.departures.value[1].routeShortName)
    }

    @Test
    fun `clearSelection clears stop and departures`() = runTest {
        val repo = FakeStopRepository(stops = listOf(stop1), departures = departures)
        val viewModel = StopsViewModel(repo)

        viewModel.onStopSelected(stop1)
        advanceUntilIdle()
        assertNotNull(viewModel.selectedStop.value)

        viewModel.clearSelection()
        advanceUntilIdle()

        assertNull(viewModel.selectedStop.value)
        assertTrue(viewModel.departures.value.isEmpty())
    }

    @Test
    fun `selecting same stop twice does not reload`() = runTest {
        val repo = FakeStopRepository(stops = listOf(stop1), departures = departures)
        val viewModel = StopsViewModel(repo)

        viewModel.onStopSelected(stop1)
        advanceUntilIdle()
        val firstCallCount = repo.departureCallCount

        viewModel.onStopSelected(stop1)
        advanceUntilIdle()

        assertEquals(firstCallCount, repo.departureCallCount)
    }
}

private class FakeStopRepository(
    private val stops: List<StopEntity> = emptyList(),
    private val departures: List<StopDeparture> = emptyList(),
) : StopRepository {
    var departureCallCount = 0

    override suspend fun searchStops(query: String): List<StopEntity> =
        stops.filter { query.trim() in it.stopName }

    override fun observeDeparturesForStop(stopId: String): Flow<List<StopDeparture>> {
        departureCallCount++
        return flowOf(departures)
    }
}
