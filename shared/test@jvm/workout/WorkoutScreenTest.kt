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
            onSavePreset = { calls += "save $it" },
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
    fun `a finished workout shows its summary`() = runUiTest {
        setThemedContent { Screen(done.toUiState(running = false).copy(canSavePreset = true)) }
        onNodeWithText("Saved to history").assertExists()
        onNodeWithText("Repeat").performClick()
        onNode(hasText("Done") and hasClickAction()).performClick()
        onNodeWithText("Save as preset").performClick()
        onNode(hasSetTextAction()).performTextInput("Classic")
        onNodeWithText("Save").performClick()
        assertEquals(listOf("restart", "exit", "save Classic"), calls)
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
