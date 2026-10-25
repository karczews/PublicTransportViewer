package com.github.karczews.publictransportviewer.data.model

data class VehiclePosition(
    val vehicleId: String,
    val routeId: String,
    val tripId: String? = null,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val bearing: Float,
    val speed: Float,
    val timestamp: Long,
    val vehicleType: VehicleType,
    val currentStatus: VehicleStatus,
    val stopId: String? = null,
    val routeShortName: String? = null,
    val tripHeadsign: String? = null,
    val routeColor: String? = null,
)

enum class VehicleType {
    TRAM,
    BUS,
}

enum class VehicleStatus {
    IN_TRANSIT_TO,
    STOPPED_AT,
    INCOMING_AT,
}
