@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.timer.WorkoutState
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The engine's states from start to done, as the previews build them. */
private val states: List<WorkoutState> = generateSequence(WorkoutEngine.start(IntervalConfig()).state) { state ->
    if (state.isDone) null else WorkoutEngine.advance(state).state
}.toList()

private val firstWork = states.first { it.phase == Phase.Work }
private val firstRest = states.first { it.phase == Phase.Rest }
private val done = states.last()

class WorkoutScreenTest {
    private val calls = mutableListOf<String>()

    @Composable
    private fun Screen(state: WorkoutUiState) {
        WorkoutScreen(
            state = state,
            onTogglePause = { calls += "toggle pause" },
            onSkip = { calls += "skip" },
            onBack = { calls += "back" },
            onAddTime = { calls += "add time" },
            onRestart = { calls += "restart" },
            onEndEarly = { calls += "end early" },
            onSavePreset = { calls += "save ${it.name}" },
            onExit = { calls += "exit" },
        )
    }

    @Test
    fun `the controls report their actions`() = runUiTest {
        setThemedContent { Screen(firstWork.toUiState(running = true)) }
        onNodeWithText("Work").assertExists()
        onNodeWithText("Pause").performClick()
        onNodeWithContentDescription("Skip to next interval").performClick()
        onNodeWithContentDescription("Back to previous interval").performClick()
        onNodeWithContentDescription("Add ten seconds").performClick()
        assertEquals(listOf("toggle pause", "skip", "back", "add time"), calls)
    }

    @Test
    fun `paused, it says so and offers to resume`() = runUiTest {
        setThemedContent { Screen(firstWork.toUiState(running = false)) }
        onNodeWithText("Paused").assertExists()
        onNodeWithText("Tap anywhere to resume").assertExists()
        onNodeWithText("Resume").performClick()
        assertEquals(listOf("toggle pause"), calls)
    }

    @Test
    fun `ending before a round is done saves nothing`() = runUiTest {
        setThemedContent { Screen(firstWork.toUiState(running = true)) }
        onNodeWithContentDescription("End workout").performClick()
        onNodeWithText("No round is finished yet, so nothing is saved.").assertExists()
        onNodeWithText("End workout").performClick()
        assertEquals(listOf("toggle pause", "exit"), calls)
    }

    @Test
    fun `ending after a round keeps what was done`() = runUiTest {
        setThemedContent { Screen(firstRest.toUiState(running = false)) }
        onNodeWithContentDescription("End workout").performClick()
        onNodeWithText("1 of 8 rounds done. What you finished is saved to history.").assertExists()
        onNodeWithText("End workout").performClick()
        assertEquals(listOf("end early"), calls)
    }

    @Test
    fun `resuming from the end dialog carries on`() = runUiTest {
        val secondRound = states.first { it.phase == Phase.Work && it.round == 2 }
        var running by mutableStateOf(true)
        setThemedContent {
            WorkoutScreen(
                state = secondRound.toUiState(running = running),
                onTogglePause = { running = !running },
                onSkip = {},
                onBack = {},
                onAddTime = {},
                onRestart = {},
                onEndEarly = { calls += "end early" },
                onSavePreset = {},
                onExit = { calls += "exit" },
            )
        }
        onNodeWithContentDescription("End workout").performClick()
        assertFalse(running, "End pauses while it asks")
        onNode(hasText("Resume") and hasAnyAncestor(isDialog())).performClick()
        onNodeWithText("End workout?").assertDoesNotExist()
        assertTrue(running, "Resume carries on")
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `the keyboard drives the workout`() = runUiTest {
        setThemedContent { Screen(firstWork.toUiState(running = true)) }
        onNode(isFocused()).performKeyInput {
            pressKey(Key.Spacebar)
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionLeft)
        }
        assertEquals(listOf("toggle pause", "skip", "back"), calls)
    }

    @Test
    fun `the readout says where the workout is and what comes next`() = runUiTest {
        var state by mutableStateOf(states.first().toUiState(running = true))
        setThemedContent { Screen(state) }
        onNodeWithText("Get ready").assertExists()
        onNodeWithText("Next: work 0:30").assertExists()
        state = firstWork.toUiState(running = true)
        onNodeWithText("Round 1 of 8 · 5:45 left").assertExists()
        onNodeWithText("Next: rest 0:15").assertExists()
        state = firstRest.toUiState(running = true)
        onNodeWithText("Rest").assertExists()
        onNodeWithText("Next: round 2 of 8").assertExists()
        state = states.last { it.phase == Phase.Work }.toUiState(running = true)
        onNodeWithText("Last round — finish strong").assertExists()
    }

    @Test
    fun `a finished workout shows its summary`() = runUiTest {
        setThemedContent { Screen(done.toUiState(running = false)) }
        onNodeWithText("Saved to history").assertExists()
        onNodeWithText("8 rounds · 5:55 total").assertExists()
        onNodeWithText("Custom · 30s on · 15s off · ×8").assertExists()
        onNodeWithText("Repeat").performClick()
        onNode(hasText("Done") and hasClickAction()).performClick()
        onNodeWithText("Save as preset").performClick()
        onNode(hasSetTextAction()).performTextInput("Classic")
        onNodeWithText("Save").performClick()
        assertEquals(listOf("restart", "exit", "save Classic"), calls)
    }

    @Test
    fun `the summary shows calories only when there is an estimate`() = runUiTest {
        var state by mutableStateOf(done.toUiState(running = false))
        setThemedContent { Screen(state) }
        onNodeWithText("kcal").assertDoesNotExist()
        state = done.toUiState(running = false, calories = 64)
        onNodeWithText("kcal").assertExists()
        onNodeWithText("64").assertExists()
    }

    @Test
    fun `a workout ended early says how far it got`() = runUiTest {
        setThemedContent { Screen(firstRest.toUiState(running = false, ended = true, elapsedSeconds = 50)) }
        onNodeWithText("Ended").assertExists()
        onNodeWithText("1 of 8 rounds · 0:50").assertExists()
        onNodeWithText("1/8").assertExists()
    }

    @Test
    fun `sideways, the controls move to the rails`() = runUiTest(Window.PhoneSideways) {
        setThemedContent { Screen(firstWork.toUiState(running = true)) }
        onNodeWithContentDescription("End workout").assertExists()
        onNodeWithContentDescription("Skip to next interval").assertExists()
        onNodeWithText("Pause").performClick()
        assertEquals(listOf("toggle pause"), calls)
    }
}
