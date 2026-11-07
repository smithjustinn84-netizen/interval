@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.interval.fakes.hiitPreset
import dev.justinsmith.interval.fakes.tabataPreset
import dev.justinsmith.interval.history.DayCell
import dev.justinsmith.interval.history.DayState
import dev.justinsmith.interval.history.HistoryDay
import dev.justinsmith.interval.history.Streak
import dev.justinsmith.interval.history.UpNextItem
import dev.justinsmith.interval.history.WeekGoal
import dev.justinsmith.interval.model.Sport
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
    fun `nothing planned leads to set up first then to the plan and to the programs`() = runUiTest {
        val calls = mutableListOf<String>()
        setThemedContent {
            HomeScreen(
                empty,
                HomeActions(
                    onPlan = { calls += "plan" },
                    onPrograms = { calls += "programs" },
                    onSetup = { calls += "set up" },
                ),
            )
        }

        onNode(hasText("Today") and isHeading()).assertIsDisplayed()
        onNodeWithText("Nothing planned").assertIsDisplayed()
        onNodeWithText("Set up your week").performClick()
        onNodeWithText("Plan your week").performClick()
        onNodeWithText("Follow a program").performClick()
        assertEquals(listOf("set up", "plan", "programs"), calls)
    }

    @Test
    fun `with the plan done it says so and leads to the plan rather than offering a first one`() = runUiTest {
        var plans = 0
        setThemedContent { HomeScreen(empty.copy(hasPlans = true), HomeActions(onPlan = { plans++ })) }

        onNodeWithText("Nothing more due").assertIsDisplayed()
        onNodeWithText("Nothing planned").assertDoesNotExist()
        onNodeWithText("Set up your week").assertDoesNotExist()
        onNodeWithText("Plan your week").assertDoesNotExist()
        onNodeWithText("Edit plan").performClick()
        assertEquals(1, plans)
    }

    @Test
    fun `the week says how it stands against the goal and leads to one while there is none`() = runUiTest {
        var opened = 0
        var state by mutableStateOf(empty.copy(weekGoal = WeekGoal(count = 2)))
        setThemedContent { HomeScreen(state, HomeActions(onPlan = { opened++ })) }

        onNodeWithText("2 workouts").assertIsDisplayed()
        onNodeWithText("Set a weekly goal").performClick()
        assertEquals(1, opened)

        state = empty.copy(weekGoal = WeekGoal(count = 3, goal = 3, streak = Streak(current = 2, best = 2)))
        onNodeWithText("Goal met").assertIsDisplayed()
        onNodeWithText("2-week streak").assertIsDisplayed()
        onNodeWithText("Set a weekly goal").assertDoesNotExist()
    }

    @Test
    fun `each day of the week is a dot read by its state that opens the log`() = runUiTest {
        var opened = 0
        val week = restWeek.mapIndexed { i, day ->
            when (i) {
                0 -> day.copy(state = DayState.Done, seconds = 480, mobilitySeconds = 480)
                1 -> day.copy(state = DayState.Missed)
                3 -> day.copy(state = DayState.Done, seconds = 0, sport = Sport.Bjj, sportLogged = true)
                5 -> day.copy(state = DayState.Planned)
                else -> day
            }
        }
        setThemedContent { HomeScreen(empty.copy(week = week), HomeActions(onOpenHistory = { opened++ })) }

        onNodeWithContentDescription("Monday, done").assertIsDisplayed()
        onNodeWithContentDescription("Tuesday, missed").assertIsDisplayed()
        onNodeWithContentDescription("Today, done, BJJ logged").performClick()
        onNodeWithContentDescription("Saturday, planned").assertIsDisplayed()
        onNodeWithText("BJJ", useUnmergedTree = true).assertExists()
        assertEquals(1, opened)
    }

    @Test
    fun `a planned workout is up next under start with the one thing after it`() = runUiTest {
        val started = mutableListOf<String>()
        val state = empty.copy(
            weekGoal = WeekGoal(count = 1, goal = 3),
            upNext = listOf(
                UpNextItem.Planned(1, THURSDAY, HistoryDay.Today, hiitPreset.name, hiitPreset.config),
                UpNextItem.Planned(2, THURSDAY + 1, HistoryDay.Tomorrow, tabataPreset.name, tabataPreset.config),
                UpNextItem.Planned(3, THURSDAY + 2, HistoryDay.On("Sat"), hiitPreset.name, hiitPreset.config),
            ),
        )
        setThemedContent { HomeScreen(state, HomeActions(onStart = { name, _ -> started += name })) }

        onNodeWithText("Up next · Today").assertIsDisplayed()
        onNodeWithText("1 of 3 done").assertIsDisplayed()
        onNodeWithText("Then tomorrow · Tabata").assertIsDisplayed()
        onNodeWithText("Start").performClick()
        assertEquals(listOf("HIIT 30/30"), started)
    }

    @Test
    fun `a class today is named under its day and leads up next to be logged`() = runUiTest {
        val logged = mutableListOf<String>()
        val state = empty.copy(
            week = restWeek.map { if (it.today) it.copy(sport = Sport.Bjj) else it },
            upNext = listOf(UpNextItem.SportDay(1, THURSDAY, HistoryDay.Today, Sport.Bjj)),
            hasPlans = true,
        )
        setThemedContent { HomeScreen(state, HomeActions(onLogSport = { sport, text -> logged += "$sport: $text" })) }

        onNodeWithContentDescription("Today, BJJ planned").assertExists()
        onNodeWithText("BJJ", useUnmergedTree = true).assertExists()
        onNodeWithText("BJJ class").assertIsDisplayed()
        onNodeWithText("Counts toward your week").assertIsDisplayed()
        onNodeWithText("Start").assertDoesNotExist()
        onNodeWithText("Also today").assertDoesNotExist()
        onNodeWithText("Log it").performClick()
        assertEquals(listOf("Bjj: BJJ class logged"), logged)
    }

    @Test
    fun `a class to come is named up next with nothing to log yet`() = runUiTest {
        val state = empty.copy(
            upNext = listOf(UpNextItem.SportDay(1, THURSDAY + 1, HistoryDay.Tomorrow, Sport.Custom("Judo"))),
            hasPlans = true,
        )
        setThemedContent { HomeScreen(state, HomeActions()) }

        onNodeWithText("Up next · Tomorrow").assertIsDisplayed()
        onNodeWithText("Judo").assertIsDisplayed()
        onNodeWithText("Log it").assertDoesNotExist()
    }

    @Test
    fun `on a class day the program's day sits beside the class and starts from there`() = runUiTest {
        val started = mutableListOf<ProgramKey?>()
        val day = program(ProgramId.PressLadders).day(3)
        val state = empty.copy(
            upNext = listOf(
                UpNextItem.Lift(THURSDAY, HistoryDay.Today, day, minutes = 35),
                UpNextItem.SportDay(1, THURSDAY, HistoryDay.Today, Sport.Climbing),
            ),
            hasPlans = true,
        )
        setThemedContent { HomeScreen(state, HomeActions(onStartLift = { started += it })) }

        // The class leads, to be logged; the program's day is a row under "Also today", with no Start of its own.
        onNodeWithText("Climbing").assertIsDisplayed()
        onNodeWithText("Log it").assertIsDisplayed()
        onNodeWithText("Also today").assertIsDisplayed()
        onNodeWithText("Press ladders 1-2-3").assertIsDisplayed()
        onNodeWithText("about 35 min · Day 3 of", substring = true).assertIsDisplayed()
        onNodeWithText("Start day 3").assertDoesNotExist()
        onNodeWithText("Do it now").performClick()
        assertEquals(listOf<ProgramKey?>(day.program.key), started)
    }

    @Test
    fun `a workout planned on a class day leads and the class is logged from beside it`() = runUiTest {
        val events = mutableListOf<String>()
        val state = empty.copy(
            upNext = listOf(
                UpNextItem.Planned(1, THURSDAY, HistoryDay.Today, hiitPreset.name, hiitPreset.config),
                UpNextItem.SportDay(2, THURSDAY, HistoryDay.Today, Sport.Bjj),
            ),
            hasPlans = true,
        )
        setThemedContent {
            HomeScreen(
                state,
                HomeActions(
                    onStart = { name, _ -> events += "start $name" },
                    onLogSport = { _, text -> events += text },
                ),
            )
        }

        onNodeWithText("Also today").assertIsDisplayed()
        onNodeWithText("Counts toward your week").assertIsDisplayed()
        onNodeWithText("Log it").performClick()
        onNodeWithText("Start").performClick()
        assertEquals(listOf("BJJ class logged", "start HIIT 30/30"), events)
    }

    @Test
    fun `a program day starts by its number and a session in progress resumes`() = runUiTest {
        val started = mutableListOf<ProgramKey?>()
        val day = program(ProgramId.PressLadders).day(3)
        var state = empty.copy(upNext = listOf(UpNextItem.Lift(THURSDAY, HistoryDay.Today, day, minutes = 35)))
        setThemedContent { HomeScreen(state, HomeActions(onStartLift = { started += it })) }
        onNodeWithText("about 35 min", substring = true).assertExists()
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
    fun `the minutes picked are handed on and a pick opens a look at it that starts it`() = runUiTest {
        val minutes = mutableListOf<Int>()
        val started = mutableListOf<String>()
        setThemedContent {
            HomeScreen(
                empty,
                HomeActions(onMinutes = { minutes += it }, onStartPick = { started += it.name }),
            )
        }

        onNode(option("10 min")).assertIsSelected()
        onNode(option("20 min")).performClick()
        onNodeWithText("HIIT 30/30").assertIsDisplayed()
        // Under the card's three ways to a plan, the third pick is below a phone's first screen.
        onNodeWithText("Tabata").performScrollTo().performClick()
        onNodeWithText("Conditioning · ", substring = true).assertExists()
        onNodeWithText("Plan for later").assertExists()
        onNodeWithText("Start").performClick()
        assertEquals(listOf(20), minutes)
        waitUntil { started.isNotEmpty() }
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
