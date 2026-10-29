package com.github.karczews.publictransportviewer.map.maplibre

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.karczews.publictransportviewer.FixedRouteDisplayRepository
import com.github.karczews.publictransportviewer.FixedVehicleRepository
import com.github.karczews.publictransportviewer.FixtureData
import com.github.karczews.publictransportviewer.ScreenshotGolden
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel
import org.junit.Rule
import org.junit.Test

/**
 * On-device screenshots of the MapLibre Home tab. JVM screenshot tools cannot render the native MapLibre GL surface, so
 * these run via `connectedAndroidTest` and compare against committed goldens (see [ScreenshotGolden]).
 */
class MapLibreHomeScreenScreenshotTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun home_default_matchesGolden() {
        setScreen()
        waitForMap()
        ScreenshotGolden.awaitMatchesGolden(
            capture = { composeRule.onRoot().captureToImage() },
            goldenAssetPath = "screenshots/maplibre_home_default.png",
        )
    }

    @Test
    fun home_selectedVehicle_matchesGolden() {
        val viewModel = HomeViewModel(FixedVehicleRepository(), FixedRouteDisplayRepository())
        setScreen(viewModel)
        waitForMap()
        composeRule.runOnIdle { viewModel.onVehicleSelected(FixtureData.tram10) }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasTestTag("vehicle_info_card")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        ScreenshotGolden.awaitMatchesGolden(
            capture = { composeRule.onRoot().captureToImage() },
            goldenAssetPath = "screenshots/maplibre_home_selected.png",
        )
    }

    private fun setScreen(
        viewModel: HomeViewModel = HomeViewModel(FixedVehicleRepository(), FixedRouteDisplayRepository()),
    ) {
        composeRule.setContent {
            MapLibreMapSdk().HomeScreen(viewModel, Modifier.fillMaxSize())
        }
    }

    private fun waitForMap() {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodes(hasTestTag("recenter_button")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("maptoolkit_logo").assertIsDisplayed()
        composeRule.waitForIdle()
    }
}
