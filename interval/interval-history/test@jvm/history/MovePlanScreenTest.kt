@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.history

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.fakes.tabataPreset
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.plan.MoveDay
import dev.justinsmith.interval.plan.MoveSheet
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

/** Moving a plan from the Log: the day sheet's Move and Moved rows, and the move sheet. */
class MovePlanScreenTest {
    private val events = mutableListOf<String>()
    private val actions = HistoryActions(
        onOpenMove = { id, day -> events += "openMove $id $day" },
        onMovePlanned = { day, text -> events += "move $day: $text" },
    )

    @Test
    fun `a missed plan offers Move and one moved off says where it went with nothing to do`() = runUiTest {
        val tabata = PlanWorkout.Interval("Tabata", tabataPreset.config)
        val missed = SheetPlan(1, tabata, weekly = false, state = PlanState.Missed)
        val moved = SheetPlan(2, PlanWorkout.Lift.ProgramDay, weekly = false, PlanState.Moved, movedTo = 20_741)
        val monday = DaySheet(20_738, HistoryDay.On("Monday, Oct 12"), 1, listOf(missed, moved), emptyList(), true)
        setThemedContent { HistoryScreen(HistoryUiState(sections = emptyList(), daySheet = monday), actions) }

        onNodeWithText("Moved to Thursday").assertExists()
        onAllNodesWithText("Move").assertCountEquals(1)
        onAllNodesWithText("Log it").assertCountEquals(1)
        onNodeWithText("Move").performClick()
        assertEquals(listOf("openMove 1 20738"), events)
    }

    @Test
    fun `the move sheet says what each day holds and moves to the day picked`() = runUiTest {
        val tabata = PlannedWorkout(1, PlanWorkout.Interval("Tabata", tabataPreset.config), PlanSchedule.Once(20_738))
        val sheet = MoveSheet(
            tabata,
            fromDay = 20_738,
            days = listOf(
                MoveDay(20_741, 4, emptyList(), null),
                MoveDay(20_742, 5, listOf(PlanWorkout.Lift.ProgramDay), null),
                MoveDay(20_743, 6, emptyList(), Sport.Bjj),
            ),
            suggested = 20_741,
        )
        setThemedContent {
            HistoryScreen(HistoryUiState(sections = emptyList(), today = 20_741, moveSheet = sheet), actions)
        }

        onNodeWithText("Move Tabata").assertExists()
        onNode(hasText("Today", substring = true) and isSelectable()).assertIsSelected()
        onNodeWithText("Nothing planned").assertExists()
        onNodeWithText("BJJ class").assertExists()
        onNodeWithText("Move to Thursday").assertExists()
        onNodeWithText("Saturday").performClick()
        onNodeWithText("Move to Saturday").performClick()
        assertEquals(listOf("move 20743: Moved Tabata to Saturday"), events)
    }
}
