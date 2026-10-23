@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.session

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.justinsmith.bells.domain.BuiltInExercise
import dev.justinsmith.bells.domain.EmomPhase
import dev.justinsmith.bells.domain.EmomState
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.domain.program
import dev.justinsmith.bells.today.TodayScreen
import dev.justinsmith.bells.today.TodayUiState
import dev.justinsmith.bells.ui.Window
import dev.justinsmith.bells.ui.bellsAppTest
import dev.justinsmith.bells.ui.runUiTest
import dev.justinsmith.bells.ui.setBellsContent
import dev.justinsmith.bells.ui.waitForText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Lift on a tablet: the cards side by side, a session in two columns either way up, an EMOM's readout in a
 * bigger ring.
 */
class TabletTest {
    @Test
    fun `Today puts the last session beside the program`() = runUiTest(Window.Tablet) {
        val state = TodayUiState(following = program(ProgramId.GobletSquatRow))
        setBellsContent { TodayScreen(state, onStartSession = {}, onChooseProgram = {}) }
        val last = onNodeWithText("No sessions yet").assertIsDisplayed().getUnclippedBoundsInRoot()
        val following = onNodeWithText("Goblet squat and row").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(following.left >= last.right)
    }

    @Test
    fun `Programs lays its families side by side`() = bellsAppTest(window = Window.Tablet) {
        waitForText("Follow a program")
        onNodeWithText("Follow a program").performClick()
        waitForText("Getting started")
        val beginner = onNodeWithText("Getting started").assertIsDisplayed().getUnclippedBoundsInRoot()
        val classics = onNodeWithText("Classics").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(classics.left >= beginner.right)
        assertTrue(classics.top == beginner.top)
    }

    @Test
    fun `a session on an upright tablet puts Log set beside the sets`() = bellsAppTest(window = Window.TabletPortrait) {
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        val log = onNodeWithText("Log set").assertIsDisplayed().getUnclippedBoundsInRoot()
        val sets = onNodeWithText("Log your first set", substring = true).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(sets.left >= log.right)
    }

    @Test
    fun `an EMOM on a tablet keeps its readout inside a bigger ring`() = runUiTest(Window.Tablet) {
        val run = EmomRun(BuiltInExercise.TwoHandSwing, 24.0, 10, 10, firstSide = Side.Left, startedAtMillis = 0)
        setBellsContent { EmomScreen(run, EmomState(EmomPhase.Work, 3, 10, 45, 0.25f), onFinishSet = {}, onStop = {}) }
        val clock = onNodeWithText("0:45").assertIsDisplayed().getUnclippedBoundsInRoot()
        val phase = onNodeWithText("Work").assertIsDisplayed().getUnclippedBoundsInRoot()
        val task = onNodeWithText("Two-hand swing · 24 kg × 10").assertIsDisplayed().getUnclippedBoundsInRoot()
        // Over and under the clock, as on an upright phone, not beside it as on a phone on its side.
        assertTrue(phase.bottom <= clock.top && task.top >= clock.bottom)
        // Bigger than a phone's 96sp clock.
        assertTrue(clock.bottom - clock.top > 150.dp)
        onNodeWithText("Set done").assertIsDisplayed()
    }

    @Test
    fun `the summary shows Done without scrolling on a tablet either way up`() {
        val finished = Finished(LiftSession("s", 0, 60_000, "Day 1", emptyList()), emptyList())
        for (window in listOf(Window.Tablet, Window.TabletPortrait)) {
            runUiTest(window) {
                setBellsContent { SummaryScreen(finished, onDone = {}) }
                onAllNodesWithText("Done").onLast().assertIsDisplayed()
                onNodeWithText("Day 1").assertIsDisplayed()
            }
        }
    }
}
