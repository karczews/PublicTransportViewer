package com.github.karczews.publictransportviewer.map.maplibre

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.karczews.publictransportviewer.data.model.RouteDisplayData
import com.github.karczews.publictransportviewer.data.model.VehiclePosition
import com.github.karczews.publictransportviewer.ui.home.HomeMapDefaults
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel
import com.github.karczews.publictransportviewer.ui.home.MapCamera
import com.github.karczews.publictransportviewer.ui.home.vehicleTitle
import kotlinx.coroutines.flow.filter
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
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.ExpandingAttributionButton
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import com.github.karczews.publictransportviewer.core.R as CoreR

private const val MAPTOOLKIT_URL = "https://www.maptoolkit.org/"

// The Maptoolkit glyph server only serves the fonts its style uses (Noto Sans, Rosario);
// maplibre-compose's default font stack 404s there and labels would silently not render.
private val LABEL_FONT = const(listOf("Noto Sans Regular"))

/**
 * Home tab rendered with maplibre-compose.
 *
 * Maptoolkit attribution is mandatory: its logo (>= 24dp, links to maptoolkit.org) and the
 * style's attribution button must stay visible and uncovered. Our own overlay replaces
 * MapOverlay.Default, which also drops the MapLibre logo that would crowd it.
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

    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(styleUrl),
        // Start where the previous SDK's map left off (camera handoff), else at the shared defaults.
        initialCameraPosition = viewModel.camera.value?.let {
            CameraPosition(center = Position(longitude = it.lon, latitude = it.lat), zoom = it.zoom)
        } ?: CameraPosition(
            center = Position(longitude = HomeMapDefaults.CENTER_LON, latitude = HomeMapDefaults.CENTER_LAT),
            zoom = HomeMapDefaults.INITIAL_ZOOM,
        ),
        content = {
            routeDisplay?.let { RouteLayers(it) }
            VehicleLayer(vehicles, onVehicleSelected = viewModel::onVehicleSelected)
            if (hasLocationPermission) {
                LocationIndicatorLayer(id = "user_location", locationState = locationState)
            }
        },
    )
    LaunchedEffect(mapState) {
        snapshotFlow { mapState.isCameraMoving to mapState.cameraPosition }
            .filter { (moving, _) -> !moving }
            .collect { (_, position) ->
                viewModel.onCameraChanged(MapCamera(position.center.latitude, position.center.longitude, position.zoom))
            }
    }
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

    Box(modifier.fillMaxSize()) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            state = mapState,
            interactions = MapInteractions {
                callbacks { click { onUnhandled { viewModel.clearSelection(); ClickResult.Consume } } }
            },
            overlay = {
                Box(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
                    Image(
                        painter = painterResource(R.drawable.maptoolkit_attribution),
                        contentDescription = "Maptoolkit",
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .height(24.dp)
                            .testTag("maptoolkit_logo")
                            .clickable { uriHandler.openUri(MAPTOOLKIT_URL) },
                    )
                    ExpandingAttributionButton(Modifier.align(Alignment.BottomEnd))
                }
            },
        )

        selectedVehicle(vehicles, selectedVehicleId)?.let { vehicle ->
            ElevatedCard(
                Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    // 48dp clears the logo/attribution row; 72dp each side keeps long titles
                    // (they wrap) out of the recenter button's column (16dp margin + 48dp + gap).
                    .padding(start = 72.dp, end = 72.dp, bottom = 48.dp)
                    .testTag("vehicle_info_card"),
            ) {
                Text(vehicleTitle(vehicle), Modifier.padding(12.dp))
            }
        }

        FilledTonalIconButton(
            onClick = {
                if (!hasLocationPermission) {
                    locationState.requestPermission()
                    return@FilledTonalIconButton
                }
                locationState.lastLocation?.let { location ->
                    coroutineScope.launch {
                        mapState.animateCamera(
                            CameraUpdate(center = location.position, zoom = HomeMapDefaults.RECENTER_ZOOM),
                        )
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
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
}

@Composable
private fun RouteLayers(route: RouteDisplayData) {
    routeLine(route)?.let { line ->
        val lineSource = rememberGeoJsonSource(GeoJsonData.Features(line))
        // Casing = line widths + 2dp each side, like TomTom's outlineWidths = WidthByZoom(2.0).
        LineLayer(
            id = "route-casing",
            source = lineSource,
            color = const(Color(0x64000000)),
            width = interpolate(linear(), zoom(), 10 to const(10.dp), 14 to const(14.dp), 18 to const(18.dp)),
        )
        LineLayer(
            id = "route-line",
            source = lineSource,
            color = const(Color(route.routeColor)),
            width = interpolate(linear(), zoom(), 10 to const(6.dp), 14 to const(10.dp), 18 to const(14.dp)),
        )
    }

    val stopSource = rememberGeoJsonSource(GeoJsonData.Features(routeStopFeatures(route)))
    SymbolLayer(
        id = "route-stops",
        source = stopSource,
        iconImage = image(painterResource(CoreR.drawable.ic_stop_dot)),
        textField = format(span(feature["name"].asString())),
        textFont = LABEL_FONT,
        textColor = const(Color.DarkGray),
        textHaloColor = const(Color.White),
        textHaloWidth = const(1.dp),
        textSize = const(10.sp),
        textAnchor = const(SymbolAnchor.Top),
        textOffset = textOffset(0f.em, 0.8f.em),
        // A stop tap keeps the selection (TomTom's stop click is a no-op too).
        onClick = { ClickResult.Consume },
    )
}

@Composable
private fun VehicleLayer(
    vehicles: List<VehiclePosition>,
    onVehicleSelected: (VehiclePosition) -> Unit,
) {
    val vehicleSource = rememberGeoJsonSource(GeoJsonData.Features(vehicleFeatures(vehicles)))
    SymbolLayer(
        id = "vehicles",
        source = vehicleSource,
        iconImage = switch(
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
            features.firstOrNull()
                ?.let(::clickedVehicleId)
                ?.let { id -> vehicles.firstOrNull { it.vehicleId == id } }
                ?.let(onVehicleSelected)
            ClickResult.Consume
        },
    )
}
