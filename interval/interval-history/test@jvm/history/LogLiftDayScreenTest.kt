@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.plan.PlanChoice
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.program
import kotlin.test.Test
import kotlin.test.assertEquals

/** The followed program's next day logged done as planned from the Log: the log sheet's Strength side, a plan's row. */
class LogLiftDayScreenTest {
    private val events = mutableListOf<String>()
    private val ladders = program(ProgramId.PressLadders).day(1)
    private val monday =
        DaySheet(20_738, HistoryDay.On("Monday, Oct 12"), weekday = 1, emptyList(), emptyList(), past = true)

    /** Monday's sheet over the log, the program's day loggable [from] an epoch day, closing as the screen asks. */
    private fun logging(from: Long): Pair<() -> HistoryUiState, HistoryActions> {
        var state by mutableStateOf(
            HistoryUiState(
                sections = emptyList(),
                daySheet = monday,
                choices = builtInPresets.map { PlanChoice(it) },
                liftLog = LiftLog(ladders, from = from, today = 20_741),
            ),
        )
        val closing = HistoryActions(
            onOpenDay = { if (it == null) state = state.copy(daySheet = null) },
            onLogLift = { title, day, text -> events += "logLift $title $day: $text" },
        )
        return ({ state }) to closing
    }

    @Test
    fun `the log sheet leads with the program's next day and logs it as planned`() = runUiTest {
        val (state, closing) = logging(from = 20_730)
        setThemedContent { HistoryScreen(state(), closing) }

        onNodeWithText("Log a workout").performClick()
        onNode(hasText("Strength") and isSelectable()).assertIsSelected()
        onNodeWithText("Press ladders 1-2-3").assertExists()
        onNodeWithText("Day 1 of", substring = true).assertExists()
        onNode(hasText("Log it") and hasClickAction()).performClick()
        assertEquals(listOf("logLift Press and Two-hand swing 20738: Press ladders 1-2-3 logged"), events)
    }

    @Test
    fun `the log sheet offers no program day on a day it cannot be logged`() = runUiTest {
        val (state, closing) = logging(from = 20_740)
        setThemedContent { HistoryScreen(state(), closing) }

        onNodeWithText("Log a workout").performClick()
        onNode(hasText("Strength") and isSelectable()).assertDoesNotExist()
        onNode(hasText("Interval") and isSelectable()).assertIsSelected()
    }

    @Test
    fun `a planned program day is logged as planned from its row`() = runUiTest {
        val plan = SheetPlan(1, PlanWorkout.Lift.ProgramDay, weekly = false, state = PlanState.Missed)
        val sheet = monday.copy(plans = listOf(plan), liftDay = ladders)
        var state by mutableStateOf(HistoryUiState(sections = emptyList(), daySheet = sheet))
        val actions = HistoryActions(onLogLift = { title, day, text -> events += "logLift $title $day: $text" })
        setThemedContent { HistoryScreen(state, actions) }

        onNodeWithText("Log it").performClick()
        assertEquals(listOf("logLift Press and Two-hand swing 20738: Press ladders 1-2-3 logged"), events)
        state = state.copy(daySheet = sheet.copy(liftDay = null))
        onNodeWithText("Log it").assertDoesNotExist()
    }
}
