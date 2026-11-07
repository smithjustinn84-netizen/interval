@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.presets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.fakes.hangboardPreset
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresetsScreenTest {
    private val started = mutableListOf<Preset>()
    private val putOnTimer = mutableListOf<Preset>()
    private val saved = mutableListOf<Preset>()
    private val deleted = mutableListOf<Preset>()

    @Composable
    private fun Screen(state: PresetsUiState = state()) {
        PresetsScreen(
            state = state,
            onStart = { started += it },
            onPutOnTimer = { putOnTimer += it },
            onSave = { saved += it },
            onSaveCurrent = { saved += it },
            onDelete = { deleted += it },
        )
    }

    @Test
    fun `a row opens its sheet, which starts the preset or puts it on the Timer`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Hangboard").performClick()
        inSheet("Built-in").assertExists()
        inSheet("10s on · 50s off · ×6 · Climbing").assertExists()
        onNodeWithText("0:10 to get ready").assertExists()
        onNodeWithText("Start workout").performClick()
        assertEquals(listOf(hangboardPreset), started)
        onNodeWithText("Start workout").assertDoesNotExist()

        onNodeWithText("Morning").performClick()
        onNodeWithText("Put on Timer").performClick()
        assertEquals(listOf(morning), putOnTimer)
        onNodeWithText("Put on Timer").assertDoesNotExist()
    }

    @Test
    fun `a sheet warns that unsaved settings on the Timer will be replaced`() = runUiTest {
        var state by mutableStateOf(state())
        setThemedContent { Screen(state) }
        onNodeWithText("Tabata").performClick()
        onNodeWithText("Replaces the unsaved settings on the Timer.").assertExists()
        state = state(canSaveCurrent = false, selected = morning)
        onNodeWithText("Replaces the unsaved settings on the Timer.").assertDoesNotExist()
    }

    @Test
    fun `the preset already on the Timer only offers to start`() = runUiTest {
        setThemedContent { Screen(state(canSaveCurrent = false, selected = morning)) }
        onNodeWithText("Morning").performClick()
        onNodeWithText("Start workout").assertExists()
        onNodeWithText("Put on Timer").assertDoesNotExist()
    }

    @Test
    fun `tapping a row changes nothing by itself`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Tabata").performClick()
        assertEquals(emptyList(), started + putOnTimer)
    }

    @Test
    fun `the user's own presets offer Edit and Delete`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Morning").performClick()
        inSheet("Yours").assertExists()
        onNodeWithText("Edit").assertExists()
        onNodeWithText("Delete").assertExists()
        onNodeWithText("Save a copy").assertDoesNotExist()
    }

    @Test
    fun `built-ins offer a copy instead`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Tabata").performClick()
        onNodeWithText("Save a copy").assertExists()
        onNodeWithText("Edit").assertDoesNotExist()
        onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun `deleting asks first and cancelling keeps the preset`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Morning").performClick()
        onNodeWithText("Delete").performClick()
        onNodeWithText("Delete Morning?").assertExists()
        onNodeWithText("Your log keeps the workouts you did with it. The Timer keeps its settings.").assertExists()
        onNodeWithText("Cancel").performClick()
        assertEquals(emptyList(), deleted)
        onNodeWithText("Delete Morning?").assertDoesNotExist()

        onNodeWithText("Delete").performClick()
        onAllNodesWithText("Delete").onLast().performClick()
        waitForIdle()
        assertEquals(listOf(morning), deleted)
        onNodeWithText("Delete Morning?").assertDoesNotExist()
    }

    @Test
    fun `the row on the Timer says so`() = runUiTest {
        setThemedContent { Screen(state(selected = morning, canSaveCurrent = false)) }
        onNode(hasText("Morning") and hasText("On the Timer")).assertExists()
    }

    @Test
    fun `rows show each preset's shape and total in the order given`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("25s on · 10s off · ×8").assertExists()
        onNodeWithText("10s on · 50s off · ×6 · Climbing").assertExists()
        onNodeWithText("4:00").assertExists()
        val tabata = onNodeWithText("Tabata").fetchSemanticsNode().positionInRoot.y
        val hangboard = onNodeWithText("Hangboard").fetchSemanticsNode().positionInRoot.y
        assertTrue(tabata < hangboard, "Tabata above Hangboard")
    }

    @Test
    fun `the sheet carries the estimate and says which activity a borrowed one used`() = runUiTest {
        val state = state(custom = emptyList()).let { s ->
            s.copy(
                builtIn = s.builtIn.map { item ->
                    when (item.preset.name) {
                        "Tabata" -> item.copy(calories = 43, estimatedAs = Activity.JumpRope)
                        "Hangboard" -> item.copy(calories = 22)
                        else -> item
                    }
                },
            )
        }
        setThemedContent { Screen(state) }
        // The row says the shape alone; the estimate is the sheet's.
        onNodeWithText("20s on · 10s off · ×8").assertExists()
        onNodeWithText("≈ 43 kcal as Jump rope").assertDoesNotExist()
        onNodeWithText("Tabata").performClick()
        inSheet("20s on · 10s off · ×8").assertExists()
        inSheet("≈ 43 kcal as Jump rope").assertExists()
    }

    @Test
    fun `an estimate with the preset's own activity names no other`() = runUiTest {
        val state = state(custom = emptyList()).let { s ->
            s.copy(builtIn = s.builtIn.map { if (it.preset.name == "Hangboard") it.copy(calories = 22) else it })
        }
        setThemedContent { Screen(state) }
        onNodeWithText("Hangboard").performClick()
        inSheet("≈ 22 kcal").assertExists()
    }

    @Test
    fun `with no presets of their own there is no group of theirs`() = runUiTest {
        setThemedContent { Screen(state(custom = emptyList())) }
        onNodeWithText("Yours").assertDoesNotExist()
        onNodeWithText("Built-in").assertExists()
    }

    @Test
    fun `a tablet shows both groups`() = runUiTest(Window.Tablet) {
        setThemedContent { Screen() }
        onNodeWithText("Built-in").assertExists()
        onNodeWithText("Yours").assertExists()
        onNodeWithText("Morning").assertExists()
    }
}
