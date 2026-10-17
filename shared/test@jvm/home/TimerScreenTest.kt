@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.sameShape
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

/** A shape no built-in preset has, which the screen calls Custom. */
private val custom = IntervalConfig(prepSeconds = 10, workSeconds = 40, restSeconds = 20, rounds = 6)

private fun state(config: IntervalConfig = custom) = TimerUiState(
    config = config,
    total = formatClock(config.totalSeconds),
    presetName = builtInPresets.firstOrNull { it.config.sameShape(config) }?.name,
)

class TimerScreenTest {
    private val calls = mutableListOf<String>()
    private val steps = mutableListOf<Pair<ConfigField, Int>>()
    private val sets = mutableListOf<Pair<ConfigField, Int>>()
    private val saved = mutableListOf<Preset>()

    @Composable
    private fun Screen(state: TimerUiState = state()) {
        TimerScreen(
            state = state,
            onStep = { field, direction -> steps += field to direction },
            onSet = { field, value -> sets += field to value },
            onSavePreset = { saved += it },
            onStart = { calls += "start" },
            onOpenSettings = { calls += "settings" },
            onSeeAllPresets = { calls += "see all" },
        )
    }

    @Test
    fun `the buttons report their actions`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Start workout").performClick()
        onNodeWithContentDescription("Settings").performClick()
        onNodeWithContentDescription("All presets").performClick()
        assertEquals(listOf("start", "settings", "see all"), calls)
    }

    @Test
    fun `each row steps its own value`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithContentDescription("More work time").performClick()
        onNodeWithContentDescription("Less rest time").performClick()
        onNodeWithContentDescription("More rounds").performClick()
        assertEquals(listOf(ConfigField.Work to 1, ConfigField.Rest to -1, ConfigField.Rounds to 1), steps)
    }

    @Test
    fun `anywhere on a row opens its sheet`() = runUiTest {
        setThemedContent { Screen() }
        // The card's start edge, over the stripe, outside the name and value.
        onNodeWithText("0:40").performTouchInput { click(centerLeft + Offset(2f, 0f)) }
        onAllNodesWithContentDescription("More work time").assertCountEquals(2)
    }

    @Test
    fun `a stepper at its limit does not open the sheet`() = runUiTest {
        setThemedContent { Screen(state(custom.copy(workSeconds = ConfigField.Work.range.first))) }
        onNodeWithContentDescription("Less work time").performClick()
        onAllNodesWithContentDescription("Less work time").assertCountEquals(1)
        assertEquals(emptyList(), steps)
    }

    @Test
    fun `a value opens its sheet, which steps and picks`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("0:40").performClick()
        // The sheet's steppers share their descriptions with the row's, and draw after them.
        onAllNodesWithContentDescription("More work time").onLast().performClick()
        onAllNodesWithContentDescription("Less work time").onLast().performClick()
        onNodeWithText("3:00").performClick()
        assertEquals(listOf(ConfigField.Work to 1, ConfigField.Work to -1), steps)
        assertEquals(listOf(ConfigField.Work to 180), sets)
    }

    @Test
    fun `a custom shape is called so and saves through the preset editor`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Custom · Total").assertExists()
        onNodeWithContentDescription("Save as preset").performClick()
        onNodeWithText("Save as preset").assertExists()
        onNode(hasSetTextAction()).performTextInput("Mine")
        onNodeWithText("Save").performClick()
        assertEquals(listOf(Preset("Mine", custom)), saved)
        onNodeWithText("Save as preset").assertDoesNotExist()
    }

    @Test
    fun `the total gives the get-ready countdown, if there is one`() = runUiTest {
        var state by mutableStateOf(state())
        setThemedContent { Screen(state) }
        onNodeWithText("5:50").assertExists()
        onNodeWithText("0:10 to get ready").assertExists()
        state = state(custom.copy(prepSeconds = 0))
        onNodeWithText("0:00 to get ready").assertDoesNotExist()
        onNodeWithText("0:10 to get ready").assertDoesNotExist()
    }

    @Test
    fun `a shape a preset has is named after it`() = runUiTest {
        setThemedContent { Screen(state(builtInPresets.first().config)) }
        onNodeWithText("Tabata · Total").assertExists()
        onNodeWithContentDescription("Save as preset").assertDoesNotExist()
    }

    @Test
    fun `sideways, the edit sheet is taller than the window and scrolls to Done`() = runUiTest(Window.PhoneSideways) {
        setThemedContent { Screen() }
        onNodeWithText("0:40").performClick()
        onNodeWithText("Done").performScrollTo().performClick()
        onNodeWithText("Quick picks").assertDoesNotExist()
    }

    @Test
    fun `sideways, Start and the steppers are both on screen`() = runUiTest(Window.PhoneSideways) {
        setThemedContent { Screen() }
        onNodeWithText("Start workout").assertExists()
        onNodeWithContentDescription("More rounds").assertExists()
    }
}
