package com.github.karczews.publictransportviewer.data.source

import com.github.karczews.publictransportviewer.data.db.entity.RouteEntity
import com.github.karczews.publictransportviewer.data.db.entity.ShapePointEntity
import com.github.karczews.publictransportviewer.data.db.entity.StopEntity
import com.github.karczews.publictransportviewer.data.db.entity.StopTimeEntity
import com.github.karczews.publictransportviewer.data.db.entity.TripEntity
import java.io.BufferedReader

object GtfsCsvParser {

    fun parseRoutes(reader: BufferedReader): List<RouteEntity> {
        val (header, lines) = readCsv(reader)
        return lines.map { fields ->
            RouteEntity(
                routeId = fields.getValue(header, "route_id"),
                agencyId = fields.getOrNull(header, "agency_id"),
                routeShortName = fields.getValue(header, "route_short_name"),
                routeLongName = fields.getValue(header, "route_long_name"),
                routeType = fields.getValue(header, "route_type").toInt(),
                routeColor = fields.getOrNull(header, "route_color")?.ifBlank { null },
                routeTextColor = fields.getOrNull(header, "route_text_color")?.ifBlank { null },
            )
        }
    }

    fun parseStops(reader: BufferedReader): List<StopEntity> {
        val (header, lines) = readCsv(reader)
        return lines.map { fields ->
            StopEntity(
                stopId = fields.getValue(header, "stop_id"),
                stopName = fields.getValue(header, "stop_name"),
                stopLat = fields.getValue(header, "stop_lat").toDouble(),
                stopLon = fields.getValue(header, "stop_lon").toDouble(),
                stopCode = fields.getOrNull(header, "stop_code")?.ifBlank { null },
                locationType = fields.getOrNull(header, "location_type")?.toIntOrNull() ?: 0,
                parentStation = fields.getOrNull(header, "parent_station")?.ifBlank { null },
            )
        }
    }

    fun parseTrips(reader: BufferedReader): List<TripEntity> {
        val (header, lines) = readCsv(reader)
        return lines.map { fields ->
            TripEntity(
                tripId = fields.getValue(header, "trip_id"),
                routeId = fields.getValue(header, "route_id"),
                serviceId = fields.getValue(header, "service_id"),
                tripHeadsign = fields.getOrNull(header, "trip_headsign")?.ifBlank { null },
                directionId = fields.getOrNull(header, "direction_id")?.toIntOrNull(),
                shapeId = fields.getOrNull(header, "shape_id")?.ifBlank { null },
            )
        }
    }

    fun parseStopTimesStreaming(
        reader: BufferedReader,
        batchSize: Int = 1000,
        onBatch: suspend (List<StopTimeEntity>) -> Unit,
    ) {
        val headerLine = reader.readLine()?.stripBom() ?: return
        val header = parseCsvLine(headerLine)
        val batch = mutableListOf<StopTimeEntity>()

        reader.forEachLine { line ->
            val fields = parseCsvLine(line)
            if (fields.size >= header.size) {
                batch.add(
                    StopTimeEntity(
                        tripId = fields.getValue(header, "trip_id"),
                        stopSequence = fields.getValue(header, "stop_sequence").toInt(),
                        stopId = fields.getValue(header, "stop_id"),
                        arrivalTime = fields.getValue(header, "arrival_time"),
                        departureTime = fields.getValue(header, "departure_time"),
                        pickupType = fields.getOrNull(header, "pickup_type")?.toIntOrNull(),
                        dropOffType = fields.getOrNull(header, "drop_off_type")?.toIntOrNull(),
                    )
                )
                if (batch.size >= batchSize) {
                    kotlinx.coroutines.runBlocking { onBatch(batch.toList()) }
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) {
            kotlinx.coroutines.runBlocking { onBatch(batch.toList()) }
        }
    }

    fun parseShapesStreaming(
        reader: BufferedReader,
        batchSize: Int = 1000,
        onBatch: suspend (List<ShapePointEntity>) -> Unit,
    ) {
        val headerLine = reader.readLine()?.stripBom() ?: return
        val header = parseCsvLine(headerLine)
        val batch = mutableListOf<ShapePointEntity>()

        reader.forEachLine { line ->
            val fields = parseCsvLine(line)
            if (fields.size >= header.size) {
                batch.add(
                    ShapePointEntity(
                        shapeId = fields.getValue(header, "shape_id"),
                        shapePtSequence = fields.getValue(header, "shape_pt_sequence").toInt(),
                        shapePtLat = fields.getValue(header, "shape_pt_lat").toDouble(),
                        shapePtLon = fields.getValue(header, "shape_pt_lon").toDouble(),
                    )
                )
                if (batch.size >= batchSize) {
                    kotlinx.coroutines.runBlocking { onBatch(batch.toList()) }
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) {
            kotlinx.coroutines.runBlocking { onBatch(batch.toList()) }
        }
    }

    private fun readCsv(reader: BufferedReader): Pair<List<String>, List<List<String>>> {
        val headerLine = reader.readLine()?.stripBom() ?: return emptyList<String>() to emptyList()
        val header = parseCsvLine(headerLine)
        val rows = reader.lineSequence()
            .filter { it.isNotBlank() }
            .map { parseCsvLine(it) }
            .filter { it.size >= header.size }
            .toList()
        return header to rows
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    fields.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        fields.add(current.toString().trim())
        return fields
    }

    private fun String.stripBom(): String =
        if (isNotEmpty() && this[0].code == 0xFEFF) substring(1) else this

    private fun List<String>.getValue(header: List<String>, column: String): String {
        val index = header.indexOf(column)
        require(index >= 0) { "Missing required column: $column" }
        return this[index]
    }

    private fun List<String>.getOrNull(header: List<String>, column: String): String? {
        val index = header.indexOf(column)
        return if (index >= 0 && index < size) this[index] else null
    }
}
