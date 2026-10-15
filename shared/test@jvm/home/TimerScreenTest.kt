@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
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
        prepLine = "${formatClock(config.prepSeconds)} to get ready",
        presets = builtInPresets.map { PresetChip(it, it.config.description, selected = it == preset) },
    )
}

class TimerScreenTest {
    private val calls = mutableListOf<String>()
    private val steps = mutableListOf<Pair<ConfigField, Int>>()
    private val sets = mutableListOf<Pair<ConfigField, Int>>()
    private val applied = mutableListOf<Preset>()
    private val saved = mutableListOf<String>()

    @Composable
    private fun Screen(state: TimerUiState = state()) {
        TimerScreen(
            state = state,
            onStep = { field, direction -> steps += field to direction },
            onSet = { field, value -> sets += field to value },
            onApplyPreset = { applied += it },
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
        onNodeWithText("See all").performClick()
        assertEquals(listOf("start", "settings", "see all"), calls)
    }

    @Test
    fun `tapping a preset applies it`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Tabata").performClick()
        assertEquals(listOf(builtInPresets.first()), applied)
    }

    @Test
    fun `a value opens its sheet, which steps and picks`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Effort interval").performClick()
        onNodeWithContentDescription("More work time").performClick()
        onNodeWithContentDescription("Less work time").performClick()
        onNodeWithText("3:00").performClick()
        assertEquals(listOf(ConfigField.Work to 1, ConfigField.Work to -1), steps)
        assertEquals(listOf(ConfigField.Work to 180), sets)
    }

    @Test
    fun `a custom shape can be saved as a preset`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Custom").assertExists()
        onNodeWithContentDescription("Save as preset").performClick()
        onNode(hasSetTextAction()).performTextInput("Sprints")
        onNodeWithText("Save").performClick()
        assertEquals(listOf("Sprints"), saved)
        onNode(hasSetTextAction()).assertDoesNotExist()
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
    fun `sideways, Start and the presets are both on screen`() = runUiTest(Window.PhoneSideways) {
        setThemedContent { Screen() }
        onNodeWithText("Start workout").assertExists()
        onNodeWithText("Tabata").assertExists()
    }
}
