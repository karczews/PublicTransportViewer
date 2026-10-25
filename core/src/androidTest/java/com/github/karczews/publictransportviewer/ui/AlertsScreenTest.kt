package com.github.karczews.publictransportviewer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.karczews.publictransportviewer.map.FakeMapSdk
import com.github.karczews.publictransportviewer.ui.theme.PublicTransportViewerTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AlertsScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Before
    fun setUp() {
        composeRule.setContent {
            PublicTransportViewerTheme { PublicTransportViewerApp(listOf(FakeMapSdk())) }
        }
        composeRule.onNodeWithText("Alerts").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun alertsScreen_showsTitle() {
        composeRule.onNodeWithText("Service Alerts").assertIsDisplayed()
    }

    @Test
    fun alertsScreen_showsAlertHeader() {
        composeRule.onNodeWithText("Tram 10 detour on Piotrkowska").assertIsDisplayed()
    }

    @Test
    fun alertsScreen_showsEffectLabel() {
        composeRule.onNodeWithText("DETOUR").assertIsDisplayed()
    }

    @Test
    fun alertsScreen_showsAffectedRoute() {
        composeRule.onNodeWithText("10").assertIsDisplayed()
    }

    @Test
    fun alertCard_expandShowsDescription() {
        composeRule.onNodeWithText("Tram 10 detour on Piotrkowska").performClick()
        composeRule.waitForIdle()
        composeRule
            .onNodeWithText("Due to roadworks, tram 10 is rerouted via Kilińskiego.")
            .assertIsDisplayed()
    }
}
