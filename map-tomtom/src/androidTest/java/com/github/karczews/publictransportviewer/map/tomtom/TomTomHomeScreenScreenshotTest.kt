package com.github.karczews.publictransportviewer.map.tomtom

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.github.karczews.publictransportviewer.FixedRouteDisplayRepository
import com.github.karczews.publictransportviewer.FixedVehicleRepository
import com.github.karczews.publictransportviewer.FixtureData
import com.github.karczews.publictransportviewer.ScreenshotGolden
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Rule
import org.junit.Test

/**
 * On-device screenshots of the TomTom Home tab. The TomTom SDK needs a real API key, so these tests skip when
 * `BuildConfig.TOMTOM_API_KEY` is blank (e.g. CI without the `TOMTOM_API_KEY` secret). JVM screenshot tools cannot
 * render the native TomTom surface, hence `connectedAndroidTest` with committed goldens (see [ScreenshotGolden]).
 *
 * Selection goes through [HomeViewModel.onVehicleSelected], the same callback a marker tap invokes. That covers the
 * app-owned selected state (route polyline plus stop markers); the SDK-owned marker balloon only appears on a physical
 * marker tap, which has no stable test hook, so these goldens intentionally capture the overlay without it.
 */
class TomTomHomeScreenScreenshotTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun home_default_matchesGolden() {
        setScreen()
        waitForMap()
        ScreenshotGolden.awaitMatchesGolden(
            capture = { composeRule.onRoot().captureToImage() },
            goldenAssetPath = "screenshots/tomtom_home_default.png",
            // TomTom vector tiles refine progressively and a cold start on software rendering
            // takes well beyond the default budget; warm runs still match on the first captures.
            timeoutMs = 120_000,
        )
    }

    @Test
    fun home_selectedVehicle_matchesGolden() {
        val viewModel = setScreen()
        waitForMap()
        composeRule.runOnIdle { viewModel.onVehicleSelected(FixtureData.tram10) }
        composeRule.waitForIdle()
        ScreenshotGolden.awaitMatchesGolden(
            capture = { composeRule.onRoot().captureToImage() },
            goldenAssetPath = "screenshots/tomtom_home_selected.png",
            timeoutMs = 120_000,
        )
    }

    private fun setScreen(): HomeViewModel {
        Assume.assumeFalse(
            "Skipping TomTom screenshot: no API key (set TOMTOM_API_KEY secret).",
            BuildConfig.TOMTOM_API_KEY.isBlank(),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        runBlocking(Dispatchers.Main) { TomTomMapSdk().initialize(context) }
        val viewModel = HomeViewModel(FixedVehicleRepository(), FixedRouteDisplayRepository())
        composeRule.setContent {
            TomTomMapSdk().HomeScreen(viewModel, Modifier.fillMaxSize())
        }
        return viewModel
    }

    private fun waitForMap() {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodes(hasTestTag("recenter_button")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }
}
