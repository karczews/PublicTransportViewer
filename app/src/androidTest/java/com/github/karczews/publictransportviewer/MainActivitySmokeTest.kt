package com.github.karczews.publictransportviewer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * Launches the real MainActivity with this build's production map SDK list (fake repositories via
 * KoinTestRunner), so the activity wiring and the SDK start-up path stay covered on a device.
 */
class MainActivitySmokeTest {

  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun launch_showsNavigationAndStartsTheMapSdk() {
    composeRule.onNodeWithText("Home").assertIsDisplayed()
    composeRule.onNodeWithText("Stops").assertIsDisplayed()
    composeRule.onNodeWithText("Alerts").assertIsDisplayed()
    // The map SDK initialized and its Home screen rendered.
    composeRule.waitUntil(20_000) {
      composeRule.onAllNodes(hasTestTag("recenter_button")).fetchSemanticsNodes().isNotEmpty()
    }
  }
}
