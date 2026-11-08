package com.github.karczews.publictransportviewer.map.maplibre

import com.github.karczews.publictransportviewer.data.model.RouteDisplayData
import com.github.karczews.publictransportviewer.data.model.VehiclePosition
import com.github.karczews.publictransportviewer.ui.home.vehicleLabel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

/** One Point per vehicle; properties `id`, `label` and `type` drive the vehicle SymbolLayer. */
internal fun vehicleFeatures(
    vehicles: List<VehiclePosition>,
): FeatureCollection<Point, JsonObject> = vehicleFeatureCollection(vehicleRenderKeys(vehicles))

/** One Point per render key, in list order. The single mapping from keys to features. */
internal fun vehicleFeatureCollection(
    keys: List<VehicleRenderKey>,
): FeatureCollection<Point, JsonObject> =
    FeatureCollection(
        keys.map { key ->
            Feature(
                geometry = Point(Position(longitude = key.longitude, latitude = key.latitude)),
                properties =
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(key.vehicleId),
                            "label" to JsonPrimitive(key.label),
                            "type" to JsonPrimitive(key.vehicleTypeName),
                        ),
                    ),
            )
        },
    )

/**
 * Only the vehicle fields that reach the map. [vehicleFeatures] builds exclusively from these keys, so the memoized
 * collection cannot go stale by construction. Feed-volatile fields (timestamp, bearing, speed, status, stopId, tripId,
 * raw label, routeColor, tripHeadsign, and routeId while routeShortName is set) are deliberately excluded so stationary
 * polls reuse the previous collection. Note routeId IS rendered via the vehicleLabel fallback when routeShortName is
 * null/blank.
 */
internal data class VehicleRenderKey(
    val vehicleId: String,
    val longitude: Double,
    val latitude: Double,
    val label: String,
    val vehicleTypeName: String,
)

/** Render keys for [vehicles] in feed order, so the memoized collection matches [vehicleFeatures] exactly. */
internal fun vehicleRenderKeys(vehicles: List<VehiclePosition>): List<VehicleRenderKey> = vehicles.map { vehicle ->
    VehicleRenderKey(
        vehicleId = vehicle.vehicleId,
        longitude = vehicle.longitude,
        latitude = vehicle.latitude,
        label = vehicleLabel(vehicle),
        vehicleTypeName = vehicle.vehicleType.name,
    )
}

/** The route shape, or null when it has fewer than 2 points (a LineString needs at least 2). */
internal fun routeLine(route: RouteDisplayData): FeatureCollection<LineString, JsonObject>? {
    if (route.polylinePoints.size < 2) return null
    val line = LineString(route.polylinePoints.map { Position(longitude = it.lon, latitude = it.lat) })
    return FeatureCollection(Feature(geometry = line, properties = JsonObject(emptyMap())))
}

/** One Point per stop on the route; properties `id` and `name`. */
internal fun routeStopFeatures(route: RouteDisplayData): FeatureCollection<Point, JsonObject> =
    FeatureCollection(
        route.stops.map { stop ->
            Feature(
                geometry = Point(Position(longitude = stop.lon, latitude = stop.lat)),
                properties =
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(stop.stopId),
                            "name" to JsonPrimitive(stop.stopName),
                        ),
                    ),
            )
        },
    )

/** The selected vehicle if it is still in the latest feed update. */
internal fun selectedVehicle(
    vehicles: List<VehiclePosition>,
    selectedId: String?,
): VehiclePosition? = selectedId?.let { id -> vehicles.firstOrNull { it.vehicleId == id } }

/** The `id` property of a clicked vehicle feature. */
internal fun clickedVehicleId(feature: Feature<*, JsonObject?>): String? =
    feature.properties?.get("id")?.jsonPrimitive?.contentOrNull
