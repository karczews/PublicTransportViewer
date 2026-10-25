package com.github.karczews.publictransportviewer.map.tomtom

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.karczews.publictransportviewer.core.R as CoreR
import com.github.karczews.publictransportviewer.data.model.RouteDisplayData
import com.github.karczews.publictransportviewer.data.model.RouteStop
import com.github.karczews.publictransportviewer.data.model.VehiclePosition
import com.github.karczews.publictransportviewer.ui.home.HomeMapDefaults
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel
import com.github.karczews.publictransportviewer.ui.home.vehicleIconRes
import com.github.karczews.publictransportviewer.ui.home.vehicleLabel
import com.github.karczews.publictransportviewer.ui.home.vehicleTitle
import com.github.karczews.publictransportviewer.ui.home.visibleRouteDisplay
import com.tomtom.sdk.init.TomTomSdk
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.map.display.MapLocationInfrastructure
import com.tomtom.sdk.map.display.camera.CameraOptions
import com.tomtom.sdk.map.display.camera.InitialCameraOptions
import com.tomtom.sdk.map.display.common.WidthByZoom
import com.tomtom.sdk.map.display.compose.TomTomMap
import com.tomtom.sdk.map.display.compose.model.MapDisplayInfrastructure
import com.tomtom.sdk.map.display.compose.model.MarkerData
import com.tomtom.sdk.map.display.compose.model.PolylineData
import com.tomtom.sdk.map.display.compose.nodes.CurrentLocationMarker
import com.tomtom.sdk.map.display.compose.nodes.Marker
import com.tomtom.sdk.map.display.compose.nodes.Polyline
import com.tomtom.sdk.map.display.compose.properties.CurrentLocationMarkerProperties
import com.tomtom.sdk.map.display.compose.properties.MarkerProperties
import com.tomtom.sdk.map.display.compose.properties.PolylineProperties
import com.tomtom.sdk.map.display.compose.state.rememberCurrentLocationMarkerState
import com.tomtom.sdk.map.display.compose.state.rememberMapViewState
import com.tomtom.sdk.map.display.compose.state.rememberMarkerState
import com.tomtom.sdk.map.display.compose.state.rememberPolylineState
import com.tomtom.sdk.map.display.image.ImageFactory
import com.tomtom.sdk.map.display.marker.Label
import kotlinx.coroutines.launch

private val LODZ_CENTER = GeoPoint(latitude = HomeMapDefaults.CENTER_LAT, longitude = HomeMapDefaults.CENTER_LON)

@Composable
fun TomTomHomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val vehicles by viewModel.vehiclePositions.collectAsStateWithLifecycle()
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsStateWithLifecycle()
    val routeDisplay by viewModel.routeDisplay.collectAsStateWithLifecycle()

    val initialCameraOptions = InitialCameraOptions.LocationBased(
        position = LODZ_CENTER,
        zoom = HomeMapDefaults.INITIAL_ZOOM,
    )
    val mapDisplayInfrastructure = remember {
        MapDisplayInfrastructure(
            sdkContext = TomTomSdk.sdkContext,
        ) {
            locationInfrastructure = MapLocationInfrastructure {
                locationProvider = TomTomSdk.locationProvider
            }
        }
    }
    val mapViewState = rememberMapViewState(initialCameraOptions = initialCameraOptions)
    val coroutineScope = rememberCoroutineScope()
    val locationProvider = remember { TomTomSdk.locationProvider }
    val context = LocalContext.current

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasLocationPermission = granted }

    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            locationProvider.enable()
            onDispose { locationProvider.disable() }
        } else {
            onDispose { }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        TomTomMap(
            state = mapViewState,
            infrastructure = mapDisplayInfrastructure,
            modifier = Modifier.fillMaxSize(),
            onMapClick = { viewModel.clearSelection() },
        ) {
            if (hasLocationPermission) {
                CurrentLocationMarker(
                    properties = CurrentLocationMarkerProperties(),
                    state = rememberCurrentLocationMarkerState(),
                )
            }

            visibleRouteDisplay(routeDisplay, vehicles, selectedVehicleId)?.let { route ->
                RouteOverlay(route = route)
            }

            vehicles.forEach { vehicle ->
                key(vehicle.vehicleId) {
                    VehicleMarker(
                        vehicle = vehicle,
                        isSelected = vehicle.vehicleId == selectedVehicleId,
                        onSelect = { viewModel.onVehicleSelected(vehicle) },
                    )
                }
            }
        }

        FilledTonalIconButton(
            onClick = {
                if (!hasLocationPermission) {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    return@FilledTonalIconButton
                }
                val location = locationProvider.lastKnownLocation
                if (location != null) {
                    coroutineScope.launch {
                        mapViewState.cameraState.animateCamera(
                            CameraOptions(position = location.position, zoom = HomeMapDefaults.RECENTER_ZOOM),
                        )
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(48.dp)
                .testTag("recenter_button"),
        ) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_my_location),
                contentDescription = "Recenter to my location",
            )
        }
    }
}

@Composable
private fun VehicleMarker(
    vehicle: VehiclePosition,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val markerState = rememberMarkerState()
    LaunchedEffect(isSelected) {
        if (!isSelected && markerState.isSelected()) {
            markerState.deselect()
        }
    }
    Marker(
        data = MarkerData(
            geoPoint = GeoPoint(vehicle.latitude, vehicle.longitude),
        ),
        properties = MarkerProperties(
            pinImage = ImageFactory.fromResource(vehicleIconRes(vehicle.vehicleType)),
        ) {
            label = Label(
                text = vehicleLabel(vehicle),
                textColor = Color.WHITE,
                textSize = 12.0,
                outlineColor = Color.BLACK,
                outlineWidth = 2.0,
            )
            balloonText = vehicleTitle(vehicle)
        },
        state = markerState,
        onClick = {
            if (markerState.isSelected()) markerState.deselect() else markerState.select()
            onSelect()
        },
    )
}

@Composable
private fun RouteOverlay(route: RouteDisplayData) {
    if (route.polylinePoints.isNotEmpty()) {
        val geoPoints = remember(route.routeId) {
            route.polylinePoints.map { GeoPoint(it.lat, it.lon) }
        }
        Polyline(
            data = PolylineData(geoPoints = geoPoints) {
                tag = "route_${route.routeId}"
            },
            properties = PolylineProperties {
                lineColor = route.routeColor
                lineWidths = listOf(
                    WidthByZoom(6.0, 10.0),
                    WidthByZoom(10.0, 14.0),
                    WidthByZoom(14.0, 18.0),
                )
                outlineColor = Color.argb(100, 0, 0, 0)
                outlineWidths = listOf(WidthByZoom(2.0))
            },
            state = rememberPolylineState(),
        )
    }

    route.stops.forEach { stop ->
        key("stop_${stop.stopId}") {
            StopMarker(stop = stop, routeColor = route.routeColor)
        }
    }
}

@Composable
private fun StopMarker(stop: RouteStop, routeColor: Int) {
    Marker(
        data = MarkerData(
            geoPoint = GeoPoint(stop.lat, stop.lon),
        ),
        properties = MarkerProperties(
            pinImage = ImageFactory.fromResource(CoreR.drawable.ic_stop_dot),
        ) {
            label = Label(
                text = stop.stopName,
                textColor = Color.DKGRAY,
                textSize = 10.0,
                outlineColor = Color.WHITE,
                outlineWidth = 1.0,
            )
            balloonText = stop.stopName
        },
        state = rememberMarkerState(),
        onClick = {
            // no-op for now
        },
    )
}
