@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.interval.history.DayCell
import dev.justinsmith.interval.history.DayState
import dev.justinsmith.interval.history.HistoryDay
import dev.justinsmith.interval.history.UpNextItem
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.flowBefore
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

private const val THURSDAY = 20_741L

private val empty = HomeUiState(
    week = List(7) { i ->
        DayCell(THURSDAY - 3 + i, weekday = i + 1, dayOfMonth = 12 + i, DayState.Rest, seconds = 0, today = i == 3)
    },
    picks = builtInPresets.fitting(10, IntervalConfig()),
)

/** Today's part in mobility: the flow before a day's sport, and the flow among the picks. */
class HomeFlowScreenTest {
    @Test
    fun `before a class its flow leads with the class to log beside it`() = runUiTest {
        val started = mutableListOf<String>()
        val logged = mutableListOf<String>()
        val flow = flowBefore(Sport.Bjj)
        val state = empty.copy(
            upNext = listOf(
                UpNextItem.Flow(THURSDAY, HistoryDay.Today, flow, Sport.Bjj),
                UpNextItem.SportDay(1, THURSDAY, HistoryDay.Today, Sport.Bjj),
            ),
            hasPlans = true,
        )
        val actions = HomeActions(
            onStart = { name, config -> started += "$name ${config == flow.config}" },
            onLogSport = { sport, _ -> logged += "$sport" },
        )
        setThemedContent { HomeScreen(state, actions) }

        onNodeWithText("Before class").assertIsDisplayed()
        onNodeWithText("Up next · Today").assertDoesNotExist()
        onNodeWithText("Hips and shoulders").assertIsDisplayed()
        onNodeWithText("Mobility · 6 moves · no bell · 8:00").assertIsDisplayed()
        onNodeWithText("Also today").assertIsDisplayed()
        onNodeWithText("BJJ class").assertIsDisplayed()
        onNodeWithText("Start workout").performClick()
        onNodeWithText("Log it").performClick()
        assertEquals(listOf("Hips and shoulders true"), started)
        assertEquals(listOf("Bjj"), logged)
    }

    @Test
    fun `what a flow comes before is said by the sport`() = runUiTest {
        var sport by mutableStateOf<Sport>(Sport.Climbing)
        setThemedContent {
            // Suggested only beside the sport it comes before, which is what makes it lead.
            val upNext = listOf(
                UpNextItem.Flow(THURSDAY, HistoryDay.Today, flowBefore(sport), sport, totalSeconds = 320),
                UpNextItem.SportDay(1, THURSDAY, HistoryDay.Today, sport),
            )
            HomeScreen(empty.copy(upNext = upNext, hasPlans = true), HomeActions())
        }
        onNodeWithText("Before climbing").assertIsDisplayed()
        onNodeWithText("Mobility · 5 moves · before the wall · 5:20").assertIsDisplayed()
        sport = Sport.Running
        waitForIdle()
        onNodeWithText("Before your run").assertIsDisplayed()
        onNodeWithText("Mobility · 6 moves · no bell · 5:20").assertIsDisplayed()
        sport = Sport.Custom("Judo")
        waitForIdle()
        onNodeWithText("Before Judo").assertIsDisplayed()
    }

    @Test
    fun `a flow that fits is a pick whose look shows its moves and starts it`() = runUiTest {
        val started = mutableListOf<String>()
        val picks = builtInPresets.fitting(5, IntervalConfig())
        setThemedContent {
            HomeScreen(empty.copy(minutes = 5, picks = picks), HomeActions(onStartPick = { started += it.name }))
        }

        onNodeWithText("5 moves").assertExists()
        onNodeWithText("Wrists and fingers").performScrollTo().performClick()
        onNodeWithText("Mobility · 5 moves · 5:00").assertExists()
        onNodeWithText("Wrist circles").assertExists()
        onNodeWithText("Start").performClick()
        waitUntil { started.isNotEmpty() }
        assertEquals(listOf("Wrists and fingers"), started)
    }
}
