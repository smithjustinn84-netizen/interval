@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.workout

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import dev.justinsmith.interval.model.CapScore
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

/** A twenty-minute cap, eight minutes in. */
private val cap = IntervalConfig(prepSeconds = 0, workSeconds = 1200, restSeconds = 0, rounds = 1, capped = true)
private val inCap = WorkoutEngine.tick(WorkoutEngine.start(cap).state, 480_000).state

/** The cap's screen as it stands with [rounds] done, the round under way 0:40 in, and a [best] before. */
private fun capState(rounds: Int = 5, best: Int? = 14, running: Boolean = true) = inCap.toUiState(running = running)
    .copy(
        cap = CapNow(rounds, roundSeconds = 40, paceSeconds = 88.takeIf { rounds > 0 }, best = best, live = true),
        caps = listOf(CapResult(CapScore(0, 1200, List(rounds) { 88_000L }), best)),
    )

class WorkoutCapScreenTest {
    private val calls = mutableListOf<String>()

    @Composable
    private fun Screen(state: WorkoutUiState) {
        WorkoutScreen(
            state = state,
            onTogglePause = { calls += "toggle pause" },
            onSkip = { calls += "skip" },
            onBack = { calls += "back" },
            onAddTime = { calls += "add time" },
            onRestart = {},
            onEndEarly = { calls += "end early" },
            onSavePreset = {},
            onExit = { calls += "exit" },
            onFinishRound = { calls += "round" },
            onUndoRound = { calls += "undo" },
        )
    }

    @Test
    fun `a cap shows its rounds its best and its pace`() = runUiTest {
        setThemedContent { Screen(capState()) }
        onNodeWithText("5 rounds").assertExists()
        onNodeWithText("Best: 14").assertExists()
        onNodeWithText("This round 0:40 · 1:28 a round").assertExists()
        onNodeWithText("Round 6 · 12:00 left").assertExists()
        onNodeWithText("Last round", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a first cap has no best and its first round no pace`() = runUiTest {
        setThemedContent { Screen(capState(rounds = 0, best = null)) }
        onNodeWithText("0 rounds").assertExists()
        onNodeWithText("Best", substring = true).assertDoesNotExist()
        onNodeWithText("This round 0:40").assertExists()
    }

    @Test
    fun `round done and undo stand where the time and back would`() = runUiTest {
        setThemedContent { Screen(capState()) }
        onNodeWithText("Round done").performClick()
        onNodeWithContentDescription("Take back the last round").performClick()
        onNodeWithContentDescription("Back to previous interval").assertDoesNotExist()
        onNodeWithContentDescription("Add ten seconds").assertDoesNotExist()
        waitForIdle()
        assertEquals(listOf("round", "undo"), calls)
    }

    @Test
    fun `getting ready for a cap keeps back and the added time`() = runUiTest {
        val ready = WorkoutEngine.start(cap.copy(prepSeconds = 10)).state
        val state = ready.toUiState(running = true)
            .copy(cap = CapNow(0, roundSeconds = null, paceSeconds = null, best = null, live = false))
        setThemedContent { Screen(state) }
        onNodeWithContentDescription("Add ten seconds").assertExists()
        onNodeWithContentDescription("Back to previous interval").assertExists()
        onNodeWithText("Round done").assertIsNotEnabled()
        onNodeWithText("Round 1 · ", substring = true).assertExists()
        onNodeWithText("Round 1 of", substring = true).assertDoesNotExist()
    }

    @Test
    fun `round done waits while paused`() = runUiTest {
        setThemedContent { Screen(capState(running = false)) }
        onNodeWithText("Round done").assertIsNotEnabled()
    }

    @Test
    fun `round done is live while the cap runs`() = runUiTest {
        setThemedContent { Screen(capState()) }
        onNodeWithText("Round done").assertIsEnabled()
    }

    @Test
    fun `enter is a round and backspace takes it back`() = runUiTest {
        setThemedContent { Screen(capState()) }
        onNode(isFocused()).performKeyInput {
            pressKey(Key.Enter)
            pressKey(Key.Backspace)
            pressKey(Key.Spacebar)
        }
        waitForIdle()
        assertEquals(listOf("round", "undo", "toggle pause"), calls)
    }

    @Test
    fun `sideways the cap keeps round done and undo in the rails`() = runUiTest(Window.PhoneSideways) {
        setThemedContent { Screen(capState()) }
        onNodeWithText("Round done").performClick()
        onNodeWithText("5 rounds").assertExists()
        onNodeWithContentDescription("Take back the last round").assertExists()
        waitForIdle()
        assertEquals(listOf("round"), calls)
    }

    @Test
    fun `the summary says the rounds in the cap and the pace`() = runUiTest {
        val over = WorkoutEngine.advance(inCap).state
        val score = CapScore(0, 1200, listOf(70_000L, 90_000L, 110_000L))
        setThemedContent {
            Screen(over.toUiState(running = false).copy(caps = listOf(CapResult(score, best = 2))))
        }
        onNodeWithText("New best: 3 rounds in 20 min").performScrollTo().assertExists()
        onNodeWithText("1:30 a round · fastest 1:10 · slowest 1:50").performScrollTo().assertExists()
    }

    @Test
    fun `ending a cap early says the rounds kept`() = runUiTest {
        setThemedContent { Screen(capState(rounds = 3)) }
        onNodeWithContentDescription("End workout").performClick()
        onNodeWithText("3 rounds done. They are saved to your log.").assertExists()
    }
}
