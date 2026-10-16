@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.sameShape
import dev.justinsmith.interval.ui.StateRestoration
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

/** A shape no built-in preset has, so the screen offers to save it. */
private val custom = IntervalConfig(prepSeconds = 10, workSeconds = 40, restSeconds = 20, rounds = 6)

private fun state(config: IntervalConfig = custom): TimerUiState {
    val preset = builtInPresets.firstOrNull { it.config.sameShape(config) }
    return TimerUiState(
        config = config,
        total = formatClock(config.totalSeconds),
        presetName = preset?.name,
        presets = builtInPresets.map { PresetChip(it, selected = it == preset) },
    )
}

class TimerScreenTest {
    private val calls = mutableListOf<String>()
    private val steps = mutableListOf<Pair<ConfigField, Int>>()
    private val sets = mutableListOf<Pair<ConfigField, Int>>()
    private val saved = mutableListOf<String>()

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
    fun `a custom shape can be saved as a preset`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Custom · Total").assertExists()
        onNodeWithContentDescription("Save as preset").performClick()
        onNode(hasSetTextAction()).performTextInput("Sprints")
        onNodeWithText("Save").performClick()
        assertEquals(listOf("Sprints"), saved)
        onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun `a preset saved without a name is numbered`() = runUiTest {
        setThemedContent { Screen(state().copy(nextCustomNumber = 3)) }
        onNodeWithContentDescription("Save as preset").performClick()
        onNodeWithText("40s on · 20s off · ×6 · 5:50 total").assertExists()
        onNodeWithText("Save").performClick()
        assertEquals(listOf("Custom 3"), saved)
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
    fun `a shape a preset already has offers no save`() = runUiTest {
        setThemedContent { Screen(state(builtInPresets.first().config)) }
        onNodeWithContentDescription("Save as preset").assertDoesNotExist()
    }

    @Test
    fun `the save dialog keeps its name across a restore`() = runUiTest {
        val restoration = StateRestoration(this)
        restoration.setContent { Screen() }
        onNodeWithContentDescription("Save as preset").performClick()
        onNode(hasSetTextAction()).performTextInput("Sprints")
        restoration.emulateSaveAndRestore()
        onNode(hasSetTextAction()).assertTextContains("Sprints")
    }

    @Test
    fun `sideways, Start and the steppers are both on screen`() = runUiTest(Window.PhoneSideways) {
        setThemedContent { Screen() }
        onNodeWithText("Start workout").assertExists()
        onNodeWithContentDescription("More rounds").assertExists()
    }
}
