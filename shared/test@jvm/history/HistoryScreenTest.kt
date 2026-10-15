@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

private val week = listOf("M", "T", "W", "T", "F", "S", "S").mapIndexed { i, label ->
    DayTally(label, if (i == 6) "Today" else "Day $i", seconds = if (i == 6) 355 else 0, today = i == 6)
}

private val oneWorkout = HistoryUiState(
    sections = listOf(
        HistorySection("Today", listOf(HistoryItem("Tabata", "20s on · 10s off · ×8 · 7:42 PM", total = "4:00"))),
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
    fun `an empty history says so and has nothing to clear`() = runUiTest {
        setThemedContent { Screen(empty) }
        onNodeWithText("Finished workouts show up here.").assertExists()
        onNodeWithContentDescription("More options").assertIsNotEnabled()
    }

    @Test
    fun `clearing asks first`() = runUiTest {
        setThemedContent { Screen(oneWorkout) }
        onNodeWithContentDescription("More options").performClick()
        onNodeWithText("Clear history").performClick()
        onNodeWithText("Clear history?").assertExists()
        onNodeWithText("Cancel").performClick()
        assertEquals(0, cleared)

        onNodeWithContentDescription("More options").performClick()
        onNodeWithText("Clear history").performClick()
        onNodeWithText("Clear").performClick()
        assertEquals(1, cleared)
        onNodeWithText("Clear history?").assertDoesNotExist()
    }
}
