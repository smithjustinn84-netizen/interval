@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.history.HistoryDay
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

// Thursday 15 October 2026, with nothing on the plan.

private val empty = PlanUiState(
    goal = 4,
    streak = 0,
    routine = (1..7).map { RoutineDay(it, emptyList()) },
    oneOffs = emptyList(),
    choices = builtInPresets.map { PlanChoice(it) },
    days = (TODAY until TODAY + 14).map { DayChoice(it, weekdayOf(it), (15 + it - TODAY).toInt()) },
    todayWeekday = 4,
    today = TODAY,
    reminder = ReminderUi(on = false, minuteOfDay = 7 * 60, text = "7:00", picks = emptyList()),
)

/** The sheet's confirming button: the sheet's title says "Add to plan" too, so take the clickable one. */
private fun SemanticsNodeInteractionsProvider.clickAdd() {
    onNode(hasText("Add to plan") and hasClickAction()).performClick()
}

/** Plan's sport days: the add sheet's Sport side, and how the routine shows what it planned. */
class PlanSportScreenTest {
    private val events = mutableListOf<String>()

    @Composable
    private fun Screen(state: PlanUiState) {
        PlanScreen(
            state = state,
            onBack = {},
            onStepGoal = {},
            onPlanWeekly = { workout, days -> events += "weekly $workout ${days.sorted()}" },
            onPlanOnce = { workout, day -> events += "once $workout $day" },
            onPlanRoutine = {},
            onRemove = { id, _ -> events += "remove $id" },
            onSetReminder = {},
        )
    }

    @Test
    fun `a weekday's plus plans a day of a sport the sheet names`() = runUiTest {
        setThemedContent { Screen(empty) }
        onNodeWithContentDescription("Add a workout every Wednesday").performClick()
        onNode(hasText("Sport") and isSelectable()).performClick()
        onNodeWithText("Trained away from the app. Log it on its day and it counts toward your week.").assertExists()
        onNode(hasText("Climbing") and isSelectable()).performClick()
        onNode(hasContentDescription("Saturday") and isToggleable()).performClick()
        clickAdd()
        assertEquals(listOf("weekly SportDay(sport=Climbing) [3, 6]"), events)
    }

    @Test
    fun `a sport of the user's own is planned once it has a name`() = runUiTest {
        setThemedContent { Screen(empty) }
        onNodeWithContentDescription("Add a workout every Friday").performClick()
        onNode(hasText("Sport") and isSelectable()).performClick()
        onNode(hasText("Something else") and isSelectable()).performClick()
        // Nothing to plan until it is named.
        onNode(hasText("Add to plan") and hasClickAction()).assertIsNotEnabled()

        onNode(hasSetTextAction()).performTextInput(" Judo ")
        clickAdd()
        assertEquals(listOf("weekly SportDay(sport=Custom(name=Judo)) [5]"), events)
    }

    @Test
    fun `sport days sit on the routine and in its count`() = runUiTest {
        val classes = empty.copy(
            routine = empty.routine.map { day ->
                when (day.weekday) {
                    2 -> day.copy(plans = listOf(PlanEntry(5, PlanWorkout.SportDay(Sport.Bjj))))
                    6 -> day.copy(plans = listOf(PlanEntry(6, PlanWorkout.SportDay(Sport.Custom("Judo")))))
                    else -> day
                }
            },
            oneOffs = listOf(
                OneOff(PlanEntry(7, PlanWorkout.SportDay(Sport.Climbing)), TODAY + 2, HistoryDay.On("Sat")),
            ),
            routineIntervals = 1,
            routineSports = 2,
        )
        setThemedContent { Screen(classes) }

        onNodeWithText("Every week · 1 interval · 2 sport days").assertExists()
        onNodeWithContentDescription("Remove BJJ class from Tuesday").assertExists()
        onNodeWithContentDescription("Remove Judo from Saturday").performClick()
        onNodeWithContentDescription("Remove Climbing, Sat").performClick()
        assertEquals(listOf("remove 6", "remove 7"), events)
    }
}
