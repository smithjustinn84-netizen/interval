@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.waitForText
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.EmomPhase
import dev.justinsmith.lift.domain.EmomState
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.offeredExercises
import dev.justinsmith.lift.domain.program
import dev.justinsmith.lift.gear.GearScreen
import dev.justinsmith.lift.gear.GearUiState
import dev.justinsmith.lift.history.LiftDetailScreen
import dev.justinsmith.lift.history.LiftDetailUiState
import dev.justinsmith.lift.library.ExerciseLibraryScreen
import dev.justinsmith.lift.library.LibraryList
import dev.justinsmith.lift.today.TodayScreen
import dev.justinsmith.lift.today.TodayUiState
import dev.justinsmith.lift.ui.liftAppTest
import dev.justinsmith.lift.ui.setLiftContent
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
        setLiftContent { TodayScreen(state, onStartSession = {}, onChooseProgram = {}) }
        val last = onNodeWithText("No sessions yet").assertIsDisplayed().getUnclippedBoundsInRoot()
        val following = onNodeWithText("Goblet squat and row").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(following.left >= last.right)
    }

    @Test
    fun `Today puts the week beside the program's day`() = runUiTest(Window.Tablet) {
        val goblet = program(ProgramId.GobletSquatRow)
        val state = TodayUiState(following = goblet, programDay = goblet.dayAfter(0))
        setLiftContent { TodayScreen(state, onStartSession = {}, onChooseProgram = {}) }
        val week = onNodeWithText("This week").assertIsDisplayed().getUnclippedBoundsInRoot()
        val day = onNodeWithText("Day 1 of 8 · week 1").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(day.left >= week.right)
        assertTrue(day.top < week.bottom)
    }

    @Test
    fun `Your equipment puts the bar beside the bells`() = runUiTest(Window.Tablet) {
        setLiftContent { GearScreen(GearUiState(), onToggle = {}, onBack = {}) }
        val bells = onNodeWithText("Your bells").assertIsDisplayed().getUnclippedBoundsInRoot()
        val bar = onNodeWithText("Your bar").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(bar.left >= bells.right)
        assertTrue(bar.top < bells.bottom)
    }

    @Test
    fun `the exercise library lays its cards side by side`() = runUiTest(Window.Tablet) {
        setLiftContent {
            ExerciseLibraryScreen(LibraryList(offeredExercises(emptyList())), onFilters = {}, onBack = {})
        }
        val bells = onNodeWithText("Kettlebell · 19").assertIsDisplayed().getUnclippedBoundsInRoot()
        val bars = onNodeWithText("Barbell · 20").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(bars.left >= bells.right)
        assertTrue(bars.top < bells.bottom)
    }

    @Test
    fun `Programs lays its families side by side`() = liftAppTest(window = Window.Tablet) {
        waitForText("Follow a program")
        onNodeWithText("Follow a program").performClick()
        waitForText("Getting started")
        val beginner = onNodeWithText("Getting started").assertIsDisplayed().getUnclippedBoundsInRoot()
        val classics = onNodeWithText("Classics").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(classics.left >= beginner.right)
        assertTrue(classics.top == beginner.top)
    }

    @Test
    fun `a session on an upright tablet keeps one wider column`() = liftAppTest(window = Window.TabletPortrait) {
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        waitForText("16 kg × 10")
        val time = onNodeWithText("Time").assertIsDisplayed().getUnclippedBoundsInRoot()
        // Straight after a set, its rest counts down where Since last set stands.
        val since = onNodeWithText("Rest").assertIsDisplayed().getUnclippedBoundsInRoot()
        val set = onNodeWithText("16 kg × 10").assertIsDisplayed().getUnclippedBoundsInRoot()
        val log = onNodeWithText("Log set").assertIsDisplayed().getUnclippedBoundsInRoot()
        // The numbers in one row, the sets under them and Log set at the foot, not a column of each.
        assertTrue(since.top == time.top && since.left > time.right)
        assertTrue(set.top > time.bottom && log.top > set.bottom)
    }

    @Test
    fun `a finished session on a tablet puts its lifts beside its numbers`() = runUiTest(Window.Tablet) {
        val sets = listOf(
            SetLog(1, BuiltInExercise.TwoHandSwing, 24.0, 10, null, 0),
            SetLog(2, BuiltInExercise.GobletSquat, 20.0, 8, null, 0),
        )
        val state = LiftDetailUiState(LiftSession("s", 0, 600_000, "Swings and squats", sets), "Saturday", "1:21 PM")
        setLiftContent { LiftDetailScreen(state, onDelete = {}, onBack = {}) }
        val moved = onNodeWithText("Moved").assertIsDisplayed().getUnclippedBoundsInRoot()
        val swing = onNodeWithText("24 kg × 10").assertIsDisplayed().getUnclippedBoundsInRoot()
        onNodeWithContentDescription("More options").assertIsDisplayed()
        assertTrue(swing.left > moved.right)
    }

    @Test
    fun `an EMOM on a tablet keeps its readout inside a bigger ring`() = runUiTest(Window.Tablet) {
        val run = EmomRun(BuiltInExercise.TwoHandSwing, 24.0, 10, 10, firstSide = Side.Left, startedAtMillis = 0)
        setLiftContent { EmomScreen(run, EmomState(EmomPhase.Work, 3, 10, 45, 0.25f), onFinishSet = {}, onStop = {}) }
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
                setLiftContent { SummaryScreen(finished, onDone = {}) }
                onAllNodesWithText("Done").onLast().assertIsDisplayed()
                onNodeWithText("Day 1").assertIsDisplayed()
            }
        }
    }
}
