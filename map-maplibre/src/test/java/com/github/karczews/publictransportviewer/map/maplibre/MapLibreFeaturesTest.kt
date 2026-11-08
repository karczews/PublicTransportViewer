package com.github.karczews.publictransportviewer.map.maplibre

import com.github.karczews.publictransportviewer.data.model.LatLon
import com.github.karczews.publictransportviewer.data.model.RouteDisplayData
import com.github.karczews.publictransportviewer.data.model.RouteStop
import com.github.karczews.publictransportviewer.data.model.VehiclePosition
import com.github.karczews.publictransportviewer.data.model.VehicleStatus
import com.github.karczews.publictransportviewer.data.model.VehicleType
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapLibreFeaturesTest {

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

    private val routeForTram10 =
        RouteDisplayData(
            routeId = "10",
            routeShortName = "10",
            routeColor = 0xFFCE1124.toInt(),
            polylinePoints =
                listOf(
                    LatLon(51.7550, 19.4500),
                    LatLon(51.7592, 19.4560),
                    LatLon(51.7620, 19.4600),
                ),
            stops =
                listOf(
                    RouteStop("s1", "Piotrkowska Centrum", 51.7550, 19.4500),
                    RouteStop("s2", "Plac Wolności", 51.7592, 19.4560),
                    RouteStop("s3", "Legionów", 51.7620, 19.4600),
                ),
        )

    @Test
    fun vehicleFeatures_mapsPropertiesAndLonLatOrder() {
        val features = vehicleFeatures(listOf(tram10, bus50)).features

        assertEquals(2, features.size)
        val first = features.first()
        assertEquals("tram-v1", first.properties["id"]?.jsonPrimitive?.content)
        assertEquals("10", first.properties["label"]?.jsonPrimitive?.content)
        assertEquals("TRAM", first.properties["type"]?.jsonPrimitive?.content)
        assertEquals(19.4560, first.geometry.coordinates.longitude, 0.0)
        assertEquals(51.7592, first.geometry.coordinates.latitude, 0.0)
    }

    @Test
    fun routeLine_keepsPointOrder() {
        val coordinates = routeLine(routeForTram10)!!.features.single().geometry.coordinates

        assertEquals(3, coordinates.size)
        assertEquals(19.4500, coordinates.first().longitude, 0.0)
        assertEquals(51.7550, coordinates.first().latitude, 0.0)
        assertEquals(19.4560, coordinates[1].longitude, 0.0)
        assertEquals(51.7592, coordinates[1].latitude, 0.0)
        assertEquals(19.4600, coordinates.last().longitude, 0.0)
        assertEquals(51.7620, coordinates.last().latitude, 0.0)
    }

    @Test
    fun routeLine_returnsNullForFewerThanTwoPoints() {
        assertNull(routeLine(routeForTram10.copy(polylinePoints = listOf(LatLon(51.0, 19.0)))))
        assertNull(routeLine(routeForTram10.copy(polylinePoints = emptyList())))
    }

    @Test
    fun routeStopFeatures_mapsIdsNamesAndPositions() {
        val features = routeStopFeatures(routeForTram10).features

        assertEquals(
            listOf("s1", "s2", "s3"),
            features.map { it.properties["id"]?.jsonPrimitive?.content },
        )
        assertEquals(
            listOf("Piotrkowska Centrum", "Plac Wolności", "Legionów"),
            features.map { it.properties["name"]?.jsonPrimitive?.content },
        )
        assertEquals(19.4500, features[0].geometry.coordinates.longitude, 0.0)
        assertEquals(51.7550, features[0].geometry.coordinates.latitude, 0.0)
        assertEquals(19.4560, features[1].geometry.coordinates.longitude, 0.0)
        assertEquals(51.7592, features[1].geometry.coordinates.latitude, 0.0)
        assertEquals(19.4600, features[2].geometry.coordinates.longitude, 0.0)
        assertEquals(51.7620, features[2].geometry.coordinates.latitude, 0.0)
    }

    @Test
    fun selectedVehicle_returnsNullWhenVehicleMissing() {
        assertNull(selectedVehicle(listOf(bus50), "tram-v1"))
        assertNull(selectedVehicle(listOf(tram10), null))
        assertEquals(bus50, selectedVehicle(listOf(tram10, bus50), "bus-v2"))
    }

    @Test
    fun clickedVehicleId_readsIdProperty() {
        assertEquals("tram-v1", clickedVehicleId(vehicleFeatures(listOf(tram10)).features.first()))
    }

    @Test
    fun renderKeys_ignoreVolatileOnlyChanges() {
        val variants =
            listOf(
                tram10.copy(timestamp = 1700000001L),
                tram10.copy(bearing = 180f),
                tram10.copy(speed = 5f),
                tram10.copy(currentStatus = VehicleStatus.STOPPED_AT),
                tram10.copy(stopId = "stop-1"),
                tram10.copy(tripId = "trip-10-2"),
                // Raw label never reaches the map: vehicleLabel() uses routeShortName ?: routeId.
                tram10.copy(label = "999"),
                // routeId feeds vehicleLabel only as a fallback; routeShortName is set here.
                tram10.copy(routeId = "10X"),
                tram10.copy(routeColor = "FFFFFF"),
                tram10.copy(tripHeadsign = "Other Headsign"),
            )

        variants.forEach { variant ->
            assertEquals(
                vehicleRenderKeys(listOf(tram10)),
                vehicleRenderKeys(listOf(variant)),
            )
            // Structural equality is why memoizing on the keys is safe.
            assertEquals(vehicleFeatures(listOf(tram10)), vehicleFeatures(listOf(variant)))
        }
    }

    @Test
    fun renderKeys_detectRenderedChanges() {
        assertNotEquals(
            vehicleRenderKeys(listOf(tram10)),
            vehicleRenderKeys(listOf(tram10.copy(latitude = 51.7700))),
        )
        assertNotEquals(
            vehicleRenderKeys(listOf(tram10)),
            vehicleRenderKeys(listOf(tram10.copy(longitude = 19.4700))),
        )
        assertNotEquals(
            vehicleRenderKeys(listOf(tram10)),
            vehicleRenderKeys(listOf(tram10.copy(vehicleId = "tram-v9"))),
        )
        assertNotEquals(
            vehicleRenderKeys(listOf(tram10)),
            vehicleRenderKeys(listOf(tram10.copy(vehicleType = VehicleType.BUS))),
        )
        // Label change via routeShortName.
        assertNotEquals(
            vehicleRenderKeys(listOf(tram10)),
            vehicleRenderKeys(listOf(tram10.copy(routeShortName = "10A"))),
        )
    }

    @Test
    fun renderKeys_followLabelFallback() {
        val withoutShortName = tram10.copy(routeShortName = null)
        // routeId change flips the fallback label.
        assertNotEquals(
            vehicleRenderKeys(listOf(withoutShortName)),
            vehicleRenderKeys(listOf(withoutShortName.copy(routeId = "99"))),
        )
        // Blank short name falls back to routeId exactly like null does.
        val blankShortName = tram10.copy(routeShortName = "  ")
        assertEquals(
            vehicleRenderKeys(listOf(withoutShortName)),
            vehicleRenderKeys(listOf(blankShortName)),
        )
        assertNotEquals(
            vehicleRenderKeys(listOf(blankShortName)),
            vehicleRenderKeys(listOf(blankShortName.copy(routeId = "99"))),
        )
    }

    @Test
    fun featureCollection_buildsExclusivelyFromKeys() {
        val vehicles = listOf(tram10, bus50)
        assertEquals(vehicleFeatures(vehicles), vehicleFeatureCollection(vehicleRenderKeys(vehicles)))
    }

    @Test
    fun renderKeys_followFeedOrder() {
        // Keys track feed order like vehicleFeatures does: a reorder-only poll rebuilds once
        // rather than risk reusing a stale-order collection.
        assertNotEquals(
            vehicleRenderKeys(listOf(tram10, bus50)),
            vehicleRenderKeys(listOf(bus50, tram10)),
        )
    }
}
