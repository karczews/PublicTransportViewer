package com.github.karczews.publictransportviewer.map.tomtom

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.github.karczews.publictransportviewer.FixedRouteDisplayRepository
import com.github.karczews.publictransportviewer.FixedVehicleRepository
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class TomTomHomeScreenTest {

  @get:Rule val composeRule = createComposeRule()

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
}
