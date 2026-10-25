package com.github.karczews.publictransportviewer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.karczews.publictransportviewer.map.FakeMapSdk
import com.github.karczews.publictransportviewer.map.MapSdk
import com.github.karczews.publictransportviewer.ui.theme.PublicTransportViewerTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test

class NavigationTest {

    @get:Rule val composeRule = createComposeRule()

    private fun setApp(vararg sdks: MapSdk) {
        composeRule.setContent {
            PublicTransportViewerTheme { PublicTransportViewerApp(sdks.toList()) }
        }
    }

    @Test
    fun bottomNavigation_showsAllTabs() {
        setApp(FakeMapSdk())
        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Stops").assertIsDisplayed()
        composeRule.onNodeWithText("Alerts").assertIsDisplayed()
    }

    @Test
    fun navigateToStops_showsSearchField() {
        setApp(FakeMapSdk())
        composeRule.onNodeWithText("Stops").performClick()
        composeRule.onNodeWithTag("search_field").assertIsDisplayed()
    }

    @Test
    fun navigateToAlerts_showsTitle() {
        setApp(FakeMapSdk())
        composeRule.onNodeWithText("Alerts").performClick()
        composeRule.onNodeWithText("Service Alerts").assertIsDisplayed()
    }

    @Test
    fun homeTab_showsFakeMap() {
        setApp(FakeMapSdk())
        composeRule.onNodeWithTag("fake_map_fake").assertIsDisplayed()
    }

    @Test
    fun homeTab_showsSpinnerWhileInitializing() {
        setApp(FakeMapSdk(onInitialize = { awaitCancellation() }))
        composeRule.onNodeWithTag("map_sdk_loading").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_map_fake").assertDoesNotExist()
    }

    @Test
    fun homeTab_showsErrorWhenInitFails() {
        setApp(FakeMapSdk(onInitialize = { error("boom") }))
        composeRule.onNodeWithText("Couldn't start Fake: boom").assertIsDisplayed()
    }
}
