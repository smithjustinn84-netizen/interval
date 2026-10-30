@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.about

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

class AboutScreenTest {
    private val calls = mutableListOf<String>()

    @Test
    fun `shows the name and version, and leads to the licenses`() = runUiTest {
        setThemedContent {
            AboutScreen(version = "0.1.0", onBack = { calls += "back" }, onShowLicenses = { calls += "licenses" })
        }
        onNodeWithText("Interval").assertIsDisplayed()
        onNodeWithText("Version 0.1.0").assertIsDisplayed()
        onNodeWithText("Open-source licenses").performClick()
        onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("licenses", "back"), calls)
    }

    @Test
    fun `a build without a version says so`() = runUiTest {
        setThemedContent { AboutScreen(version = null, onBack = {}, onShowLicenses = {}) }
        onNodeWithText("Development build").assertIsDisplayed()
        onNodeWithText("Version", substring = true).assertDoesNotExist()
    }
}
