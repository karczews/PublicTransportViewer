package com.github.karczews.publictransportviewer.map.maplibre

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.karczews.publictransportviewer.map.MapSdk
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel

/** Maptoolkit community style; no API key, but its attribution rules apply (see [MapLibreHomeScreen]). */
const val MAPTOOLKIT_SUMMER_STYLE = "https://styles.maptoolkit.org/summer.json"

class MapLibreMapSdk(private val styleUrl: String = MAPTOOLKIT_SUMMER_STYLE) : MapSdk {
    override val id = "maplibre"
    override val displayName = "MapLibre"

    @Composable
    override fun HomeScreen(viewModel: HomeViewModel, modifier: Modifier) {
        MapLibreHomeScreen(viewModel = viewModel, styleUrl = styleUrl, modifier = modifier)
    }
}
