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
import dev.justinsmith.interval.fakes.hiitPreset
import dev.justinsmith.interval.fakes.tabataPreset
import dev.justinsmith.interval.history.HistoryDay
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val tabata = tabataPreset
private val hiit = hiitPreset

// Thursday 15 October 2026.

private val state = PlanUiState(
    goal = 4,
    streak = 5,
    routine = (1..7).map { weekday ->
        RoutineDay(weekday, if (weekday == 1) listOf(PlanEntry(1, tabata.name, tabata.config)) else emptyList())
    },
    oneOffs = listOf(OneOff(PlanEntry(2, hiit.name, hiit.config), TODAY + 1, HistoryDay.Tomorrow)),
    choices = listOf(PlanChoice(Preset("40/20 ×6", tabata.config.copy(workSeconds = 40)), onTimer = true)) +
        builtInPresets.map { PlanChoice(it) },
    days = (TODAY until TODAY + 14).map { DayChoice(it, weekdayOf(it), (15 + it - TODAY).toInt()) },
    todayWeekday = 4,
    today = TODAY,
)

/** The sheet's confirming button: the sheet's title says "Add to plan" too, so take the clickable one. */
private fun SemanticsNodeInteractionsProvider.clickAdd() {
    onNode(hasText("Add to plan") and hasClickAction()).performClick()
}

class PlanScreenTest {
    private val events = mutableListOf<String>()

    @Composable
    private fun Screen(state: PlanUiState) {
        PlanScreen(
            state = state,
            onBack = { events += "back" },
            onStepGoal = { events += "goal $it" },
            onPlanWeekly = { preset, days -> events += "weekly ${preset.name} ${days.sorted()}" },
            onPlanOnce = { preset, day -> events += "once ${preset.name} $day" },
            onPlanRoutine = { routine ->
                routine.plans.forEach { events += "described ${it.workout.name} ${it.weekdays.sorted()} ${it.days}" }
                events += "goal ${routine.goal}"
            },
            onRemove = { id, _ -> events += "remove $id" },
        )
    }

    @Test
    fun `on a tablet the routine sits beside the goal and the one-offs`() = runUiTest(Window.TabletPortrait) {
        setThemedContent { Screen(state) }
        val goal = onNodeWithText("Workouts a week · 5-week streak").fetchSemanticsNode().boundsInRoot
        val routine = onNodeWithText("Routine").fetchSemanticsNode().boundsInRoot
        val oneOffs = onNodeWithText("One-offs").fetchSemanticsNode().boundsInRoot
        assertTrue(routine.left > goal.right, "Routine beside the goal: $goal, $routine")
        assertTrue(oneOffs.top > goal.bottom && oneOffs.right < routine.left, "One-offs under the goal: $oneOffs")
    }

    @Test
    fun `the goal steps either way and shows the streak`() = runUiTest {
        setThemedContent { Screen(state) }
        onNodeWithText("Workouts a week · 5-week streak").assertExists()
        onNodeWithContentDescription("Raise the goal").performClick()
        onNodeWithContentDescription("Lower the goal").performClick()
        assertEquals(listOf("goal 1", "goal -1"), events)
    }

    @Test
    fun `with no goal it reads off and cannot go lower`() = runUiTest {
        setThemedContent { Screen(state.copy(goal = null, streak = 0)) }
        onNodeWithText("Off").assertExists()
        onNodeWithContentDescription("Lower the goal").assertIsNotEnabled()
    }

    @Test
    fun `routine and one-off workouts come off with their own buttons`() = runUiTest {
        setThemedContent { Screen(state) }
        onNodeWithContentDescription("Remove Tabata from Monday").performClick()
        onNodeWithContentDescription("Remove HIIT 30/30, Tomorrow").performClick()
        assertEquals(listOf("remove 1", "remove 2"), events)
    }

    @Test
    fun `a weekday's plus plans a preset on that weekday and any others picked`() = runUiTest {
        setThemedContent { Screen(state) }
        onNodeWithContentDescription("Add a workout every Wednesday").performClick()
        // The routine's chip says Tabata too: take the sheet's choice.
        onNode(hasText("Tabata") and isSelectable()).performClick()
        // The routine's Friday row is read "Friday" too: take the sheet's toggle.
        onNode(hasContentDescription("Friday") and isToggleable()).performClick()
        clickAdd()
        assertEquals(listOf("weekly Tabata [3, 5]"), events)
    }

    @Test
    fun `adding on a date plans one day`() = runUiTest {
        setThemedContent { Screen(state) }
        onNodeWithText("Add on a date").performClick()
        onNodeWithText("On the Timer").performClick()
        onNodeWithContentDescription("Saturday · 17").performClick()
        clickAdd()
        assertEquals(listOf("once 40/20 ×6 ${TODAY + 2}"), events)
    }

    @Test
    fun `a routine typed in words previews its days and goes on the plan`() = runUiTest {
        setThemedContent { Screen(state) }
        onNodeWithContentDescription("Describe a routine").performClick()
        onNode(hasSetTextAction()).performTextInput("tabata mon wed fri, hiit 30/30 next sat, 4 a week")
        onNodeWithText("Mon · Wed · Fri", substring = true).assertExists()
        onNodeWithText("Sat · 17", substring = true).assertExists()
        onNodeWithText("Goal 4 a week").assertExists()
        clickAdd()
        assertEquals(
            listOf("described Tabata [1, 3, 5] []", "described HIIT 30/30 [] [${TODAY + 2}]", "goal 4"),
            events,
        )
    }

    @Test
    fun `a workout with no day says so and cannot be added`() = runUiTest {
        setThemedContent { Screen(state) }
        onNodeWithContentDescription("Describe a routine").performClick()
        onNode(hasSetTextAction()).performTextInput("tabata")
        onNodeWithText("No day, so it stays off the plan", substring = true).assertExists()
        onNode(hasText("Add to plan") and hasClickAction()).assertDoesNotExist()
    }
}
