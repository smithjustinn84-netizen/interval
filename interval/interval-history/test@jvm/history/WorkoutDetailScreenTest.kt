@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.fakes.hiitPreset
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.plan.DayChoice
import dev.justinsmith.interval.platform.HeartRate
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
    private fun Screen(state: WorkoutDetailUiState, inPane: Boolean = false) {
        WorkoutDetailScreen(
            state = state,
            onBack = { events += "back" },
            onRepeat = { events += "repeat" },
            onDelete = { events += "delete" },
            onPlanWeekly = { workout, days ->
                events +=
                    "weekly ${(workout as PlanWorkout.Interval).name} ${days.sorted()}"
            },
            onPlanOnce = { workout, day -> events += "once ${(workout as PlanWorkout.Interval).name} $day" },
            inPane = inPane,
            onShare = { events += "share $it" },
        )
    }

    @Test
    fun `share from the overflow sends the workout in words`() = runUiTest {
        setThemedContent { Screen(state) }
        onNodeWithContentDescription("More options").performClick()
        onNodeWithText("Share workout").performClick()
        assertEquals(listOf("share HIIT 30/30 · Wednesday, Oct 14 · 7:05 AM\n9:40 total · 5:00 work · 84 kcal"), events)
    }

    @Test
    fun `beside the log it closes rather than going back`() = runUiTest {
        setThemedContent { Screen(state, inPane = true) }
        onNodeWithContentDescription("Back").assertDoesNotExist()
        onNodeWithContentDescription("Close workout").performClick()
        assertEquals(listOf("back"), events)
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
    fun `how it went shows only once something was said and its note is shared`() = runUiTest {
        var shown by mutableStateOf(state)
        setThemedContent { Screen(shown) }
        onNodeWithText("How it went").assertDoesNotExist()

        shown = state.copy(workout = workout.copy(effort = PerceivedEffort.VeryHard, note = "Legs heavy"))
        onNodeWithText("How it went").assertExists()
        onNodeWithText("Very hard").assertExists()
        onNodeWithText("Legs heavy").assertExists()
        onNodeWithContentDescription("More options").performClick()
        onNodeWithText("Share workout").performClick()
        assertEquals(
            listOf("share HIIT 30/30 · Wednesday, Oct 14 · 7:05 AM\n9:40 total · 5:00 work · 84 kcal\nLegs heavy"),
            events,
        )
    }

    @Test
    fun `a watch's heart rate shows once it is read`() = runUiTest {
        var shown by mutableStateOf(state)
        setThemedContent { Screen(shown) }
        onNodeWithText("Avg heart rate").assertDoesNotExist()

        shown = state.copy(workout = workout.copy(heartRate = HeartRate(averageBpm = 142, peakBpm = 171)))
        onNodeWithText("Avg heart rate").assertExists()
        onNodeWithText("142 bpm").assertExists()
        onNodeWithText("171 bpm").assertExists()
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
        waitForIdle()
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
