package com.github.karczews.publictransportviewer.ui.home

import androidx.annotation.DrawableRes
import com.github.karczews.publictransportviewer.core.R
import com.github.karczews.publictransportviewer.data.model.VehiclePosition
import com.github.karczews.publictransportviewer.data.model.VehicleType

/** Route number shown on a vehicle marker; falls back to the route id when static data has none (missing or blank). */
fun vehicleLabel(vehicle: VehiclePosition): String =
    vehicle.routeShortName?.takeIf { it.isNotBlank() } ?: vehicle.routeId

/** One-line description of a vehicle, e.g. "Tram 10 → Chocianowice IKEA" or "Bus 50". */
fun vehicleTitle(vehicle: VehiclePosition): String {
    val typeName =
        when (vehicle.vehicleType) {
            VehicleType.TRAM -> "Tram"
            VehicleType.BUS -> "Bus"
        }
    val routeName = vehicleLabel(vehicle)
    val headsign = vehicle.tripHeadsign
    return if (headsign != null) "$typeName $routeName → $headsign" else "$typeName $routeName"
}

@DrawableRes
fun vehicleIconRes(type: VehicleType): Int =
    when (type) {
        VehicleType.TRAM -> R.drawable.ic_tram
        VehicleType.BUS -> R.drawable.ic_bus
    }
