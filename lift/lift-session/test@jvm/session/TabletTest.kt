@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.EmomPhase
import dev.justinsmith.lift.domain.EmomState
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.history.LiftDetailScreen
import dev.justinsmith.lift.history.LiftDetailUiState
import kotlin.test.Test
import kotlin.test.assertTrue

/** A session's pages on a tablet: a finished session in two columns, an EMOM's readout in a bigger ring. */
class TabletTest {
    @Test
    fun `a finished session on a tablet puts its lifts beside its numbers`() = runUiTest(Window.Tablet) {
        val sets = listOf(
            SetLog(1, BuiltInExercise.TwoHandSwing, 24.0, 10, null, 0),
            SetLog(2, BuiltInExercise.GobletSquat, 20.0, 8, null, 0),
        )
        val state = LiftDetailUiState(LiftSession("s", 0, 600_000, "Swings and squats", sets), "Saturday", "1:21 PM")
        setThemedContent(dark = true) { LiftDetailScreen(state, onDelete = {}, onBack = {}) }
        val moved = onNodeWithText("Moved").assertIsDisplayed().getUnclippedBoundsInRoot()
        val swing = onNodeWithText("24 kg × 10").assertIsDisplayed().getUnclippedBoundsInRoot()
        onNodeWithContentDescription("More options").assertIsDisplayed()
        assertTrue(swing.left > moved.right)
    }

    @Test
    fun `an EMOM on a tablet keeps its readout inside a bigger ring`() = runUiTest(Window.Tablet) {
        val run = EmomRun(BuiltInExercise.TwoHandSwing, 24.0, 10, 10, firstSide = Side.Left, startedAtMillis = 0)
        setThemedContent(dark = true) {
            EmomScreen(run, EmomState(EmomPhase.Work, 3, 10, 45, 0.25f), onFinishSet = {}, onStop = {})
        }
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
                setThemedContent(dark = true) { SummaryScreen(finished, onDone = {}) }
                onAllNodesWithText("Done").onLast().assertIsDisplayed()
                onNodeWithText("Day 1").assertIsDisplayed()
            }
        }
    }
}
