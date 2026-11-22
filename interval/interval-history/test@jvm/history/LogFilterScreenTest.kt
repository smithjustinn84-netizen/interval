@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.interval.fakes.tabataPreset
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

class LogFilterScreenTest {
    @Test
    fun `all time shows search and filter chips and empty filter state with clear`() = runUiTest {
        val events = mutableListOf<String>()
        val actions = HistoryActions(
            onFilter = { events += "filter $it" },
            onQuery = { events += "query $it" },
            onClearFilters = { events += "clear" },
        )
        var state by mutableStateOf(
            HistoryUiState(
                sections = listOf(
                    HistorySection(
                        HistoryDay.Today,
                        dayNumber = 20_741,
                        listOf(HistoryItem("Tabata", tabataPreset.config, false, "7:42 PM", "4:00", id = 7)),
                    ),
                ),
                range = TrainingRange.AllTime,
                summary = RangeSummary(1, 240, null, 0, 0),
                hasHistory = true,
            ),
        )
        setThemedContent { HistoryScreen(state, actions) }

        onNodeWithText("Search log").assertExists()
        onNodeWithText("Strength").performClick()
        assertEquals(listOf("filter Strength"), events)

        onNodeWithText("PRs").performClick()
        assertEquals(listOf("filter Strength", "filter Prs"), events)

        onNode(hasSetTextAction()).performTextInput("tabata")
        waitForIdle()
        assertEquals("query tabata", events.last())

        // Filtered empty state shows the empty filter message and Clear button, not FirstRun.
        state = state.copy(sections = emptyList(), hasHistory = true, filter = LogFilter.Prs)
        onNodeWithText("Nothing matches that filter.").assertExists()
        onNodeWithText("No workouts yet").assertDoesNotExist()
        onAllNodesWithText("Clear").onLast().performClick()
        assertEquals("clear", events.last())
    }
}
