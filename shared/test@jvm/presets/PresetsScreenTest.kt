@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

private val morning = Preset("Morning", IntervalConfig(workSeconds = 25, restSeconds = 10, rounds = 8))

private fun row(preset: Preset, selected: Boolean = false) =
    PresetRowItem(preset, preset.config.description, formatClock(preset.config.totalSeconds), selected)

private fun state(custom: List<Preset> = listOf(morning), canSaveCurrent: Boolean = true) = PresetsUiState(
    builtIn = builtInPresets.map { row(it) },
    custom = custom.map { row(it) },
    canSaveCurrent = canSaveCurrent,
    currentSummary = "40s on · 20s off · ×6 · 5:50 total",
)

class PresetsScreenTest {
    private val applied = mutableListOf<Preset>()
    private val saved = mutableListOf<String>()
    private val deleted = mutableListOf<Preset>()

    @Composable
    private fun Screen(state: PresetsUiState = state()) {
        PresetsScreen(
            state = state,
            onApply = { applied += it },
            onSaveCurrent = { saved += it },
            onDelete = { deleted += it },
        )
    }

    @Test
    fun `tapping a preset applies it`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Hangboard").performClick()
        onNodeWithText("Morning").performClick()
        assertEquals(listOf(builtInPresets[2], morning), applied)
    }

    @Test
    fun `the user's own presets can be deleted`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithContentDescription("Delete preset Morning").performClick()
        assertEquals(listOf(morning), deleted)
        // Built-ins are not deletable.
        onNodeWithContentDescription("Delete preset Tabata").assertDoesNotExist()
    }

    @Test
    fun `the current settings can be saved as a preset`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithContentDescription("Save current settings as a preset").performClick()
        onNode(hasSetTextAction()).performTextInput("Evening")
        onNodeWithText("Save").performClick()
        assertEquals(listOf("Evening"), saved)
    }

    @Test
    fun `saving is off when the settings already match a preset`() = runUiTest {
        setThemedContent { Screen(state(canSaveCurrent = false)) }
        onNodeWithContentDescription("Save current settings as a preset").assertIsNotEnabled()
    }

    @Test
    fun `with no presets of their own the user is told where they go`() = runUiTest {
        setThemedContent { Screen(state(custom = emptyList())) }
        onNodeWithText("Presets you save show up here.").assertExists()
    }

    @Test
    fun `a tablet shows both groups`() = runUiTest(Window.Tablet) {
        setThemedContent { Screen() }
        onNodeWithText("Built-in").assertExists()
        onNodeWithText("Yours").assertExists()
        onNodeWithText("Morning").assertExists()
    }
}
