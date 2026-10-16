@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

private val tabata = builtInPresets.first().config

/** Monday to Sunday, Sunday being today, with [seconds] on each day. */
private fun week(vararg seconds: Int) = seconds.mapIndexed { i, s ->
    DayTally(
        weekday = i + 1,
        seconds = s,
        today =
        i == 6,
    )
}

private val week = week(0, 0, 0, 0, 0, 0, 355)

private val oneWorkout = HistoryUiState(
    sections = listOf(
        HistorySection(
            HistoryDay.Today,
            dayNumber = 9,
            listOf(HistoryItem("Tabata", tabata, false, "7:42 PM", "4:00")),
        ),
    ),
    weekCount = 1,
    weekTotal = "5:55",
    week = week,
)

private val empty = HistoryUiState(sections = emptyList(), weekCount = 0, weekTotal = "0:00", week = week)

class HistoryScreenTest {
    private var cleared = 0

    @Composable
    private fun Screen(state: HistoryUiState) {
        HistoryScreen(state = state, onClear = { cleared++ })
    }

    @Test
    fun `finished workouts show under their day`() = runUiTest {
        setThemedContent { Screen(oneWorkout) }
        onNodeWithText("Today").assertExists()
        onNodeWithText("Tabata").assertExists()
        onNodeWithText("Last 7 days").assertExists()
        onNodeWithContentDescription("Time per day this week: Today 6 minutes").assertExists()
    }

    @Test
    fun `a row names its preset or leads with its shape, and says when it ended early`() = runUiTest {
        val custom = IntervalConfig(workSeconds = 25, restSeconds = 5, rounds = 4)
        val state = oneWorkout.copy(
            sections = listOf(
                HistorySection(
                    HistoryDay.Yesterday,
                    dayNumber = 8,
                    listOf(
                        HistoryItem("Tabata", tabata, endedEarly = true, "7:42 PM", "1:30"),
                        HistoryItem(null, custom, endedEarly = false, "6:15 PM", "1:40"),
                    ),
                ),
                HistorySection(HistoryDay.On("Sunday, Oct 11"), dayNumber = 1, emptyList()),
            ),
        )
        setThemedContent { Screen(state) }
        onNodeWithText("Yesterday").assertExists()
        onNodeWithText("Sunday, Oct 11").assertExists()
        onNodeWithText("20s on · 10s off · ×8 · ended early · 7:42 PM").assertExists()
        onNodeWithText("25s on · 5s off · ×4").assertExists()
        onNodeWithText("6:15 PM").assertExists()
    }

    @Test
    fun `the week chart reads each day's time in seconds or minutes`() = runUiTest {
        var state by mutableStateOf(oneWorkout.copy(week = week(0, 0, 0, 240, 0, 0, 90)))
        setThemedContent { Screen(state) }
        // 90 seconds rounds to 2 minutes.
        onNodeWithContentDescription("Time per day this week: Thursday 4 minutes, Today 2 minutes").assertExists()
        state = oneWorkout.copy(week = week(0, 45, 0, 0, 0, 0, 1))
        onNodeWithContentDescription("Time per day this week: Tuesday 45 seconds, Today 1 second").assertExists()
        state = oneWorkout.copy(week = week(0, 0, 0, 0, 0, 0, 0))
        onNodeWithContentDescription("No workouts in the last 7 days").assertExists()
    }

    @Test
    fun `the week's count names its unit`() = runUiTest {
        var state by mutableStateOf(oneWorkout)
        setThemedContent { Screen(state) }
        onNodeWithText("1 workout").assertExists()
        state = oneWorkout.copy(weekCount = 3)
        onNodeWithText("3 workouts").assertExists()
    }

    @Test
    fun `an empty history says so and has nothing to clear`() = runUiTest {
        setThemedContent { Screen(empty) }
        onNodeWithText("No workouts yet").assertExists()
        onNodeWithText("Finished workouts show up here.").assertExists()
        onNodeWithContentDescription("More options").assertIsNotEnabled()
    }

    @Test
    fun `clearing asks first`() = runUiTest {
        setThemedContent { Screen(oneWorkout) }
        onNodeWithContentDescription("More options").performClick()
        onNodeWithText("Clear history").performClick()
        onNodeWithText("Clear history?").assertExists()
        onNodeWithText("Every finished workout is removed. This can't be undone.").assertExists()
        onNodeWithText("Cancel").performClick()
        assertEquals(0, cleared)

        onNodeWithContentDescription("More options").performClick()
        onNodeWithText("Clear history").performClick()
        onNodeWithText("Clear").performClick()
        assertEquals(1, cleared)
        onNodeWithText("Clear history?").assertDoesNotExist()
    }
}
