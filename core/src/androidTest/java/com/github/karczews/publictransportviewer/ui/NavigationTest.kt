package com.github.karczews.publictransportviewer.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.karczews.publictransportviewer.map.FakeMapSdk
import androidx.test.platform.app.InstrumentationRegistry
import com.github.karczews.publictransportviewer.map.MapSdk
import com.github.karczews.publictransportviewer.map.MapSdkPreference
import com.github.karczews.publictransportviewer.ui.theme.PublicTransportViewerTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun clearSavedSdk() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(MapSdkPreference.FILE_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

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

    @Test
    fun singleSdk_hidesToggle() {
        setApp(FakeMapSdk("a", "A"))
        composeRule.onNodeWithTag("map_sdk_toggle").assertDoesNotExist()
    }

    @Test
    fun twoSdks_showToggleAndSwitchMap() {
        setApp(FakeMapSdk("a", "A"), FakeMapSdk("b", "B"))
        composeRule.onNodeWithTag("fake_map_a").assertIsDisplayed()

        composeRule.onNodeWithTag("map_sdk_option_b").performClick()

        composeRule.onNodeWithTag("fake_map_b").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_map_a").assertDoesNotExist()
    }

    @Test
    fun switchingDuringInit_initializesOnce() {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        setApp(FakeMapSdk("a", "A", onInitialize = { calls++; gate.await() }), FakeMapSdk("b", "B"))

        composeRule.onNodeWithTag("map_sdk_option_b").performClick()
        composeRule.onNodeWithTag("map_sdk_option_a").performClick()
        gate.complete(Unit)
        composeRule.waitForIdle()

        assertEquals(1, calls)
        composeRule.onNodeWithTag("fake_map_a").assertIsDisplayed()
    }
}
