@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.fakes.hiitPreset
import dev.justinsmith.interval.plan.DayChoice
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

private val hiit = hiitPreset

private val workout = FinishedWorkout(
    id = 4,
    presetName = hiit.name,
    config = hiit.config,
    day = HistoryDay.On("Wednesday, Oct 14"),
    time = "7:05 AM",
    total = "9:40",
    work = "5:00",
    calories = 84,
    endedEarly = false,
    planned = true,
)

private val state = WorkoutDetailUiState(
    workout,
    days = (20_741L until 20_755L).map { DayChoice(it, weekdayOf(it), (15 + it - 20_741L).toInt()) },
    todayWeekday = 4,
)

class WorkoutDetailScreenTest {
    private val events = mutableListOf<String>()

    @Composable
    private fun Screen(state: WorkoutDetailUiState) {
        WorkoutDetailScreen(
            state = state,
            onBack = { events += "back" },
            onRepeat = { events += "repeat" },
            onDelete = { events += "delete" },
            onPlanWeekly = { preset, days -> events += "weekly ${preset.name} ${days.sorted()}" },
            onPlanOnce = { preset, day -> events += "once ${preset.name} $day" },
        )
    }

    @Test
    fun `it shows when it was and its figures`() = runUiTest {
        setThemedContent { Screen(state) }
        onNodeWithText("HIIT 30/30").assertExists()
        onNodeWithText("Wednesday, Oct 14 · 7:05 AM").assertExists()
        onNodeWithText("9:40").assertExists()
        onNodeWithText("5:00").assertExists()
        onNodeWithText("84").assertExists()
        onNodeWithText("Planned · done").assertExists()
    }

    @Test
    fun `repeat starts it now`() = runUiTest {
        setThemedContent { Screen(state) }
        onNodeWithText("Repeat now").performClick()
        assertEquals(listOf("repeat"), events)
    }

    @Test
    fun `deleting asks first`() = runUiTest {
        setThemedContent { Screen(state) }
        onNodeWithContentDescription("More options").performClick()
        onNodeWithText("Delete workout").performClick()
        onNodeWithText("Delete workout?").assertExists()
        onNodeWithText("Cancel").performClick()
        assertEquals(emptyList(), events)

        onNodeWithContentDescription("More options").performClick()
        onNodeWithText("Delete workout").performClick()
        onNodeWithText("Delete").performClick()
        assertEquals(listOf("delete"), events)
    }

    @Test
    fun `plan again puts this workout on the weekday picked`() = runUiTest {
        setThemedContent { Screen(state) }
        onNodeWithText("Plan again").performClick()
        onNode(hasText("Add to plan") and hasClickAction()).performClick()
        assertEquals(listOf("weekly HIIT 30/30 [4]"), events)
    }
}
