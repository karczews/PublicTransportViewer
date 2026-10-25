package com.github.karczews.publictransportviewer.data.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class GtfsCsvParserTest {

    private fun reader(content: String): BufferedReader =
        BufferedReader(StringReader(content))

    @Test
    fun `parseRoutes maps all fields correctly`() {
        val csv = """
            route_id,agency_id,route_short_name,route_long_name,route_type,route_color,route_text_color
            1,MPK,1,Helenówek - Chocianowice IKEA,0,CE1124,FFFFFF
        """.trimIndent()

        val routes = GtfsCsvParser.parseRoutes(reader(csv))

        assertEquals(1, routes.size)
        val route = routes[0]
        assertEquals("1", route.routeId)
        assertEquals("MPK", route.agencyId)
        assertEquals("1", route.routeShortName)
        assertEquals("Helenówek - Chocianowice IKEA", route.routeLongName)
        assertEquals(0, route.routeType)
        assertEquals("CE1124", route.routeColor)
        assertEquals("FFFFFF", route.routeTextColor)
    }

    @Test
    fun `parseRoutes handles blank optional fields as null`() {
        val csv = """
            route_id,route_short_name,route_long_name,route_type,route_color
            1,1,Some Route,3,
        """.trimIndent()

        val routes = GtfsCsvParser.parseRoutes(reader(csv))

        assertEquals(1, routes.size)
        assertNull(routes[0].routeColor)
        assertNull(routes[0].agencyId)
    }

    @Test
    fun `parseRoutes handles different column order`() {
        val csv = """
            route_type,route_id,route_long_name,route_short_name
            0,42,Some Long Name,42
        """.trimIndent()

        val routes = GtfsCsvParser.parseRoutes(reader(csv))

        assertEquals("42", routes[0].routeId)
        assertEquals("42", routes[0].routeShortName)
        assertEquals(0, routes[0].routeType)
    }

    @Test
    fun `parseStops maps coordinates correctly`() {
        val csv = """
            stop_id,stop_name,stop_lat,stop_lon,stop_code,location_type,parent_station
            S1,Piotrkowska Centrum,51.7592,19.4560,1234,0,
        """.trimIndent()

        val stops = GtfsCsvParser.parseStops(reader(csv))

        assertEquals(1, stops.size)
        assertEquals("S1", stops[0].stopId)
        assertEquals("Piotrkowska Centrum", stops[0].stopName)
        assertEquals(51.7592, stops[0].stopLat, 0.0001)
        assertEquals(19.4560, stops[0].stopLon, 0.0001)
        assertEquals("1234", stops[0].stopCode)
        assertEquals(0, stops[0].locationType)
        assertNull(stops[0].parentStation)
    }

    @Test
    fun `parseTrips maps headsign and shape`() {
        val csv = """
            trip_id,route_id,service_id,trip_headsign,direction_id,shape_id
            T1,R1,WD,Chocianowice,0,SH1
        """.trimIndent()

        val trips = GtfsCsvParser.parseTrips(reader(csv))

        assertEquals(1, trips.size)
        assertEquals("T1", trips[0].tripId)
        assertEquals("R1", trips[0].routeId)
        assertEquals("Chocianowice", trips[0].tripHeadsign)
        assertEquals(0, trips[0].directionId)
        assertEquals("SH1", trips[0].shapeId)
    }

    @Test
    fun `parseStopTimesStreaming batches correctly`() {
        val csv = """
            trip_id,stop_sequence,stop_id,arrival_time,departure_time
            T1,1,S1,08:00:00,08:00:30
            T1,2,S2,08:05:00,08:05:30
            T1,3,S3,08:10:00,08:10:30
        """.trimIndent()

        val allBatches = mutableListOf<List<Any>>()
        GtfsCsvParser.parseStopTimesStreaming(reader(csv), batchSize = 2) { batch ->
            allBatches.add(batch.toList())
        }

        assertEquals(2, allBatches.size)
        assertEquals(2, allBatches[0].size)
        assertEquals(1, allBatches[1].size)
    }

    @Test
    fun `parseStopTimesStreaming parses time fields`() {
        val csv = """
            trip_id,stop_sequence,stop_id,arrival_time,departure_time
            T1,1,S1,25:30:00,25:30:30
        """.trimIndent()

        val results = mutableListOf<com.github.karczews.publictransportviewer.data.db.entity.StopTimeEntity>()
        GtfsCsvParser.parseStopTimesStreaming(reader(csv)) { batch ->
            results.addAll(batch)
        }

        assertEquals(1, results.size)
        assertEquals("25:30:00", results[0].arrivalTime)
        assertEquals("25:30:30", results[0].departureTime)
    }

    @Test
    fun `parseRoutes handles quoted fields with commas`() {
        val csv = """
            route_id,route_short_name,route_long_name,route_type
            1,1,"Helenówek - Chocianowice, IKEA",0
        """.trimIndent()

        val routes = GtfsCsvParser.parseRoutes(reader(csv))

        assertEquals("Helenówek - Chocianowice, IKEA", routes[0].routeLongName)
    }

    @Test
    fun `parseRoutes handles BOM in header`() {
        val bom = "﻿"
        val csv = "${bom}route_id,route_short_name,route_long_name,route_type\n1,1,Test,0"

        val routes = GtfsCsvParser.parseRoutes(reader(csv))

        assertEquals(1, routes.size)
        assertEquals("1", routes[0].routeId)
    }

    @Test
    fun `parseRoutes returns empty for empty input`() {
        val routes = GtfsCsvParser.parseRoutes(reader(""))
        assertEquals(0, routes.size)
    }

    @Test
    fun `parseRoutes skips rows with fewer columns than header`() {
        val csv = """
            route_id,route_short_name,route_long_name,route_type
            1,1,Test,0
            bad,row
            2,2,Test2,3
        """.trimIndent()

        val routes = GtfsCsvParser.parseRoutes(reader(csv))
        assertEquals(2, routes.size)
    }

    @Test
    fun `parseShapesStreaming parses coordinates`() {
        val csv = """
            shape_id,shape_pt_sequence,shape_pt_lat,shape_pt_lon
            SH1,1,51.7592,19.4560
            SH1,2,51.7600,19.4570
        """.trimIndent()

        val results = mutableListOf<com.github.karczews.publictransportviewer.data.db.entity.ShapePointEntity>()
        GtfsCsvParser.parseShapesStreaming(reader(csv)) { batch ->
            results.addAll(batch)
        }

        assertEquals(2, results.size)
        assertEquals("SH1", results[0].shapeId)
        assertEquals(1, results[0].shapePtSequence)
        assertEquals(51.7592, results[0].shapePtLat, 0.0001)
        assertEquals(19.4560, results[0].shapePtLon, 0.0001)
    }

    @Test
    fun `parseRoutes handles multiple rows`() {
        val csv = """
            route_id,route_short_name,route_long_name,route_type
            1,1,Route One,0
            50,50A,Route Fifty,3
            99,N1,Night Route,3
        """.trimIndent()

        val routes = GtfsCsvParser.parseRoutes(reader(csv))

        assertEquals(3, routes.size)
        assertEquals(0, routes[0].routeType)
        assertEquals(3, routes[1].routeType)
        assertEquals("N1", routes[2].routeShortName)
    }
}
