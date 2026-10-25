package com.github.karczews.publictransportviewer

import android.app.Application
import com.github.karczews.publictransportviewer.data.db.entity.StopEntity
import com.github.karczews.publictransportviewer.data.model.AlertCause
import com.github.karczews.publictransportviewer.data.model.AlertEffect
import com.github.karczews.publictransportviewer.data.model.LatLon
import com.github.karczews.publictransportviewer.data.model.RouteDisplayData
import com.github.karczews.publictransportviewer.data.model.RouteStop
import com.github.karczews.publictransportviewer.data.model.ServiceAlert
import com.github.karczews.publictransportviewer.data.model.StopDeparture
import com.github.karczews.publictransportviewer.data.model.VehiclePosition
import com.github.karczews.publictransportviewer.data.model.VehicleStatus
import com.github.karczews.publictransportviewer.data.model.VehicleType
import com.github.karczews.publictransportviewer.data.repository.AlertRepository
import com.github.karczews.publictransportviewer.data.repository.RouteDisplayRepository
import com.github.karczews.publictransportviewer.data.repository.StopRepository
import com.github.karczews.publictransportviewer.data.repository.VehicleRepository
import com.github.karczews.publictransportviewer.ui.alerts.AlertsViewModel
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel
import com.github.karczews.publictransportviewer.ui.stops.StopsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Test [Application] installed by [KoinTestRunner]. Starts Koin with [testModule] only —
 * fake repositories plus the real view models — so instrumented UI tests run against fixed
 * data with no database, network, or WorkManager involvement.
 */
class TestPublicTransportApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TestPublicTransportApp)
            modules(testModule)
        }
    }
}

val testModule = module {
    single<VehicleRepository> { FixedVehicleRepository() }
    single<RouteDisplayRepository> { FixedRouteDisplayRepository() }
    single<AlertRepository> { FixedAlertRepository() }
    single<StopRepository> { FixedStopRepository() }
    viewModel { HomeViewModel(get(), get()) }
    viewModel { StopsViewModel(get()) }
    viewModel { AlertsViewModel(get()) }
}

object FixtureData {

    val tram10 = VehiclePosition(
        vehicleId = "tram-v1",
        routeId = "10",
        tripId = "trip-10-1",
        label = "10",
        latitude = 51.7592,
        longitude = 19.4560,
        bearing = 90f,
        speed = 20f,
        timestamp = 1700000000L,
        vehicleType = VehicleType.TRAM,
        currentStatus = VehicleStatus.IN_TRANSIT_TO,
        routeShortName = "10",
        tripHeadsign = "Chocianowice IKEA",
        routeColor = "CE1124",
    )

    val bus50 = VehiclePosition(
        vehicleId = "bus-v2",
        routeId = "50",
        tripId = "trip-50-1",
        label = "50",
        latitude = 51.7610,
        longitude = 19.4580,
        bearing = 180f,
        speed = 15f,
        timestamp = 1700000000L,
        vehicleType = VehicleType.BUS,
        currentStatus = VehicleStatus.IN_TRANSIT_TO,
        routeShortName = "50",
        tripHeadsign = "Retkinia",
        routeColor = "0070BB",
    )

    val routeForTram10 = RouteDisplayData(
        routeId = "10",
        routeShortName = "10",
        routeColor = 0xFFCE1124.toInt(),
        polylinePoints = listOf(
            LatLon(51.7550, 19.4500),
            LatLon(51.7592, 19.4560),
            LatLon(51.7620, 19.4600),
        ),
        stops = listOf(
            RouteStop("s1", "Piotrkowska Centrum", 51.7550, 19.4500),
            RouteStop("s2", "Plac Wolności", 51.7592, 19.4560),
            RouteStop("s3", "Legionów", 51.7620, 19.4600),
        ),
    )

    val stops = listOf(
        StopEntity("s1", "Piotrkowska Centrum", 51.7550, 19.4500, "1001", 0, null),
        StopEntity("s2", "Plac Wolności", 51.7592, 19.4560, "1002", 0, null),
        StopEntity("s3", "Politechnika", 51.7500, 19.4450, "1003", 0, null),
        StopEntity("s4", "Piłsudskiego", 51.7530, 19.4480, "1004", 0, null),
    )

    val departuresForS1 = listOf(
        StopDeparture("t1", "10", "10", "Chocianowice IKEA", 45000, 120, "CE1124", VehicleType.TRAM),
        StopDeparture("t2", "50", "50", "Retkinia", 45300, null, "0070BB", VehicleType.BUS),
        StopDeparture("t3", "10", "10", "Helenówek", 45600, -60, "CE1124", VehicleType.TRAM),
    )

    val alerts = listOf(
        ServiceAlert(
            alertId = "alert-1",
            headerText = "Tram 10 detour on Piotrkowska",
            descriptionText = "Due to roadworks, tram 10 is rerouted via Kilińskiego.",
            cause = AlertCause.CONSTRUCTION,
            effect = AlertEffect.DETOUR,
            activePeriods = emptyList(),
            affectedRouteIds = listOf("10"),
            affectedRouteShortNames = listOf("10"),
            affectedStopIds = listOf("s1"),
        ),
    )
}

class FixedVehicleRepository : VehicleRepository {
    override fun observeVehiclePositions(): Flow<List<VehiclePosition>> =
        flowOf(listOf(FixtureData.tram10, FixtureData.bus50))
}

class FixedRouteDisplayRepository : RouteDisplayRepository {
    private val routes = mapOf("trip-10-1" to FixtureData.routeForTram10)
    override suspend fun loadRouteForTrip(tripId: String) = routes[tripId]
}

class FixedAlertRepository : AlertRepository {
    override fun observeAlerts(): Flow<List<ServiceAlert>> = flowOf(FixtureData.alerts)
}

class FixedStopRepository : StopRepository {
    override suspend fun searchStops(query: String): List<StopEntity> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        return FixtureData.stops.filter { q.lowercase() in it.stopName.lowercase() }
    }

    override fun observeDeparturesForStop(stopId: String): Flow<List<StopDeparture>> =
        flowOf(FixtureData.departuresForS1)
}
