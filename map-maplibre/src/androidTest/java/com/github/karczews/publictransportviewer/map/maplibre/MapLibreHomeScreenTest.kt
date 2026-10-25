package com.github.karczews.publictransportviewer.map.maplibre

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.github.karczews.publictransportviewer.FixedRouteDisplayRepository
import com.github.karczews.publictransportviewer.FixedVehicleRepository
import com.github.karczews.publictransportviewer.FixtureData
import com.github.karczews.publictransportviewer.data.repository.VehicleRepository
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertFalse
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
    fun homeScreen_showsRecenterButton() {
        setScreen()
        composeRule.onNodeWithTag("recenter_button").assertIsDisplayed()
    }

    @Test
    fun selectedVehicle_showsInfoCard() {
        val viewModel = HomeViewModel(FixedVehicleRepository(), FixedRouteDisplayRepository())
        setScreen(viewModel)
        composeRule.runOnIdle { viewModel.onVehicleSelected(FixtureData.tram10) }
        composeRule.onNodeWithTag("vehicle_info_card").assertIsDisplayed()
        composeRule.onNodeWithText("Tram 10 → Chocianowice IKEA").assertIsDisplayed()
    }

    @Test
    fun infoCard_staysClearOfRecenterButton_onNarrowPhone() {
        val vehicle = FixtureData.tram10.copy(tripHeadsign = "RADOGOSZCZ WSCHÓD PRZEZ CENTRUM")
        val repository = object : VehicleRepository {
            override fun observeVehiclePositions() = flowOf(listOf(vehicle))
        }
        val viewModel = HomeViewModel(repository, FixedRouteDisplayRepository())
        composeRule.setContent {
            Box(Modifier.width(360.dp).height(760.dp)) {
                MapLibreMapSdk().HomeScreen(viewModel, Modifier.fillMaxSize())
            }
        }
        composeRule.runOnIdle { viewModel.onVehicleSelected(vehicle) }

        val card = composeRule.onNodeWithTag("vehicle_info_card").getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithTag("recenter_button").getUnclippedBoundsInRoot()
        val overlaps = card.left < button.right && button.left < card.right &&
            card.top < button.bottom && button.top < card.bottom
        assertFalse("info card $card overlaps recenter button $button", overlaps)
    }
}
