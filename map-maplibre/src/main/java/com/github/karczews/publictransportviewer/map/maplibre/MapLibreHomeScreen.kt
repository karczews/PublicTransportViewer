package com.github.karczews.publictransportviewer.map.maplibre

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.karczews.publictransportviewer.core.R as CoreR
import com.github.karczews.publictransportviewer.data.model.RouteDisplayData
import com.github.karczews.publictransportviewer.data.model.VehiclePosition
import com.github.karczews.publictransportviewer.ui.home.HomeMapDefaults
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel
import com.github.karczews.publictransportviewer.ui.home.vehicleTitle
import com.github.karczews.publictransportviewer.ui.home.visibleRouteDisplay
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.textOffset
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.location.SystemSettingsLauncher
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.location.rememberSystemSettingsLauncher
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.ExpandingAttributionButton
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

private const val MAPTOOLKIT_URL = "https://www.maptoolkit.org/"

// The Maptoolkit glyph server only serves the fonts its style uses (Noto Sans, Rosario);
// maplibre-compose's default font stack 404s there and labels would silently not render.
private val LABEL_FONT = const(listOf("Noto Sans Regular"))

// Zoom stops shared by the route casing + line widths below.
private const val ZOOM_FAR = 10
private const val ZOOM_MID = 14
private const val ZOOM_NEAR = 18
private val ROUTE_CASING_COLOR = Color(0x64000000)

/**
 * Home tab rendered with maplibre-compose.
 *
 * Maptoolkit attribution is mandatory: its logo (>= 24dp, links to maptoolkit.org) and the style's attribution button
 * must stay visible and uncovered. Our own overlay replaces MapOverlay.Default (which bundles the MapLibre logo, scale
 * bar and compass); ours keeps only the Maptoolkit logo and the attribution button.
 */
@Composable
internal fun MapLibreHomeScreen(
    viewModel: HomeViewModel,
    styleUrl: String,
    modifier: Modifier = Modifier,
) {
    val vehicles by viewModel.vehiclePositions.collectAsStateWithLifecycle()
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsStateWithLifecycle()
    val routeDisplay by viewModel.routeDisplay.collectAsStateWithLifecycle()
    val locationState = rememberLocationState(provider = rememberDefaultLocationProvider())
    val hasLocationPermission = locationState.permission is LocationPermission.Granted

    val mapState =
        rememberHomeMapState(
            styleUrl = styleUrl,
            routeDisplay = routeDisplay,
            vehicles = vehicles,
            selectedVehicleId = selectedVehicleId,
            hasLocationPermission = hasLocationPermission,
            locationState = locationState,
            onSelect = viewModel::onVehicleSelected,
        )
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val settingsLauncher = rememberSystemSettingsLauncher()

    Box(modifier.fillMaxSize()) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            state = mapState,
            interactions =
                MapInteractions {
                    callbacks {
                        click {
                            onUnhandled {
                                viewModel.clearSelection()
                                ClickResult.Consume
                            }
                        }
                    }
                },
            overlay = { AttributionOverlay(uriHandler) },
        )

        selectedVehicle(vehicles, selectedVehicleId)?.let { vehicle ->
            SelectedVehicleCard(vehicle)
        }

        RecenterButton(
            hasLocationPermission = hasLocationPermission,
            locationState = locationState,
            settingsLauncher = settingsLauncher,
            onRecenter = { position ->
                coroutineScope.launch {
                    mapState.animateCamera(
                        CameraUpdate(center = position, zoom = HomeMapDefaults.RECENTER_ZOOM),
                    )
                }
            },
        )
    }
}

@Composable
private fun AttributionOverlay(uriHandler: UriHandler) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
        Image(
            painter = painterResource(R.drawable.maptoolkit_attribution),
            contentDescription = "Maptoolkit",
            modifier =
                Modifier.align(Alignment.BottomStart).height(24.dp).testTag("maptoolkit_logo").clickable {
                    uriHandler.openUri(MAPTOOLKIT_URL)
                },
        )
        ExpandingAttributionButton(Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun BoxScope.SelectedVehicleCard(vehicle: VehiclePosition) {
    ElevatedCard(
        Modifier.align(Alignment.BottomCenter)
            .safeDrawingPadding()
            // 112dp clears the 48dp recenter button sitting 56dp above the bottom edge,
            // so a wide card on a narrow screen doesn't slide under it.
            .padding(bottom = 112.dp)
            .testTag("vehicle_info_card"),
    ) {
        Text(vehicleTitle(vehicle), Modifier.padding(12.dp))
    }
}

@Composable
private fun BoxScope.RecenterButton(
    hasLocationPermission: Boolean,
    locationState: LocationState,
    settingsLauncher: SystemSettingsLauncher,
    onRecenter: (Position) -> Unit,
) {
    FilledTonalIconButton(
        onClick = {
            if (!hasLocationPermission) {
                val permission = locationState.permission
                if (permission is LocationPermission.NotGranted && permission.canRequest == false) {
                    settingsLauncher.openApplicationSettings()
                } else {
                    locationState.requestPermission()
                }
                return@FilledTonalIconButton
            }
            locationState.lastLocation?.let { location ->
                onRecenter(location.position)
            }
        },
        modifier =
            Modifier.align(Alignment.BottomEnd)
                .safeDrawingPadding()
                // 56dp clears the attribution button below.
                .padding(end = 16.dp, bottom = 56.dp)
                .size(48.dp)
                .testTag("recenter_button"),
    ) {
        Icon(
            painter = painterResource(CoreR.drawable.ic_my_location),
            contentDescription = "Recenter to my location",
        )
    }
}

@Composable
private fun rememberHomeMapState(
    styleUrl: String,
    routeDisplay: RouteDisplayData?,
    vehicles: List<VehiclePosition>,
    selectedVehicleId: String?,
    hasLocationPermission: Boolean,
    locationState: LocationState,
    onSelect: (VehiclePosition) -> Unit,
): MapState =
    rememberMapState(
        baseStyle = BaseStyle.Uri(styleUrl),
        initialCameraPosition =
            CameraPosition(
                center =
                    Position(
                        longitude = HomeMapDefaults.CENTER_LON,
                        latitude = HomeMapDefaults.CENTER_LAT,
                    ),
                zoom = HomeMapDefaults.INITIAL_ZOOM,
            ),
        content = {
            visibleRouteDisplay(routeDisplay, vehicles, selectedVehicleId)?.let { RouteLayers(it) }
            VehicleLayer(vehicles, onVehicleSelected = onSelect)
            if (hasLocationPermission) {
                LocationIndicatorLayer(id = "user_location", locationState = locationState)
            }
        },
    )

@Composable
private fun RouteLayers(route: RouteDisplayData) {
    routeLine(route)?.let { line ->
        val lineSource = rememberGeoJsonSource(GeoJsonData.Features(line))
        // Casing = line widths + 2dp each side, like TomTom's outlineWidths = WidthByZoom(2.0).
        LineLayer(
            id = "route-casing",
            source = lineSource,
            color = const(ROUTE_CASING_COLOR),
            width =
                interpolate(
                    linear(),
                    zoom(),
                    ZOOM_FAR to const(10.dp),
                    ZOOM_MID to const(14.dp),
                    ZOOM_NEAR to const(18.dp),
                ),
        )
        LineLayer(
            id = "route-line",
            source = lineSource,
            color = const(Color(route.routeColor)),
            width =
                interpolate(
                    linear(),
                    zoom(),
                    ZOOM_FAR to const(6.dp),
                    ZOOM_MID to const(10.dp),
                    ZOOM_NEAR to const(14.dp),
                ),
        )
    }

    val stopSource = rememberGeoJsonSource(GeoJsonData.Features(routeStopFeatures(route)))
    // Stops must stay visible next to vehicles and each other in dense areas (TomTom shows every
    // stop marker): icons ignore collision, and a colliding label may drop while its dot stays.
    SymbolLayer(
        id = "route-stops",
        source = stopSource,
        iconImage = image(painterResource(CoreR.drawable.ic_stop_dot)),
        iconAllowOverlap = const(true),
        textField = format(span(feature["name"].asString())),
        textFont = LABEL_FONT,
        textColor = const(Color.DarkGray),
        textHaloColor = const(Color.White),
        textHaloWidth = const(1.dp),
        textSize = const(10.sp),
        textAnchor = const(SymbolAnchor.Top),
        textOffset = textOffset(0f.em, 0.8f.em),
        textOptional = const(true),
        // A stop tap keeps the selection (TomTom's stop click is a no-op too).
        onClick = { ClickResult.Consume },
    )
}

@Composable
// onVehicleSelected matches the AdapterView onItemSelected naming convention.
@Suppress("ParameterNaming")
private fun VehicleLayer(
    vehicles: List<VehiclePosition>,
    onVehicleSelected: (VehiclePosition) -> Unit,
) {
    val vehicleSource = rememberGeoJsonSource(GeoJsonData.Features(vehicleFeatures(vehicles)))
    SymbolLayer(
        id = "vehicles",
        source = vehicleSource,
        iconImage =
            switch(
                feature["type"].asString(),
                case("TRAM", image(painterResource(CoreR.drawable.ic_tram))),
                fallback = image(painterResource(CoreR.drawable.ic_bus)),
            ),
        iconAllowOverlap = const(true),
        textField = format(span(feature["label"].asString())),
        textFont = LABEL_FONT,
        textColor = const(Color.White),
        textHaloColor = const(Color.Black),
        textHaloWidth = const(2.dp),
        textSize = const(12.sp),
        textAnchor = const(SymbolAnchor.Top),
        textOffset = textOffset(0f.em, 0.8f.em),
        textAllowOverlap = const(true),
        onClick = { features ->
            features
                .firstOrNull()
                ?.let(::clickedVehicleId)
                ?.let { id -> vehicles.firstOrNull { it.vehicleId == id } }
                ?.let(onVehicleSelected)
            ClickResult.Consume
        },
    )
}
