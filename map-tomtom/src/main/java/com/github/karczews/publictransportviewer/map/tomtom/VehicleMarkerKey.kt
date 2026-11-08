package com.github.karczews.publictransportviewer.map.tomtom

import com.github.karczews.publictransportviewer.data.model.VehiclePosition
import com.github.karczews.publictransportviewer.data.model.VehicleType
import com.github.karczews.publictransportviewer.ui.home.vehicleLabel
import com.github.karczews.publictransportviewer.ui.home.vehicleTitle

/**
 * Only the vehicle fields that reach a TomTom marker. The marker builders read nothing but the key, so a memoized
 * marker cannot go stale by construction. Feed-volatile fields (timestamp, bearing, speed, status, stopId, tripId, raw
 * label, routeColor, and routeId while routeShortName is set) are deliberately excluded so stationary polls reuse the
 * previous marker params. Note routeId IS rendered via the vehicleLabel fallback when routeShortName is null/blank, and
 * the headsign IS rendered via the balloon title.
 */
internal data class VehicleMarkerKey(
    val vehicleId: String,
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val title: String,
    val vehicleType: VehicleType,
)

/** Render key for a single [vehicle]; the marker builders read only this. */
internal fun vehicleMarkerKey(vehicle: VehiclePosition): VehicleMarkerKey =
    VehicleMarkerKey(
        vehicleId = vehicle.vehicleId,
        latitude = vehicle.latitude,
        longitude = vehicle.longitude,
        label = vehicleLabel(vehicle),
        title = vehicleTitle(vehicle),
        vehicleType = vehicle.vehicleType,
    )

/** Render keys for [vehicles] in feed order. */
internal fun vehicleMarkerKeys(vehicles: List<VehiclePosition>): List<VehicleMarkerKey> =
    vehicles.map(::vehicleMarkerKey)
