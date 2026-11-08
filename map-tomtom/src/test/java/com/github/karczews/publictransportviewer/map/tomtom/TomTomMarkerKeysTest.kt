package com.github.karczews.publictransportviewer.map.tomtom

import com.github.karczews.publictransportviewer.data.model.VehiclePosition
import com.github.karczews.publictransportviewer.data.model.VehicleStatus
import com.github.karczews.publictransportviewer.data.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TomTomMarkerKeysTest {

    private val tram10 =
        VehiclePosition(
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

    private val bus50 =
        tram10.copy(
            vehicleId = "bus-v2",
            routeId = "50",
            tripId = "trip-50-1",
            label = "50",
            latitude = 51.7610,
            longitude = 19.4580,
            vehicleType = VehicleType.BUS,
            routeShortName = "50",
            tripHeadsign = "Retkinia",
            routeColor = "0070BB",
        )

    @Test
    fun markerKeys_ignoreVolatileOnlyChanges() {
        val variants =
            listOf(
                tram10.copy(timestamp = 1700000001L),
                tram10.copy(bearing = 180f),
                tram10.copy(speed = 5f),
                tram10.copy(currentStatus = VehicleStatus.STOPPED_AT),
                tram10.copy(stopId = "stop-1"),
                tram10.copy(tripId = "trip-10-2"),
                // Raw label never reaches the marker: label()/title() use routeShortName ?: routeId.
                tram10.copy(label = "999"),
                // routeId feeds vehicleLabel only as a fallback; routeShortName is set here.
                tram10.copy(routeId = "10X"),
                tram10.copy(routeColor = "FFFFFF"),
            )

        variants.forEach { variant ->
            assertEquals(
                vehicleMarkerKeys(listOf(tram10)),
                vehicleMarkerKeys(listOf(variant)),
            )
        }
    }

    @Test
    fun markerKeys_detectRenderedChanges() {
        assertNotEquals(
            vehicleMarkerKeys(listOf(tram10)),
            vehicleMarkerKeys(listOf(tram10.copy(latitude = 51.7700))),
        )
        assertNotEquals(
            vehicleMarkerKeys(listOf(tram10)),
            vehicleMarkerKeys(listOf(tram10.copy(longitude = 19.4700))),
        )
        assertNotEquals(
            vehicleMarkerKeys(listOf(tram10)),
            vehicleMarkerKeys(listOf(tram10.copy(vehicleId = "tram-v9"))),
        )
        assertNotEquals(
            vehicleMarkerKeys(listOf(tram10)),
            vehicleMarkerKeys(listOf(tram10.copy(vehicleType = VehicleType.BUS))),
        )
        // Label change via routeShortName.
        assertNotEquals(
            vehicleMarkerKeys(listOf(tram10)),
            vehicleMarkerKeys(listOf(tram10.copy(routeShortName = "10A"))),
        )
        // Headsign change via balloon title.
        assertNotEquals(
            vehicleMarkerKeys(listOf(tram10)),
            vehicleMarkerKeys(listOf(tram10.copy(tripHeadsign = "Other Headsign"))),
        )
    }

    @Test
    fun markerKeys_followLabelFallback() {
        val withoutShortName = tram10.copy(routeShortName = null)
        // routeId change flips the fallback label.
        assertNotEquals(
            vehicleMarkerKeys(listOf(withoutShortName)),
            vehicleMarkerKeys(listOf(withoutShortName.copy(routeId = "99"))),
        )
        // Blank short name falls back to routeId exactly like null does.
        val blankShortName = tram10.copy(routeShortName = "  ")
        assertEquals(
            vehicleMarkerKeys(listOf(withoutShortName)),
            vehicleMarkerKeys(listOf(blankShortName)),
        )
        assertNotEquals(
            vehicleMarkerKeys(listOf(blankShortName)),
            vehicleMarkerKeys(listOf(blankShortName.copy(routeId = "99"))),
        )
    }

    @Test
    fun markerKeys_followFeedOrder() {
        // Keys track feed order: a reorder-only poll rebuilds once
        // rather than risk reusing a stale-order collection.
        assertNotEquals(
            vehicleMarkerKeys(listOf(tram10, bus50)),
            vehicleMarkerKeys(listOf(bus50, tram10)),
        )
    }
}
