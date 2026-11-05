@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.today

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.fakes.hiitPreset
import dev.justinsmith.interval.fakes.tabataPreset
import dev.justinsmith.interval.history.DayCell
import dev.justinsmith.interval.history.DayState
import dev.justinsmith.interval.history.HistoryDay
import dev.justinsmith.interval.history.UpNextItem
import dev.justinsmith.interval.history.WeekGoal
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.program
import kotlin.test.Test
import kotlin.test.assertEquals

private const val THURSDAY = 20_741L

/** Monday to Sunday with nothing done or planned, Thursday being today. */
private val restWeek = List(7) { i ->
    DayCell(THURSDAY - 3 + i, weekday = i + 1, dayOfMonth = 12 + i, DayState.Rest, seconds = 0, today = i == 3)
}

private val empty = HomeUiState(week = restWeek, picks = builtInPresets.fitting(10, IntervalConfig()))

private fun option(text: String): SemanticsMatcher = hasText(text) and isSelectable()

class HomeScreenTest {
    @Test
    fun `nothing planned leads to the plan and to the programs`() = runUiTest {
        val calls = mutableListOf<String>()
        setThemedContent {
            HomeScreen(empty, HomeActions(onPlan = { calls += "plan" }, onPrograms = { calls += "programs" }))
        }

        onNode(hasText("Today") and isHeading()).assertIsDisplayed()
        onNodeWithText("Nothing planned").assertIsDisplayed()
        onNodeWithText("Plan your week").performClick()
        onNodeWithText("Follow a program").performClick()
        assertEquals(listOf("plan", "programs"), calls)
    }

    @Test
    fun `with the plan done it says so and leads to the plan rather than offering a first one`() = runUiTest {
        var plans = 0
        setThemedContent { HomeScreen(empty.copy(hasPlans = true), HomeActions(onPlan = { plans++ })) }

        onNodeWithText("Nothing more due").assertIsDisplayed()
        onNodeWithText("Nothing planned").assertDoesNotExist()
        onNodeWithText("Plan your week").assertDoesNotExist()
        onNodeWithText("Edit plan").performClick()
        assertEquals(1, plans)
    }

    @Test
    fun `a planned workout is up next under start workout`() = runUiTest {
        val started = mutableListOf<String>()
        val state = empty.copy(
            weekGoal = WeekGoal(count = 1, goal = 3),
            upNext = listOf(
                UpNextItem.Planned(1, THURSDAY, HistoryDay.Today, hiitPreset.name, hiitPreset.config),
                UpNextItem.Planned(2, THURSDAY + 1, HistoryDay.Tomorrow, tabataPreset.name, tabataPreset.config),
            ),
        )
        setThemedContent { HomeScreen(state, HomeActions(onStart = { name, _ -> started += name })) }

        onNodeWithText("Up next · Today").assertIsDisplayed()
        onNodeWithText("2 more to hit 3").assertIsDisplayed()
        onNodeWithText("Then Tomorrow · Tabata").assertIsDisplayed()
        onNodeWithText("Start workout").performClick()
        assertEquals(listOf("HIIT 30/30"), started)
    }

    @Test
    fun `a program day starts by its number and a session in progress resumes`() = runUiTest {
        val started = mutableListOf<ProgramKey?>()
        val day = program(ProgramId.PressLadders).day(3)
        var state = empty.copy(upNext = listOf(UpNextItem.Lift(THURSDAY, HistoryDay.Today, day)))
        setThemedContent { HomeScreen(state, HomeActions(onStartLift = { started += it })) }
        onNodeWithText("Start day 3").performClick()
        assertEquals(listOf<ProgramKey?>(day.program.key), started)

        state = empty.copy(upNext = listOf(UpNextItem.Lift(THURSDAY, HistoryDay.Today, day, resume = true)))
        setThemedContent { HomeScreen(state, HomeActions(onStartLift = { started += it })) }
        onNodeWithText("Resume session").performClick()
        // Resuming names no program: Lift picks up the session it has.
        assertEquals(listOf<ProgramKey?>(day.program.key, null), started)
    }

    @Test
    fun `a session planned with no program followed starts a free one`() = runUiTest {
        val started = mutableListOf<ProgramKey?>()
        val state = empty.copy(upNext = listOf(UpNextItem.Lift(THURSDAY, HistoryDay.Today, programDay = null)))
        setThemedContent { HomeScreen(state, HomeActions(onStartLift = { started += it })) }

        onNodeWithText("Lift session").assertIsDisplayed()
        onNodeWithText("Start session").performClick()
        assertEquals(listOf<ProgramKey?>(null), started)
    }

    @Test
    fun `the minutes picked and a pick's own start are handed on`() = runUiTest {
        val minutes = mutableListOf<Int>()
        val started = mutableListOf<String>()
        setThemedContent {
            HomeScreen(
                empty,
                HomeActions(onMinutes = { minutes += it }, onStartPick = { started += it.preset.name }),
            )
        }

        onNode(option("10 min")).assertIsSelected()
        onNode(option("20 min")).performClick()
        onNodeWithText("HIIT 30/30").assertIsDisplayed()
        onNodeWithContentDescription("Start Tabata").performClick()
        assertEquals(listOf(20), minutes)
        assertEquals(listOf("Tabata"), started)
    }

    @Test
    fun `with no preset that short the way leads to the timer`() = runUiTest {
        var opened = 0
        setThemedContent { HomeScreen(empty.copy(picks = emptyList()), HomeActions(onOpenTimer = { opened++ })) }

        onNodeWithText("Nothing that short yet").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `nothing is drawn under the title until the first state is built`() = runUiTest {
        setThemedContent { HomeScreen(HomeUiState.Loading, HomeActions()) }

        onNode(hasText("Today") and isHeading()).assertIsDisplayed()
        onNodeWithText("Nothing planned").assertDoesNotExist()
        onNodeWithText("Or pick by time").assertDoesNotExist()
    }

    @Test
    fun `a tablet sets the cards side by side with every one in view`() = runUiTest(Window.Tablet) {
        setThemedContent { HomeScreen(empty, HomeActions()) }

        onNodeWithText("This week").assertIsDisplayed()
        onNodeWithText("Nothing planned").assertIsDisplayed()
        onNodeWithText("Or pick by time").assertIsDisplayed()
    }
}
