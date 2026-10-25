package com.github.karczews.publictransportviewer.ui.home

import com.github.karczews.publictransportviewer.core.R
import com.github.karczews.publictransportviewer.data.model.VehiclePosition
import com.github.karczews.publictransportviewer.data.model.VehicleStatus
import com.github.karczews.publictransportviewer.data.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleFormattingTest {

    private val tram10 = VehiclePosition(
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
    )

    private val bus50 = tram10.copy(
        vehicleId = "bus-v2",
        routeId = "50",
        label = "50",
        vehicleType = VehicleType.BUS,
        routeShortName = "50",
        tripHeadsign = "Retkinia",
    )

    @Test
    fun title_tramWithHeadsign() {
        assertEquals("Tram 10 → Chocianowice IKEA", vehicleTitle(tram10))
    }

    @Test
    fun title_busWithoutHeadsign() {
        assertEquals("Bus 50", vehicleTitle(bus50.copy(tripHeadsign = null)))
    }

    @Test
    fun label_fallsBackToRouteId() {
        assertEquals("50A", vehicleLabel(bus50.copy(routeShortName = null, routeId = "50A")))
    }

    @Test
    fun title_fallsBackToRouteId() {
        assertEquals(
            "Tram 10X",
            vehicleTitle(tram10.copy(routeShortName = null, routeId = "10X", tripHeadsign = null)),
        )
    }

    @Test
    fun iconRes_matchesType() {
        assertEquals(R.drawable.ic_tram, vehicleIconRes(VehicleType.TRAM))
        assertEquals(R.drawable.ic_bus, vehicleIconRes(VehicleType.BUS))
    }
}
