package com.github.karczews.publictransportviewer.data.source

import com.github.karczews.publictransportviewer.data.model.VehicleStatus
import com.github.karczews.publictransportviewer.data.model.VehicleType
import com.github.karczews.publictransportviewer.data.network.GtfsRtApi
import com.google.transit.realtime.GtfsRealtime.FeedEntity
import com.google.transit.realtime.GtfsRealtime.FeedHeader
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.google.transit.realtime.GtfsRealtime.Position
import com.google.transit.realtime.GtfsRealtime.TripDescriptor
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor
import com.google.transit.realtime.GtfsRealtime.VehiclePosition
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GtfsRtVehicleDataSourceTest {

    private fun buildFeed(vararg entities: FeedEntity): FeedMessage =
        FeedMessage.newBuilder()
            .setHeader(
                FeedHeader.newBuilder()
                    .setGtfsRealtimeVersion("2.0")
                    .setTimestamp(1000L)
            )
            .addAllEntity(entities.toList())
            .build()

    private fun vehicleEntity(
        id: String,
        routeId: String = "10",
        tripId: String = "T1",
        vehicleId: String = "V1",
        vehicleLabel: String = "Label",
        lat: Float = 51.76f,
        lon: Float = 19.46f,
        bearing: Float? = null,
        speed: Float? = null,
        status: VehiclePosition.VehicleStopStatus = VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO,
        stopId: String? = null,
        timestamp: Long = 1234567890L,
    ): FeedEntity {
        val posBuilder = Position.newBuilder()
            .setLatitude(lat)
            .setLongitude(lon)
        bearing?.let { posBuilder.setBearing(it) }
        speed?.let { posBuilder.setSpeed(it) }

        val vpBuilder = VehiclePosition.newBuilder()
            .setPosition(posBuilder)
            .setTrip(
                TripDescriptor.newBuilder()
                    .setTripId(tripId)
                    .setRouteId(routeId)
            )
            .setVehicle(
                VehicleDescriptor.newBuilder()
                    .setId(vehicleId)
                    .setLabel(vehicleLabel)
            )
            .setCurrentStatus(status)
            .setTimestamp(timestamp)

        stopId?.let { vpBuilder.setStopId(it) }

        return FeedEntity.newBuilder()
            .setId(id)
            .setVehicle(vpBuilder)
            .build()
    }

    @Test
    fun `maps protobuf vehicle to domain model`() = runTest {
        val feed = buildFeed(
            vehicleEntity(
                id = "e1",
                routeId = "10",
                tripId = "T1",
                vehicleId = "V100",
                vehicleLabel = "Tram 10",
                lat = 51.76f,
                lon = 19.46f,
                bearing = 180f,
                speed = 25.5f,
                timestamp = 1700000000L,
            )
        )
        val api = FakeGtfsRtApi(vehiclePositionsFeed = feed)
        val dataSource = GtfsRtVehicleDataSource(api)

        val positions = dataSource.getVehiclePositions()

        assertEquals(1, positions.size)
        val v = positions[0]
        assertEquals("V100", v.vehicleId)
        assertEquals("10", v.routeId)
        assertEquals("T1", v.tripId)
        assertEquals("Tram 10", v.label)
        assertEquals(51.76, v.latitude, 0.01)
        assertEquals(19.46, v.longitude, 0.01)
        assertEquals(180f, v.bearing)
        assertEquals(25.5f, v.speed)
        assertEquals(1700000000L, v.timestamp)
        assertEquals(VehicleStatus.IN_TRANSIT_TO, v.currentStatus)
    }

    @Test
    fun `infers TRAM for route ids 1-46`() = runTest {
        val feed = buildFeed(
            vehicleEntity(id = "e1", routeId = "1"),
            vehicleEntity(id = "e2", routeId = "46", vehicleId = "V2"),
        )
        val api = FakeGtfsRtApi(vehiclePositionsFeed = feed)
        val dataSource = GtfsRtVehicleDataSource(api)

        val positions = dataSource.getVehiclePositions()

        assertEquals(VehicleType.TRAM, positions[0].vehicleType)
        assertEquals(VehicleType.TRAM, positions[1].vehicleType)
    }

    @Test
    fun `infers BUS for route ids above 46 and non-numeric`() = runTest {
        val feed = buildFeed(
            vehicleEntity(id = "e1", routeId = "50"),
            vehicleEntity(id = "e2", routeId = "N1", vehicleId = "V2"),
            vehicleEntity(id = "e3", routeId = "65A", vehicleId = "V3"),
        )
        val api = FakeGtfsRtApi(vehiclePositionsFeed = feed)
        val dataSource = GtfsRtVehicleDataSource(api)

        val positions = dataSource.getVehiclePositions()

        assertEquals(VehicleType.BUS, positions[0].vehicleType)
        assertEquals(VehicleType.BUS, positions[1].vehicleType)
        assertEquals(VehicleType.BUS, positions[2].vehicleType)
    }

    @Test
    fun `maps all vehicle stop statuses`() = runTest {
        val feed = buildFeed(
            vehicleEntity(id = "e1", status = VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO, vehicleId = "V1"),
            vehicleEntity(id = "e2", status = VehiclePosition.VehicleStopStatus.STOPPED_AT, vehicleId = "V2"),
            vehicleEntity(id = "e3", status = VehiclePosition.VehicleStopStatus.INCOMING_AT, vehicleId = "V3"),
        )
        val api = FakeGtfsRtApi(vehiclePositionsFeed = feed)
        val dataSource = GtfsRtVehicleDataSource(api)

        val positions = dataSource.getVehiclePositions()

        assertEquals(VehicleStatus.IN_TRANSIT_TO, positions[0].currentStatus)
        assertEquals(VehicleStatus.STOPPED_AT, positions[1].currentStatus)
        assertEquals(VehicleStatus.INCOMING_AT, positions[2].currentStatus)
    }

    @Test
    fun `includes stop id when present`() = runTest {
        val feed = buildFeed(
            vehicleEntity(id = "e1", stopId = "STOP_42"),
        )
        val api = FakeGtfsRtApi(vehiclePositionsFeed = feed)
        val dataSource = GtfsRtVehicleDataSource(api)

        val positions = dataSource.getVehiclePositions()
        assertEquals("STOP_42", positions[0].stopId)
    }

    @Test
    fun `stop id is null when not set`() = runTest {
        val feed = buildFeed(
            vehicleEntity(id = "e1", stopId = null),
        )
        val api = FakeGtfsRtApi(vehiclePositionsFeed = feed)
        val dataSource = GtfsRtVehicleDataSource(api)

        val positions = dataSource.getVehiclePositions()
        assertNull(positions[0].stopId)
    }

    @Test
    fun `defaults bearing and speed to zero when absent`() = runTest {
        val feed = buildFeed(
            vehicleEntity(id = "e1", bearing = null, speed = null),
        )
        val api = FakeGtfsRtApi(vehiclePositionsFeed = feed)
        val dataSource = GtfsRtVehicleDataSource(api)

        val positions = dataSource.getVehiclePositions()
        assertEquals(0f, positions[0].bearing)
        assertEquals(0f, positions[0].speed)
    }

    @Test
    fun `skips entities without vehicle field`() = runTest {
        val entityWithoutVehicle = FeedEntity.newBuilder()
            .setId("no-vehicle")
            .build()
        val feed = buildFeed(
            entityWithoutVehicle,
            vehicleEntity(id = "e1"),
        )
        val api = FakeGtfsRtApi(vehiclePositionsFeed = feed)
        val dataSource = GtfsRtVehicleDataSource(api)

        val positions = dataSource.getVehiclePositions()
        assertEquals(1, positions.size)
    }

    @Test
    fun `empty feed returns empty list`() = runTest {
        val feed = buildFeed()
        val api = FakeGtfsRtApi(vehiclePositionsFeed = feed)
        val dataSource = GtfsRtVehicleDataSource(api)

        val positions = dataSource.getVehiclePositions()
        assertEquals(0, positions.size)
    }
}

private class FakeGtfsRtApi(
    private val vehiclePositionsFeed: FeedMessage = FeedMessage.getDefaultInstance(),
) : GtfsRtApi {

    override suspend fun fetchVehiclePositions(): Result<FeedMessage> =
        Result.success(vehiclePositionsFeed)

    override suspend fun fetchTripUpdates(): Result<FeedMessage> =
        Result.success(FeedMessage.getDefaultInstance())

    override suspend fun fetchAlerts(): Result<FeedMessage> =
        Result.success(FeedMessage.getDefaultInstance())
}
