package com.github.karczews.publictransportviewer.map.maplibre

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.karczews.publictransportviewer.ui.home.HomeMapDefaults
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.ExpandingAttributionButton
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

private const val MAPTOOLKIT_URL = "https://www.maptoolkit.org/"

/**
 * Home tab rendered with maplibre-compose.
 *
 * Maptoolkit attribution is mandatory: its logo (>= 24dp, links to maptoolkit.org) and the
 * style's attribution button must stay visible and uncovered. Our own [overlay] replaces
 * MapOverlay.Default, which also drops the MapLibre logo that would crowd it.
 */
@Composable
internal fun MapLibreHomeScreen(
    viewModel: HomeViewModel,
    styleUrl: String,
    modifier: Modifier = Modifier,
) {
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(styleUrl),
        initialCameraPosition = CameraPosition(
            center = Position(longitude = HomeMapDefaults.CENTER_LON, latitude = HomeMapDefaults.CENTER_LAT),
            zoom = HomeMapDefaults.INITIAL_ZOOM,
        ),
    )
    val uriHandler = LocalUriHandler.current

    Box(modifier.fillMaxSize()) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            state = mapState,
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
    }
}
