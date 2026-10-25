package com.github.karczews.publictransportviewer.data.model

data class StopDeparture(
    val tripId: String,
    val routeId: String,
    val routeShortName: String,
    val tripHeadsign: String?,
    val scheduledDepartureSecondsOfDay: Int,
    val delaySeconds: Int?,
    val routeColor: String?,
    val vehicleType: VehicleType,
)
