@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.presets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.interval.fakes.hangboardPreset
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `a card opens its sheet, which starts the preset or puts it on the Timer`() = runUiTest {
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
    fun `tapping a card changes nothing by itself`() = runUiTest {
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
        onNodeWithText("History keeps the workouts you did with it. The Timer keeps its settings.").assertExists()
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
    fun `the card on the Timer says so`() = runUiTest {
        setThemedContent { Screen(state(selected = morning, canSaveCurrent = false)) }
        onNodeWithText("On the Timer").assertExists()
        onNodeWithText("On the Timer · not saved").assertDoesNotExist()
    }

    @Test
    fun `cards show each preset's shape`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("25s on · 10s off · ×8").assertExists()
    }

    @Test
    fun `an estimate made with the Timer's activity says which`() = runUiTest {
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
        onNodeWithText("20s on · 10s off · ×8 · ≈ 43 kcal as Jump rope").assertExists()
        onNodeWithText("10s on · 50s off · ×6 · Climbing · ≈ 22 kcal").performScrollTo().assertExists()
    }

    @Test
    fun `with no presets of their own the user is told where they go`() = runUiTest {
        setThemedContent { Screen(state(custom = emptyList())) }
        onNodeWithText("Nothing saved yet").assertExists()
        onNodeWithText("Save one from the Timer or the end of a workout.").assertExists()
    }

    @Test
    fun `a tablet shows both groups`() = runUiTest(Window.Tablet) {
        setThemedContent { Screen() }
        onNodeWithText("Built-in").assertExists()
        onNodeWithText("Yours").assertExists()
        onNodeWithText("Morning").assertExists()
    }
}
