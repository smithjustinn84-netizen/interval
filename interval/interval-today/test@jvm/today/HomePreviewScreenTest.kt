@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.today

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.interval.history.DayCell
import dev.justinsmith.interval.history.DayState
import dev.justinsmith.interval.history.HistoryDay
import dev.justinsmith.interval.history.UpNextItem
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.program
import kotlin.test.Test
import kotlin.test.assertEquals

private const val THURSDAY = 20_741L

private val restWeek = List(7) { i ->
    DayCell(THURSDAY - 3 + i, weekday = i + 1, dayOfMonth = 12 + i, DayState.Rest, seconds = 0, today = i == 3)
}

private val empty = HomeUiState(week = restWeek, picks = builtInPresets.fitting(10, IntervalConfig()))

/** Today's look before Start: Up next's and a pick's, the program's day among the picks, and Plan for later. */
class HomePreviewScreenTest {
    @Test
    fun `up next opens a look at the program's day with its lifts while its start starts at once`() = runUiTest {
        val started = mutableListOf<ProgramKey?>()
        val day = program(ProgramId.PressLadders).day(3)
        val state = empty.copy(upNext = listOf(UpNextItem.Lift(THURSDAY, HistoryDay.Today, day, minutes = 35)))
        setThemedContent { HomeScreen(state, HomeActions(onStartLift = { started += it })) }

        onNodeWithText("Press ladders 1-2-3").performClick()
        onNodeWithText("Strength · about 35 min").assertExists()
        onNodeWithText("Plan for later").assertExists()
        onAllNodesWithText("Start day 3")[1].performClick()
        waitUntil { started.isNotEmpty() }
        assertEquals(listOf<ProgramKey?>(day.program.key), started)
    }

    @Test
    fun `the program's day is the first pick and opens a look at it`() = runUiTest {
        val started = mutableListOf<ProgramKey?>()
        val day = program(ProgramId.PressLadders).day(3)
        val state = empty.copy(strength = StrengthPick(day, 20))
        setThemedContent { HomeScreen(state, HomeActions(onStartLift = { started += it })) }

        // The chip of 20 minutes, and the day's own length.
        onAllNodesWithText("20 min").assertCountEquals(2)
        onNodeWithText("Day 3 of", substring = true).assertExists()
        onAllNodesWithText("Press ladders 1-2-3").onFirst().performScrollTo().performClick()
        onNodeWithText("Strength · about 20 min").assertExists()
        onNodeWithText("Start day 3").performClick()
        waitUntil { started.isNotEmpty() }
        assertEquals(listOf<ProgramKey?>(day.program.key), started)
    }

    @Test
    fun `a pick planned for later goes on the add sheet with it alone`() = runUiTest {
        val planned = mutableListOf<String>()
        setThemedContent {
            HomeScreen(
                empty,
                HomeActions(onPlanOnce = { workout, _ -> planned += (workout as PlanWorkout.Interval).name }),
            )
        }
        onNodeWithText("HIIT 30/30").performScrollTo().performClick()
        onNodeWithText("Plan for later").performClick()
        onNodeWithText("Once").performClick()
        onAllNodesWithText("Add to plan").onLast().performClick()
        waitUntil { planned.isNotEmpty() }
        assertEquals(listOf("HIIT 30/30"), planned)
    }
}
