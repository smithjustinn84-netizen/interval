@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.workout

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.timer.WorkoutState
import dev.justinsmith.interval.timer.builtInFlows
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

private val hips = builtInFlows.first { it.key == "hips-shoulders" }

/** Hips and shoulders from start to done: get ready, the hip switch, the change to the lunge, its two sides… */
private val states: List<WorkoutState> = generateSequence(WorkoutEngine.start(hips.config).state) { state ->
    if (state.isDone) null else WorkoutEngine.advance(state).state
}.toList()

private val getReady = states.first()
private val hipSwitch = states[1]
private val toLunge = states[2]
private val lungeLeft = states[3]
private val switchSides = states[4]
private val lungeRight = states[5]
private val lastMove = states.last { it.phase == Phase.Work }
private val done = states.last()

/** The workout screen running a mobility flow: the move, its side and its cue in place of rounds and work. */
class WorkoutFlowScreenTest {
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
            onShare = { calls += "share $it" },
        )
    }

    @Test
    fun `get ready names the first move and how it is done`() = runUiTest {
        setThemedContent { Screen(getReady.toUiState(running = true, flows = builtInFlows)) }
        onNodeWithText("Get ready").assertExists()
        onNodeWithText("90/90 hip switch").assertExists()
        onNodeWithText("Sit with both knees bent square. Swing them over to the other side and sit tall.")
            .assertExists()
        onNodeWithText("Move 1 of 6 · 8:00 left").assertExists()
    }

    @Test
    fun `a move worked through says what is next`() = runUiTest {
        setThemedContent { Screen(hipSwitch.toUiState(running = true, flows = builtInFlows)) }
        onNodeWithText("90/90 hip switch").assertExists()
        onNodeWithText("Move").assertExists()
        onNodeWithText("Next: Hip flexor lunge").assertExists()
        onNodeWithText("Work").assertDoesNotExist()
    }

    @Test
    fun `a change-over shows the move coming`() = runUiTest {
        setThemedContent { Screen(toLunge.toUiState(running = true, flows = builtInFlows)) }
        onNodeWithText("Get ready").assertExists()
        onNodeWithText("Hip flexor lunge").assertExists()
        onNodeWithText("Left side").assertExists()
        onNodeWithText("Move 2 of 6 · 6:50 left").assertExists()
    }

    @Test
    fun `a hold with sides says its side and then the other`() = runUiTest {
        setThemedContent { Screen(lungeLeft.toUiState(running = true, flows = builtInFlows)) }
        onNodeWithText("Hold").assertExists()
        onNodeWithText("Left side").assertExists()
        onNodeWithText("Next: right side").assertExists()
    }

    @Test
    fun `between the sides it says to switch and the right follows`() = runUiTest(Window.PhoneSideways) {
        setThemedContent { Screen(switchSides.toUiState(running = true, flows = builtInFlows)) }
        onNodeWithText("Switch sides").assertExists()
        onNodeWithText("Right side").assertExists()
        onNodeWithText("Hip flexor lunge").assertExists()
    }

    @Test
    fun `the right side says the move after it and the last move says so`() = runUiTest {
        setThemedContent { Screen(lungeRight.toUiState(running = true, flows = builtInFlows)) }
        onNodeWithText("Right side").assertExists()
        onNodeWithText("Next: Deep squat").assertExists()
    }

    @Test
    fun `the last move's last side says it is the last`() = runUiTest {
        setThemedContent { Screen(lastMove.toUiState(running = true, flows = builtInFlows)) }
        onNodeWithText("Shoulder circles").assertExists()
        onNodeWithText("Last move").assertExists()
    }

    @Test
    fun `the controls work as in any workout`() = runUiTest {
        setThemedContent { Screen(lungeLeft.toUiState(running = true, flows = builtInFlows)) }
        onNodeWithText("Pause").performClick()
        onNodeWithContentDescription("Skip to next interval").performClick()
        onNodeWithContentDescription("Back to previous interval").performClick()
        onNodeWithContentDescription("Add ten seconds").performClick()
        assertEquals(listOf("toggle pause", "skip", "back", "add time"), calls)
    }

    @Test
    fun `ending early counts the moves done`() = runUiTest {
        setThemedContent { Screen(lungeLeft.toUiState(running = true, flows = builtInFlows)) }
        onNodeWithContentDescription("End workout").performClick()
        onNodeWithText("1 of 6 moves done. What you finished is saved to history.").assertExists()
    }

    @Test
    fun `the summary counts its moves and names the flow with nothing to save`() = runUiTest {
        val state = done.toUiState(running = false, presetName = hips.name, elapsedSeconds = 480, flows = builtInFlows)
        setThemedContent { Screen(state) }
        onNodeWithText("6 moves · 8:00 total").assertExists()
        onNodeWithText("Hips and shoulders · Mobility · 6 moves").assertExists()
        onNodeWithText("Moves").assertExists()
        onNodeWithText("6/6").assertExists()
        onNodeWithText("Save as preset").assertDoesNotExist()
        onNodeWithText("Share").performClick()
        assertEquals(listOf("share Hips and shoulders · Mobility · 6 moves\n6/6 moves · 8:00"), calls)
    }
}
