@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.about

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.justinsmith.interval.ui.performClick
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test

class LicensesScreenTest {
    @Test
    fun `every credit names its license and holder`() = runUiTest {
        setThemedContent { LicensesScreen(onBack = {}) }
        onNodeWithText("Barlow").assertIsDisplayed()
        onNodeWithText("SIL Open Font License 1.1 · The Barlow Project Authors").assertIsDisplayed()
        onNodeWithText("MIT License · Composable Horizons").assertExists()
        onNodeWithText("Public domain").assertExists()
    }

    @Test
    fun `a credit opens to its license text, and one without a text cannot`() = runUiTest {
        setThemedContent { LicensesScreen(onBack = {}) }
        val ofl = hasText("SIL OPEN FONT LICENSE Version 1.1", substring = true)
        onNode(ofl).assertDoesNotExist()
        onNodeWithText("Barlow").assertHasClickAction().performClick()
        waitUntilExactlyOneExists(ofl)
        onNodeWithText("SQLite").assertHasNoClickAction()
    }

    @Test
    fun `the heart-rate sensor's SDK opens to Polar's licence agreement`() = runUiTest {
        setThemedContent { LicensesScreen(onBack = {}) }
        onNodeWithText("Polar BLE SDK").performScrollTo()
        onNodeWithText("Polar SDK License Agreement · Polar Electro Oy").assertIsDisplayed()
        onNodeWithText("Polar BLE SDK").performClick()
        waitUntilExactlyOneExists(hasText("SOFTWARE DEVELOPMENT KIT LIMITED LICENSE AGREEMENT", substring = true))
    }
}
