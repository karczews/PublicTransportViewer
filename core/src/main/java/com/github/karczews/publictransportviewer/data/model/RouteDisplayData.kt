package com.github.karczews.publictransportviewer.data.model

data class RouteDisplayData(
    val routeId: String,
    val routeShortName: String,
    val routeColor: Int,
    val polylinePoints: List<LatLon>,
    val stops: List<RouteStop>,
)

data class LatLon(val lat: Double, val lon: Double)

data class RouteStop(
    val stopId: String,
    val stopName: String,
    val lat: Double,
    val lon: Double,
)
