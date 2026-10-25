package com.github.karczews.publictransportviewer.map.tomtom

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.github.karczews.publictransportviewer.FixedRouteDisplayRepository
import com.github.karczews.publictransportviewer.FixedVehicleRepository
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel
import com.github.karczews.publictransportviewer.ui.home.MapCamera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TomTomHomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreen_showsRecenterButton() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        runBlocking(Dispatchers.Main) { TomTomMapSdk().initialize(context) }
        val viewModel = HomeViewModel(FixedVehicleRepository(), FixedRouteDisplayRepository())
        composeRule.setContent {
            TomTomMapSdk().HomeScreen(viewModel, Modifier.fillMaxSize())
        }
        composeRule.onNodeWithTag("recenter_button").assertIsDisplayed()
    }

    @Test
    fun reshownScreen_startsAtHandedOffCamera() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        runBlocking(Dispatchers.Main) { TomTomMapSdk().initialize(context) }
        val viewModel = HomeViewModel(FixedVehicleRepository(), FixedRouteDisplayRepository())
        var shown by mutableStateOf(true)
        composeRule.setContent {
            if (shown) TomTomMapSdk().HomeScreen(viewModel, Modifier.fillMaxSize())
        }
        // First showing settles at the default camera and reports it.
        composeRule.waitUntil(20_000) { viewModel.camera.value != null }

        // Another SDK's screen left the camera somewhere else, then TomTom is shown again.
        val handoff = MapCamera(lat = 51.80, lon = 19.30, zoom = 15.0)
        composeRule.runOnIdle { shown = false }
        Thread.sleep(3_000) // the other SDK is on screen for a while, as in the app
        composeRule.runOnIdle { viewModel.onCameraChanged(handoff) }
        composeRule.runOnIdle { shown = true }
        composeRule.waitForIdle()
        // The re-shown screen composed from the handoff; overwrite the value it was given so only a
        // live camera reporter, on a map that really started at the handoff, can bring it back.
        composeRule.runOnIdle { viewModel.onCameraChanged(MapCamera(lat = 0.0, lon = 0.0, zoom = 1.0)) }
        Thread.sleep(5_000)
        composeRule.waitForIdle()

        val settled = viewModel.camera.value!!
        assertEquals(handoff.lat, settled.lat, 0.001)
        assertEquals(handoff.lon, settled.lon, 0.001)
        assertEquals(handoff.zoom, settled.zoom, 0.1)
    }
}
