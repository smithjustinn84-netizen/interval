@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.ui.StateRestoration
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

/** The editor over [PresetsScreen], opened from a card's sheet, the + or the unsaved card's Save. */
class PresetEditorTest {
    private val saved = mutableListOf<Preset>()
    private val savedCurrent = mutableListOf<Preset>()

    @Composable
    private fun Screen(state: PresetsUiState = state()) {
        PresetsScreen(
            state = state,
            onStart = {},
            onPutOnTimer = {},
            onSave = { saved += it },
            onSaveCurrent = { savedCurrent += it },
            onDelete = {},
        )
    }

    @Test
    fun `editing a preset saves its new name and shape under its id`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Morning").performClick()
        onNodeWithText("Edit").performClick()
        onNodeWithText("Edit preset").assertExists()
        onNode(hasSetTextAction()).assertTextContains("Morning")
        onNodeWithText("7 / 24").assertExists()
        onNode(hasSetTextAction()).performTextClearance()
        onNode(hasSetTextAction()).performTextInput("Dawn")
        // Rounds opens the edit sheet; a quick pick sets it.
        onNodeWithText("×8").performClick()
        onNodeWithText("12").performClick()
        onNodeWithText("Done").performClick()
        onNodeWithText("×12").assertExists()
        saveInEditor()
        assertEquals(listOf(Preset("Dawn", morning.config.copy(rounds = 12), id = 7)), saved)
        onNodeWithText("Edit preset").assertDoesNotExist()
    }

    @Test
    fun `the editor's total counts the get-ready countdown and says where it comes from`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Morning").performClick()
        onNodeWithText("Edit").performClick()
        onNodeWithText("×8").performClick()
        onNodeWithText("12").performClick()
        // 12 × 25s and 11 × 10s, plus the 10s countdown: in the edit sheet and the editor under it,
        // then the editor alone.
        onAllNodesWithText("7:00").assertCountEquals(2)
        onNodeWithText("Done").performClick()
        onNodeWithText("7:00").assertExists()
        onNodeWithText("Get ready time comes from Settings.").assertExists()
        onNodeWithContentDescription("Edit get ready").assertDoesNotExist()
    }

    @Test
    fun `a new preset starts from the Timer's shape and takes its name from it`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithContentDescription("New preset").performClick()
        onNodeWithText("New preset").assertExists()
        onNodeWithText("0 / 24").assertExists()
        onNodeWithText("0:40").assertExists()
        onNodeWithText("0:20").assertExists()
        onNodeWithText("×6").assertExists()
        saveInEditor()
        assertEquals(listOf(Preset("40/20 ×6", current)), saved)
    }

    @Test
    fun `the unsaved settings card opens the editor on them`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("On the Timer · not saved").assertExists()
        onNodeWithText("40s on · 20s off · ×6").assertExists()
        onNodeWithText("Save").performClick()
        onNodeWithText("Save as preset").assertExists()
        onNode(hasSetTextAction()).performTextInput("Evening")
        saveInEditor()
        // Saved as the Timer's own settings, so it stays on the Timer; + leaves the Timer alone.
        assertEquals(listOf(Preset("Evening", current)), savedCurrent)
        assertEquals(emptyList(), saved)
    }

    @Test
    fun `a copy of a built-in is named after it`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Tabata").performClick()
        onNodeWithText("Save a copy").performClick()
        onNode(hasSetTextAction()).assertTextContains("Tabata copy")
        saveInEditor()
        assertEquals(listOf(Preset("Tabata copy", builtInPresets.first().config)), saved)
    }

    @Test
    fun `a name another preset has says what it will be saved as`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithContentDescription("New preset").performClick()
        onNodeWithText("Saves as Tabata 2").assertDoesNotExist()
        onNode(hasSetTextAction()).performTextInput("Tabata")
        onNodeWithText("Saves as Tabata 2").assertExists()
    }

    @Test
    fun `a shape another preset has is pointed out but not its own`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Tabata").performClick()
        onNodeWithText("Save a copy").performClick()
        onNodeWithText("Same shape as Tabata").assertExists()
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Morning").performClick()
        onNodeWithText("Edit").performClick()
        onNodeWithText("Same shape as Morning").assertDoesNotExist()
    }

    @Test
    fun `cancelling the editor saves nothing`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithContentDescription("New preset").performClick()
        onNodeWithText("Cancel").performClick()
        onNodeWithText("New preset").assertDoesNotExist()
        assertEquals(emptyList(), saved)
    }

    @Test
    fun `the editor keeps its name and shape across a restore`() = runUiTest {
        val restoration = StateRestoration(this)
        restoration.setContent { Screen() }
        onNodeWithContentDescription("New preset").performClick()
        onNode(hasSetTextAction()).performTextInput("Sprints")
        onNodeWithText("×6").performClick()
        onNodeWithContentDescription("More rounds").performClick()
        onNodeWithText("Done").performClick()
        restoration.emulateSaveAndRestore()
        onNodeWithText("New preset").assertExists()
        onNode(hasSetTextAction()).assertTextContains("Sprints")
        onNodeWithText("×7").assertExists()
    }
}
