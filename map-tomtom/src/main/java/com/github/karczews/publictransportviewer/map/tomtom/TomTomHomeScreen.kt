package com.github.karczews.publictransportviewer.map.tomtom

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.github.karczews.publictransportviewer.data.model.VehicleType
import com.github.karczews.publictransportviewer.ui.home.HomeMapDefaults
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel
import com.github.karczews.publictransportviewer.ui.home.vehicleIconRes
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
import com.tomtom.sdk.map.display.image.Image
import com.tomtom.sdk.map.display.image.ImageFactory
import com.tomtom.sdk.map.display.marker.Label
import kotlinx.coroutines.launch

private val LODZ_CENTER = GeoPoint(latitude = HomeMapDefaults.CENTER_LAT, longitude = HomeMapDefaults.CENTER_LON)

// Route line widths per zoom level, mirroring the MapLibre casing + line pair.
private val ROUTE_LINE_WIDTHS =
    listOf(
        WidthByZoom(6.0, 10.0),
        WidthByZoom(10.0, 14.0),
        WidthByZoom(14.0, 18.0),
    )
private const val ROUTE_OUTLINE_ALPHA = 100

@Composable
fun TomTomHomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val vehicles by viewModel.vehiclePositions.collectAsStateWithLifecycle()
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsStateWithLifecycle()
    val routeDisplay by viewModel.routeDisplay.collectAsStateWithLifecycle()

    val initialCameraOptions =
        InitialCameraOptions.LocationBased(
            position = LODZ_CENTER,
            zoom = HomeMapDefaults.INITIAL_ZOOM,
        )
    val mapDisplayInfrastructure = rememberMapInfrastructure()
    val mapViewState = rememberMapViewState(initialCameraOptions = initialCameraOptions)
    val coroutineScope = rememberCoroutineScope()
    val locationProvider = remember { TomTomSdk.locationProvider }
    val (hasLocationPermission, requestPermission) = rememberLocationPermission()
    // Stable across recompositions so marker subtrees can skip when their inputs are unchanged
    // (a fresh `viewModel::onVehicleSelected` reference each composition would defeat skipping).
    val onSelect = remember(viewModel) { viewModel::onVehicleSelected }

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

            VehicleMarkers(
                vehicles = vehicles,
                selectedVehicleId = selectedVehicleId,
                onSelect = onSelect,
            )
        }

        RecenterButton(
            hasLocationPermission = hasLocationPermission,
            onRequestPermission = requestPermission,
            onRecenter = {
                locationProvider.lastKnownLocation?.let { location ->
                    coroutineScope.launch {
                        mapViewState.cameraState.animateCamera(
                            CameraOptions(
                                position = location.position,
                                zoom = HomeMapDefaults.RECENTER_ZOOM,
                            ),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun BoxScope.RecenterButton(
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRecenter: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = {
            if (!hasLocationPermission) {
                onRequestPermission()
                return@FilledTonalIconButton
            }
            onRecenter()
        },
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(48.dp).testTag("recenter_button"),
    ) {
        Icon(
            painter = painterResource(CoreR.drawable.ic_my_location),
            contentDescription = "Recenter to my location",
        )
    }
}

@Composable
private fun rememberMapInfrastructure(): MapDisplayInfrastructure = remember {
    MapDisplayInfrastructure(
        sdkContext = TomTomSdk.sdkContext,
    ) {
        locationInfrastructure = MapLocationInfrastructure {
            locationProvider = TomTomSdk.locationProvider
        }
    }
}

@Composable
private fun rememberLocationPermission(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted = it
        }
    DisposableEffect(granted) {
        if (granted) {
            TomTomSdk.locationProvider.enable()
            onDispose { TomTomSdk.locationProvider.disable() }
        } else {
            onDispose {}
        }
    }
    return granted to { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
}

@Composable
private fun VehicleMarkers(
    vehicles: List<VehiclePosition>,
    selectedVehicleId: String?,
    onSelect: (VehiclePosition) -> Unit,
) {
    // Pin images are hoisted so 200 markers don't rebuild them every 5s poll.
    val tramPin = remember { ImageFactory.fromResource(vehicleIconRes(VehicleType.TRAM)) }
    val busPin = remember { ImageFactory.fromResource(vehicleIconRes(VehicleType.BUS)) }
    // Fresh-vehicle lookup for taps: the map itself is cheap to rebuild per poll, while the
    // per-marker click lambdas below stay stable (keyed by callback + vehicle id) and always
    // resolve the latest vehicle instead of a stale captured one.
    val latestById = rememberUpdatedState(vehicles.associateBy { it.vehicleId })
    val selectById =
        remember(onSelect) {
            { id: String ->
                latestById.value[id]?.let(onSelect)
                Unit
            }
        }
    // Keys are computed once per poll here (cheap projection); each keyed child skips unless
    // its rendered fields changed, so volatile-only polls do no per-marker work at all.
    vehicleMarkerKeys(vehicles).forEach { markerKey ->
        key(markerKey.vehicleId) {
            VehicleMarker(
                markerKey = markerKey,
                isSelected = markerKey.vehicleId == selectedVehicleId,
                tramPin = tramPin,
                busPin = busPin,
                onSelectById = selectById,
            )
        }
    }
}

@Composable
private fun VehicleMarker(
    markerKey: VehicleMarkerKey,
    isSelected: Boolean,
    tramPin: Image,
    busPin: Image,
    onSelectById: (String) -> Unit,
) {
    val markerState = rememberMarkerState()
    LaunchedEffect(isSelected) {
        if (!isSelected && markerState.isSelected()) {
            markerState.deselect()
        }
    }
    // Marker params are memoized on the rendered projection only: volatile-only polls
    // (timestamp/bearing/speed/...) reuse identical data/properties/click instances, so the
    // marker node can skip instead of updating all 200 markers natively every 5s.
    // The builder reads nothing but the key, so the memoized values cannot go stale.
    val markerData =
        remember(markerKey) {
            MarkerData(
                geoPoint = GeoPoint(markerKey.latitude, markerKey.longitude),
            )
        }
    val markerProperties =
        remember(markerKey, tramPin, busPin) {
            MarkerProperties(
                pinImage =
                    when (markerKey.vehicleType) {
                        VehicleType.TRAM -> tramPin
                        VehicleType.BUS -> busPin
                    },
            ) {
                label =
                    Label(
                        text = markerKey.label,
                        textColor = Color.WHITE,
                        textSize = 12.0,
                        outlineColor = Color.BLACK,
                        outlineWidth = 2.0,
                    )
                balloonText = markerKey.title
            }
        }
    val onClick =
        remember(onSelectById, markerKey.vehicleId) {
            { onSelectById(markerKey.vehicleId) }
        }
    // Memoized too: it only reads the stable marker state and the stable click above,
    // so Marker keeps identical params (and skips) unless data/properties change.
    val markerClick =
        remember(markerState, onClick) {
            {
                if (markerState.isSelected()) markerState.deselect() else markerState.select()
                onClick()
            }
        }
    Marker(
        data = markerData,
        properties = markerProperties,
        state = markerState,
        onClick = markerClick,
    )
}

@Composable
private fun RouteOverlay(route: RouteDisplayData) {
    if (route.polylinePoints.isNotEmpty()) {
        val geoPoints =
            remember(route.routeId) {
                route.polylinePoints.map { GeoPoint(it.lat, it.lon) }
            }
        Polyline(
            data =
                PolylineData(geoPoints = geoPoints) {
                    tag = "route_${route.routeId}"
                },
            properties =
                PolylineProperties {
                    lineColor = route.routeColor
                    lineWidths = ROUTE_LINE_WIDTHS
                    outlineColor = Color.argb(ROUTE_OUTLINE_ALPHA, 0, 0, 0)
                    outlineWidths = listOf(WidthByZoom(2.0))
                },
            state = rememberPolylineState(),
        )
    }

    route.stops.forEach { stop ->
        key("stop_${stop.stopId}") {
            StopMarker(stop = stop)
        }
    }
}

@Composable
private fun StopMarker(stop: RouteStop) {
    Marker(
        data =
            MarkerData(
                geoPoint = GeoPoint(stop.lat, stop.lon),
            ),
        properties =
            MarkerProperties(
                pinImage = ImageFactory.fromResource(CoreR.drawable.ic_stop_dot),
            ) {
                label =
                    Label(
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
