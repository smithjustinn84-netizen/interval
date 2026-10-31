@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.kettlebellLifts
import dev.justinsmith.lift.domain.program
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Lift tab over fixed state. What it does over the real ViewModel is in lift-nav's `TodayFlowTest`. */
class TodayScreenTest {
    @Test
    fun `without a program Today offers one and a free session`() = runUiTest {
        val started = mutableListOf<ProgramKey?>()
        var choosing = false
        setThemedContent(dark = true) {
            TodayScreen(TodayUiState(), onStartSession = { started += it }, onChooseProgram = {
                choosing =
                    true
            })
        }
        onNodeWithText("No sessions yet").assertExists()
        onNodeWithText("Free session").assertDoesNotExist()
        onNodeWithText("Follow a program").performClick()
        assertEquals(true, choosing)
        onNodeWithText("Start session").performClick()
        assertEquals(listOf<ProgramKey?>(null), started)
    }

    @Test
    fun `with a program Today lays out its next day`() = runUiTest {
        val started = mutableListOf<ProgramKey?>()
        val state = TodayUiState(
            following = program(ProgramId.GobletSquatRow),
            programDay = program(ProgramId.GobletSquatRow).dayAfter(0),
        )
        setThemedContent(dark = true) {
            TodayScreen(state, onStartSession = { started += it }, onChooseProgram = {})
        }
        onNodeWithText("Goblet squat and row").assertExists()
        onNodeWithText("Day 1 of 8 · week 1").assertExists()
        onNodeWithText("Goblet squat").assertExists()
        onNodeWithText("Start session").assertDoesNotExist()
        onNodeWithText("Start day 1").performClick()
        onNodeWithText("Free session").performClick()
        assertEquals(listOf<ProgramKey?>(ProgramKey.BuiltIn(ProgramId.GobletSquatRow), null), started)
    }

    @Test
    fun `with a session open Today offers the day beside Resume and asks only when it has sets`() = runUiTest {
        val started = mutableListOf<ProgramKey?>()
        var state by mutableStateOf(
            TodayUiState(
                inProgress = true,
                following = program(ProgramId.GobletSquatRow),
                programDay = program(ProgramId.GobletSquatRow).dayAfter(0),
            ),
        )
        setThemedContent(dark = true) {
            TodayScreen(state, onStartSession = { started += it }, onChooseProgram = {})
        }
        onNodeWithText("Resume session").assertExists()
        onNodeWithText("Free session").assertDoesNotExist()
        // Nothing logged: the day starts without a word.
        onNodeWithText("Start day 1").performClick()
        onNodeWithText("Leave the session?").assertDoesNotExist()
        assertEquals(listOf<ProgramKey?>(ProgramKey.BuiltIn(ProgramId.GobletSquatRow)), started)
        state = state.copy(openHasSets = true)
        onNodeWithText("Start day 1").performClick()
        onNodeWithText("Leave the session?").assertExists()
        onNodeWithText("Its sets are not saved: the day starts fresh.").assertExists()
        onNodeWithText("Cancel").performClick()
        waitUntil { onAllNodesWithText("Leave the session?").fetchSemanticsNodes().isEmpty() }
        assertEquals(1, started.size)
        onNodeWithText("Start day 1").performClick()
        onAllNodesWithText("Start day 1").assertCountEquals(2)
        onAllNodesWithText("Start day 1").onLast().performClick()
        // The dialog's action runs once its exit has played.
        waitUntil { started.size == 2 }
        assertEquals(ProgramKey.BuiltIn(ProgramId.GobletSquatRow), started.last())
        onNodeWithText("Resume session").performClick()
        assertEquals(null, started.last())
    }

    @Test
    fun `the weekly goal shows what is left of it this week`() = runUiTest {
        val week = (0L until 7L).map {
            WeekDay(it, it.toInt() + 1, bells = 0, interval = 0, today = it == 0L, date = "day $it")
        }
        var state by mutableStateOf(TodayUiState(week = week))
        setThemedContent(dark = true) { TodayScreen(state, onStartSession = {}, onChooseProgram = {}) }
        onNodeWithText("Goal met this week").assertDoesNotExist()
        state = state.copy(
            week = week.mapIndexed { i, day -> if (i == 0) day.copy(bells = 1, interval = 1) else day },
            weeklyGoal = 3,
        )
        onNodeWithText("1 more to hit 3").assertExists()
        state = state.copy(weeklyGoal = 2)
        onNodeWithText("Goal met this week").assertExists()
    }

    @Test
    fun `an empty week shows its count alone and no legend`() = runUiTest {
        val week = (0L until 7L).map {
            WeekDay(it, it.toInt() + 1, bells = 0, interval = 0, today = it == 0L, date = "day $it")
        }
        var state by mutableStateOf(TodayUiState(week = week))
        setThemedContent(dark = true) { TodayScreen(state, onStartSession = {}, onChooseProgram = {}) }
        onNodeWithText("0 workouts").assertExists()
        onNodeWithText("Lifts").assertDoesNotExist()
        state = state.copy(week = week.mapIndexed { i, day -> if (i == 0) day.copy(interval = 1) else day })
        onNodeWithText("1 workout · 1 on the Timer").assertExists()
        onNodeWithText("Lifts").assertExists()
    }

    @Test
    fun `the last session opens on a tap`() = runUiTest {
        val opened = mutableListOf<String>()
        val last =
            LiftSession("s1", 0, 600_000, "Swings", listOf(SetLog(1, BuiltInExercise.TwoHandSwing, 24.0, 10, null, 0)))
        setThemedContent(dark = true) {
            TodayScreen(
                TodayUiState(last = last),
                onStartSession = {},
                onChooseProgram = {},
                onOpenSession = { opened += it },
            )
        }
        onNodeWithText("Swings").performClick()
        assertEquals(listOf("s1"), opened)
    }

    @Test
    fun `Start stays on screen under a long program day`() = runUiTest {
        val long = Program(
            ProgramKey.Custom("long"),
            days = listOf(
                List(12) {
                    Block(kettlebellLifts[it % kettlebellLifts.size], rounds = 5, reps = 5)
                },
            ),
            daysPerWeek = 3,
            weeks = 4,
            name = "Long day",
        )
        setThemedContent(dark = true) {
            TodayScreen(
                TodayUiState(following = long, programDay = long.dayAfter(0)),
                onStartSession = {},
                onChooseProgram = {},
            )
        }
        onNodeWithText("Start day 1").assertIsDisplayed()
        onNodeWithText("Change program").assertIsNotDisplayed()
    }

    @Test
    fun `a finished program's card says so and opens the program`() = runUiTest {
        var opened = 0
        val state = TodayUiState(following = program(ProgramId.GobletSquatRow), programDay = null)
        setThemedContent(dark = true) {
            TodayScreen(state, onStartSession = {}, onChooseProgram = {}, onOpenProgram = { opened++ })
        }
        onNodeWithText("Finished").assertIsDisplayed()
        onNodeWithText("Finished · pick another program").assertDoesNotExist()
        onNodeWithText("Finished").performClick()
        assertEquals(1, opened)
    }
}
