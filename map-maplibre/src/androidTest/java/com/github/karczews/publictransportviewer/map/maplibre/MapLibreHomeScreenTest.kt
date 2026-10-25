package com.github.karczews.publictransportviewer.map.maplibre

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.github.karczews.publictransportviewer.FixedRouteDisplayRepository
import com.github.karczews.publictransportviewer.FixedVehicleRepository
import com.github.karczews.publictransportviewer.FixtureData
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel
import org.junit.Rule
import org.junit.Test

class MapLibreHomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(viewModel: HomeViewModel = HomeViewModel(FixedVehicleRepository(), FixedRouteDisplayRepository())) {
        composeRule.setContent {
            MapLibreMapSdk().HomeScreen(viewModel, Modifier.fillMaxSize())
        }
    }

    @Test
    fun homeScreen_showsMaptoolkitLogo() {
        setScreen()
        composeRule.onNodeWithTag("maptoolkit_logo").assertIsDisplayed()
    }

    @Test
    fun selectedVehicle_showsInfoCard() {
        val viewModel = HomeViewModel(FixedVehicleRepository(), FixedRouteDisplayRepository())
        setScreen(viewModel)
        composeRule.runOnIdle { viewModel.onVehicleSelected(FixtureData.tram10) }
        composeRule.onNodeWithTag("vehicle_info_card").assertIsDisplayed()
        composeRule.onNodeWithText("Tram 10 → Chocianowice IKEA").assertIsDisplayed()
    }
}
