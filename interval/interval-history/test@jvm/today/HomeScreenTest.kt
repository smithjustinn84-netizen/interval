@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
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
import dev.justinsmith.interval.timer.flowBefore
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.ReadinessReason
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

@Suppress("detekt.TooManyFunctions") // One behaviour per test, side by side.
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
    fun `a day turning done draws its mark on and reads done throughout`() = runUiTest {
        var week by mutableStateOf(restWeek)
        setThemedContent { HomeScreen(empty.copy(week = week), HomeActions()) }
        onNodeWithContentDescription("Monday, no workout").assertIsDisplayed()
        week = restWeek.mapIndexed { i, day -> if (i == 0) day.copy(state = DayState.Done, seconds = 480) else day }
        onNodeWithContentDescription("Monday, done").assertIsDisplayed()
        mainClock.advanceTimeBy(Motion.ENTER_MILLIS + Motion.STATE_MILLIS.toLong())
        onNodeWithContentDescription("Monday, done").assertIsDisplayed()
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
        onNodeWithText("Thursday · class day").assertIsDisplayed()
        onNodeWithText("Today is for the mat.").assertIsDisplayed()
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
    fun `on a class day the program's day is a step for spare time after the class`() = runUiTest {
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

        // The class leads, to be logged; the program's day is the step after it, with no Start of its own.
        onNodeWithText("Thursday · climbing day").assertIsDisplayed()
        onNodeWithText("Climbing").assertIsDisplayed()
        onNodeWithText("Log it").assertIsDisplayed()
        onNodeWithText("If there is time").assertIsDisplayed()
        onNodeWithText("Press ladders 1-2-3").assertIsDisplayed()
        onNodeWithText("about 35 min · Day 3 of", substring = true).assertIsDisplayed()
        onNodeWithText("Start day 3").assertDoesNotExist()
        onNodeWithText("Do it now").performClick()
        assertEquals(listOf<ProgramKey?>(day.program.key), started)
    }

    @Test
    fun `a workout planned on a class day leads and the class is the step after it`() = runUiTest {
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

        onNodeWithText("Counts toward your week").assertIsDisplayed()
        onNodeWithText("If there is time").assertDoesNotExist()
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
        // Each row says its kind before its shape, and a flow's its moves.
        onNodeWithText("Conditioning · 30s on · 30s off", substring = true).assertIsDisplayed()
        onNodeWithText("Mobility · ", substring = true).assertExists()
        // Under the card's three ways to a plan, the third pick is below a phone's first screen.
        onNodeWithText("Hangboard").performScrollTo().performClick()
        // The look's own caption, under the rows' kinds.
        onAllNodesWithText("Conditioning · ", substring = true).onLast().assertExists()
        onNodeWithText("Plan for later").assertExists()
        onNodeWithText("Start").performClick()
        assertEquals(listOf(20), minutes)
        waitUntil { started.isNotEmpty() }
        assertEquals(listOf("Hangboard"), started)
    }

    @Test
    fun `with no preset that short the way leads to the timer`() = runUiTest {
        var opened = 0
        setThemedContent { HomeScreen(empty.copy(picks = emptyList()), HomeActions(onOpenTimer = { opened++ })) }

        onNodeWithText("Nothing that short yet").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `what is done today is ticked above the step still to do`() = runUiTest {
        val logged = mutableListOf<String>()
        val flow = flowBefore(Sport.Bjj)
        val state = empty.copy(
            upNext = listOf(UpNextItem.SportDay(1, THURSDAY, HistoryDay.Today, Sport.Bjj)),
            done = listOf(DoneStep.Workout(flow.name, flow.config, elapsedSeconds = 540)),
            hasPlans = true,
        )
        setThemedContent { HomeScreen(state, HomeActions(onLogSport = { _, text -> logged += text })) }

        onNodeWithText("Before rolling").assertIsDisplayed()
        onNodeWithText("9:00").assertIsDisplayed()
        onNode(hasStateDescription("Done")).assertExists()
        onNodeWithText("Log it").performClick()
        assertEquals(listOf("BJJ class logged"), logged)
    }

    @Test
    fun `with today's steps done it says so and what comes after waits behind a quiet button`() = runUiTest {
        val started = mutableListOf<String>()
        val state = empty.copy(
            weekGoal = WeekGoal(count = 3, goal = 3),
            upNext = listOf(
                UpNextItem.Planned(1, THURSDAY + 1, HistoryDay.Tomorrow, tabataPreset.name, tabataPreset.config),
                UpNextItem.Planned(2, THURSDAY + 2, HistoryDay.On("Sat"), hiitPreset.name, hiitPreset.config),
            ),
            done = listOf(DoneStep.Session("Press ladders", 1_200), DoneStep.SportLogged(Sport.Bjj)),
            hasPlans = true,
        )
        setThemedContent { HomeScreen(state, HomeActions(onStart = { name, _ -> started += name })) }

        onNodeWithText("Thursday · done").assertIsDisplayed()
        onNodeWithText("That is the day, and the goal for the week is met.").assertIsDisplayed()
        onNode(hasText("Done for today") and isHeading()).assertIsDisplayed()
        onNodeWithText("Press ladders").assertIsDisplayed()
        onNodeWithText("Logged · Counts toward your week").assertIsDisplayed()
        // Tomorrow's is named, with no raised Start: only a quiet way to do it today.
        onNodeWithText("Up next · Tomorrow").assertIsDisplayed()
        onNodeWithText("Start").assertDoesNotExist()
        onNodeWithText("Then Sat · HIIT 30/30").assertIsDisplayed()
        onNodeWithText("Do it today").performClick()
        assertEquals(listOf("Tabata"), started)
    }

    @Test
    fun `the first week's workout points at a start above only when today has one`() = runUiTest {
        val week = FirstWeek(
            listOf(
                FirstWeekStep(FirstWeekStep.Kind.Build, done = true),
                FirstWeekStep(FirstWeekStep.Kind.Workout, done = false),
            ),
        )
        val tomorrow = UpNextItem.Planned(1, THURSDAY + 1, HistoryDay.Tomorrow, tabataPreset.name, tabataPreset.config)
        val today = UpNextItem.Planned(2, THURSDAY, HistoryDay.Today, hiitPreset.name, hiitPreset.config)
        var state by mutableStateOf(empty.copy(upNext = listOf(tomorrow), hasPlans = true, firstWeek = week))
        setThemedContent { HomeScreen(state, HomeActions()) }

        // Tomorrow's leads with no raised Start: the first workout is picked by time.
        onNodeWithText("Pick by time, below").performScrollTo().assertIsDisplayed()
        onNodeWithText("Start, above").assertDoesNotExist()

        state = state.copy(upNext = listOf(today, tomorrow))
        onNodeWithText("Start, above").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `once the class is logged the program's day waits for spare time and the day is done`() = runUiTest {
        val started = mutableListOf<ProgramKey?>()
        val day = program(ProgramId.PressLadders).day(3)
        val state = empty.copy(
            upNext = listOf(UpNextItem.Lift(THURSDAY, HistoryDay.Today, day, minutes = 35)),
            done = listOf(DoneStep.SportLogged(Sport.Bjj)),
            hasPlans = true,
        )
        setThemedContent { HomeScreen(state, HomeActions(onStartLift = { started += it })) }

        onNode(hasText("Done for today") and isHeading()).assertIsDisplayed()
        onNodeWithText("If there is time").assertIsDisplayed()
        onNodeWithText("Start day 3").assertDoesNotExist()
        onNodeWithText("Do it now").performClick()
        assertEquals(listOf<ProgramKey?>(day.program.key), started)
    }

    @Test
    fun `a day with nothing planned is a rest day with a flow to do and tomorrow's standing back`() = runUiTest {
        val started = mutableListOf<String>()
        val state = empty.copy(
            upNext = listOf(
                UpNextItem.Planned(1, THURSDAY + 1, HistoryDay.Tomorrow, tabataPreset.name, tabataPreset.config),
            ),
            hasPlans = true,
        )
        setThemedContent { HomeScreen(state, HomeActions(onStart = { name, _ -> started += name })) }

        onNodeWithText("Thursday · rest day").assertIsDisplayed()
        onNodeWithText("Nothing is planned today. Rest is part of the week.").assertIsDisplayed()
        onNode(hasText("Rest day") and isHeading()).assertIsDisplayed()
        onNodeWithText("Nothing due until tomorrow").assertIsDisplayed()
        onNodeWithText("If moving feels good").assertIsDisplayed()
        onNodeWithText("Up next · Tomorrow").assertIsDisplayed()
        onNodeWithText("Start").performClick()
        onNodeWithText("Do it today").performClick()
        assertEquals(listOf("Daily five", "Tabata"), started)
    }

    @Test
    fun `a taxed day says why and starts the program's day easier unless switched off`() = runUiTest {
        val started = mutableListOf<String>()
        val day = program(ProgramId.PressLadders).day(3)
        val state = empty.copy(
            upNext = listOf(UpNextItem.Lift(THURSDAY, HistoryDay.Today, day, minutes = 35)),
            hasPlans = true,
            taxed = ReadinessReason.Mat,
            easier = true,
        )
        setThemedContent {
            HomeScreen(
                state,
                HomeActions(onStartLift = { started += "as planned" }, onStartLiftEasier = { started += "easier" }),
            )
        }

        onNodeWithText("Thursday · strength day").assertIsDisplayed()
        onNodeWithText("Take today a notch easier.", substring = true).assertIsDisplayed()
        onNodeWithText("Start day 3, easier").performClick()
        onNodeWithText("Easier today").performClick()
        onNodeWithText("Start day 3").performClick()
        assertEquals(listOf("easier", "as planned"), started)
    }

    @Test
    fun `a taxed day with nothing to ease says why and starts the day as it is`() = runUiTest {
        val day = program(ProgramId.PressLadders).day(3)
        val state = empty.copy(
            upNext = listOf(UpNextItem.Lift(THURSDAY, HistoryDay.Today, day, minutes = 35)),
            hasPlans = true,
            taxed = ReadinessReason.HardSession,
        )
        setThemedContent { HomeScreen(state, HomeActions()) }

        onNodeWithText("The last session felt very hard. Take today a notch easier.").assertIsDisplayed()
        onNodeWithText("Easier today").assertDoesNotExist()
        onNodeWithText("Start day 3").assertIsDisplayed()
    }

    @Test
    fun `on a day with its plan the picks fold to a row that opens them`() = runUiTest {
        val state = empty.copy(
            minutes = 20,
            upNext = listOf(UpNextItem.Planned(1, THURSDAY, HistoryDay.Today, hiitPreset.name, hiitPreset.config)),
            hasPlans = true,
        )
        setThemedContent { HomeScreen(state, HomeActions()) }

        onNodeWithText("Thursday · conditioning day").assertIsDisplayed()
        onNodeWithText("Or pick by time").assertDoesNotExist()
        onNodeWithText("Pick by time · 20 min").assertIsDisplayed()
        onNodeWithText("Something else").performScrollTo().performClick()
        onNodeWithText("Or pick by time").assertExists()
        onNode(option("20 min")).assertExists()
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
