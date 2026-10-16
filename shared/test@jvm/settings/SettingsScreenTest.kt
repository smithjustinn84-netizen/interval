@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.data.AppSettings
import dev.justinsmith.interval.data.CueSettings
import dev.justinsmith.interval.data.ThemeMode
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

private val defaults = SettingsUiState(
    cues = CueSettings(vibrate = false),
    prepSeconds = 10,
    app = AppSettings(theme = ThemeMode.Dark),
)

class SettingsScreenTest {
    private val calls = mutableListOf<Any>()

    @Composable
    private fun Screen(state: SettingsUiState = defaults, inPane: Boolean = false) {
        SettingsScreen(
            state = state,
            onBack = { calls += "back" },
            onToggle = { calls += it },
            onStepPrep = { calls += "step $it" },
            onSetPrep = { calls += "set $it" },
            onToggleKeepScreenOn = { calls += "keep screen on" },
            onSetTheme = { calls += it },
            onShowAbout = { calls += "about" },
            inPane = inPane,
        )
    }

    @Test
    fun `the about row opens about`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("About Interval").performClick()
        assertEquals(listOf<Any>("about"), calls)
    }

    @Test
    fun `switches show their state and report toggles`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Sound cues").assertIsOn()
        onNodeWithText("Vibrate").assertIsOff()
        onNodeWithText("Sound cues").performClick()
        onNodeWithText("Voice").performClick()
        onNodeWithText("Halfway alert").performClick()
        onNodeWithText("Vibrate").performClick()
        onNodeWithText("Keep screen on").performClick()
        assertEquals(
            listOf<Any>(CueToggle.Sound, CueToggle.Voice, CueToggle.Halfway, CueToggle.Vibrate, "keep screen on"),
            calls,
        )
    }

    @Test
    fun `the theme picker shows and changes the theme`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Dark").assertIsSelected()
        onNodeWithText("Light").performClick()
        assertEquals(listOf<Any>(ThemeMode.Light), calls)
    }

    @Test
    fun `get ready opens its sheet`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Countdown before the first round").performClick()
        onNodeWithContentDescription("More get ready time").performClick()
        onNodeWithText("0:45").performClick()
        assertEquals(listOf<Any>("step 1", "set 45"), calls)
    }

    @Test
    fun `full screen it goes back, beside the timer it closes`() = runUiTest {
        var inPane by mutableStateOf(false)
        setThemedContent { Screen(inPane = inPane) }
        onNodeWithContentDescription("Back").performClick()
        onNodeWithContentDescription("Close settings").assertDoesNotExist()

        inPane = true
        onNodeWithContentDescription("Back").assertDoesNotExist()
        onNodeWithContentDescription("Close settings").performClick()
        assertEquals(listOf<Any>("back", "back"), calls)
    }
}
