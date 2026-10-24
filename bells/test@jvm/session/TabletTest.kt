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
import dev.justinsmith.bells.domain.SetLog
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.domain.offeredExercises
import dev.justinsmith.bells.domain.program
import dev.justinsmith.bells.gear.GearScreen
import dev.justinsmith.bells.gear.GearUiState
import dev.justinsmith.bells.history.LiftDetailScreen
import dev.justinsmith.bells.history.LiftDetailUiState
import dev.justinsmith.bells.library.ExerciseLibraryScreen
import dev.justinsmith.bells.library.LibraryList
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
    fun `Today puts the week beside the program's day`() = runUiTest(Window.Tablet) {
        val goblet = program(ProgramId.GobletSquatRow)
        val state = TodayUiState(following = goblet, programDay = goblet.dayAfter(0))
        setBellsContent { TodayScreen(state, onStartSession = {}, onChooseProgram = {}) }
        val week = onNodeWithText("This week").assertIsDisplayed().getUnclippedBoundsInRoot()
        val day = onNodeWithText("Day 1 of 8 · week 1").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(day.left >= week.right)
        assertTrue(day.top < week.bottom)
    }

    @Test
    fun `Your equipment puts the bar beside the bells`() = runUiTest(Window.Tablet) {
        setBellsContent { GearScreen(GearUiState(), onToggle = {}, onBack = {}) }
        val bells = onNodeWithText("Your bells").assertIsDisplayed().getUnclippedBoundsInRoot()
        val bar = onNodeWithText("Your bar").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(bar.left >= bells.right)
        assertTrue(bar.top < bells.bottom)
    }

    @Test
    fun `the exercise library lays its cards side by side`() = runUiTest(Window.Tablet) {
        setBellsContent {
            ExerciseLibraryScreen(LibraryList(offeredExercises(emptyList())), onFilters = {}, onBack = {})
        }
        val bells = onNodeWithText("Kettlebell · 19").assertIsDisplayed().getUnclippedBoundsInRoot()
        val bars = onNodeWithText("Barbell · 20").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(bars.left >= bells.right)
        assertTrue(bars.top < bells.bottom)
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
    fun `a session on an upright tablet keeps one wider column`() = bellsAppTest(window = Window.TabletPortrait) {
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        waitForText("Reps")
        onAllNodesWithText("Log set").onLast().performClick()
        waitForText("16 kg × 10")
        val time = onNodeWithText("Time").assertIsDisplayed().getUnclippedBoundsInRoot()
        val since = onNodeWithText("Since last set").assertIsDisplayed().getUnclippedBoundsInRoot()
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
        setBellsContent { LiftDetailScreen(state, onDelete = {}, onBack = {}) }
        val moved = onNodeWithText("Moved").assertIsDisplayed().getUnclippedBoundsInRoot()
        val swing = onNodeWithText("24 kg × 10").assertIsDisplayed().getUnclippedBoundsInRoot()
        val delete = onNodeWithText("Delete").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(swing.left > moved.right)
        // Delete is centred under both, not stuck to the first column.
        assertTrue(delete.left > moved.left + 100.dp)
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
