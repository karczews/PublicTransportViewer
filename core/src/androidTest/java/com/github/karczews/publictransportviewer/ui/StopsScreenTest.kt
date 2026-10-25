package com.github.karczews.publictransportviewer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.karczews.publictransportviewer.map.FakeMapSdk
import com.github.karczews.publictransportviewer.ui.theme.PublicTransportViewerTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StopsScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Before
    fun setUp() {
        composeRule.setContent {
            PublicTransportViewerTheme { PublicTransportViewerApp(listOf(FakeMapSdk())) }
        }
        composeRule.onNodeWithText("Stops").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun searchField_isDisplayed() {
        composeRule.onNodeWithTag("search_field").assertIsDisplayed()
    }

    @Test
    fun emptySearch_showsHint() {
        composeRule.onNodeWithText("Type a stop name to search").assertIsDisplayed()
    }

    @Test
    fun typingQuery_showsMatchingStops() {
        composeRule.onNodeWithTag("search_field").performTextInput("Piotrkowska")
        composeRule.waitUntil(3_000) {
            composeRule
                .onAllNodes(hasText("Piotrkowska Centrum"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Piotrkowska Centrum").assertIsDisplayed()
    }

    @Test
    fun typingQuery_filtersCorrectly() {
        composeRule.onNodeWithTag("search_field").performTextInput("Plac")
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodes(hasText("Plac Wolności")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Plac Wolności").assertIsDisplayed()
        composeRule.onNodeWithText("Piotrkowska Centrum").assertDoesNotExist()
    }

    @Test
    fun selectingStop_showsDepartures() {
        composeRule.onNodeWithTag("search_field").performTextInput("Piotrkowska")
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodes(hasTestTag("stop_row_s1")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("stop_row_s1").performClick()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodes(hasText("Chocianowice IKEA")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Chocianowice IKEA").assertIsDisplayed()
        composeRule.onNodeWithText("Retkinia").assertIsDisplayed()
        composeRule.onNodeWithText("Helenówek").assertIsDisplayed()
    }

    @Test
    fun selectingStop_showsRouteBadges() {
        composeRule.onNodeWithTag("search_field").performTextInput("Piotrkowska")
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodes(hasTestTag("stop_row_s1")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("stop_row_s1").performClick()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodes(hasText("Chocianowice IKEA")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onAllNodes(hasText("10"))[0].assertIsDisplayed()
        composeRule.onAllNodes(hasText("50"))[0].assertIsDisplayed()
    }

    @Test
    fun backButton_returnToStopList() {
        composeRule.onNodeWithTag("search_field").performTextInput("Piotrkowska")
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodes(hasTestTag("stop_row_s1")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("stop_row_s1").performClick()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodes(hasText("Chocianowice IKEA")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.waitUntil(3_000) {
            composeRule
                .onAllNodes(hasText("Piotrkowska Centrum"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Piotrkowska Centrum").assertIsDisplayed()
    }

    @Test
    fun noResults_showsEmptyMessage() {
        composeRule.onNodeWithTag("search_field").performTextInput("Nonexistent")
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodes(hasText("No stops found")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("No stops found").assertIsDisplayed()
    }
}
