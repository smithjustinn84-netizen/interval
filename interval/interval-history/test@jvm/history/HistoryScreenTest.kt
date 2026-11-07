@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.fakes.hangboardPreset
import dev.justinsmith.interval.fakes.hiitPreset
import dev.justinsmith.interval.fakes.tabataPreset
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.plan.PlanChoice
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.program
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val tabata = tabataPreset
private val hiit = hiitPreset

/** Monday to Sunday, Thursday being today, each day in [states]. */
private fun week(vararg states: DayState) = states.mapIndexed { i, state ->
    DayCell(
        20_738L + i,
        weekday = i + 1,
        dayOfMonth = 12 + i,
        state,
        seconds = if (state ==
            DayState.Done
        ) {
            240
        } else {
            0
        },
        today = i == 3,
    )
}

private val restWeek = week(*Array(7) { DayState.Rest })

private val oneWorkout = HistoryUiState(
    sections = listOf(
        HistorySection(
            HistoryDay.Today,
            dayNumber = 20_741,
            listOf(HistoryItem("Tabata", tabata.config, false, "7:42 PM", "4:00", id = 7)),
        ),
    ),
    summary = RangeSummary(1, "4:00", 240, null, 0, 0),
    weekCount = 1,
    week = restWeek,
)

private val empty = HistoryUiState(sections = emptyList(), week = restWeek)

@Suppress("detekt.TooManyFunctions") // One behaviour per test, side by side.
class HistoryScreenTest {
    private val events = mutableListOf<String>()

    private val actions = HistoryActions(
        onRange = { events += "range $it" },
        onOpenDay = { events += "day $it" },
        onStart = { name, _ -> events += "start $name" },
        onRemovePlanned = { id, _ -> events += "remove $id" },
        onLogSport = { sport, day, text -> events += "log $sport $day: $text" },
        onUnlogSport = { sport, day, text -> events += "unlog $sport $day: $text" },
        onPlanOnce = { workout, day -> events += "once ${(workout as? PlanWorkout.Interval)?.name ?: workout} $day" },
        onOpenWorkout = { events += "open $it" },
        onPlan = { events += "plan" },
        onShowTimer = { events += "timer" },
        onStartLift = { events += "lift ${it?.stored}" },
        onOpenSettings = { events += "settings" },
    )

    @Test
    fun `the gear opens settings`() = runUiTest {
        setThemedContent { HistoryScreen(oneWorkout, actions) }
        onNodeWithContentDescription("Settings").performClick()
        assertEquals(listOf("settings"), events)
    }

    @Test
    fun `finished workouts show under their day and open`() = runUiTest {
        setThemedContent { HistoryScreen(oneWorkout, actions) }
        onNodeWithText("Today").assertExists()
        onNodeWithText("Week").assertExists()
        onNodeWithText("Tabata").performClick()
        assertEquals(listOf("open 7"), events)
    }

    @Test
    fun `the row open beside the log says it is selected`() = runUiTest(Window.Tablet) {
        setThemedContent { HistoryScreen(oneWorkout, actions, selectedWorkout = 7) }
        onNode(hasText("Tabata") and hasClickAction()).assertIsSelected()
    }

    @Test
    fun `an upright tablet keeps the summary beside the log`() = runUiTest(Window.TabletPortrait) {
        setThemedContent { HistoryScreen(oneWorkout, actions) }
        val summary = onNodeWithText("Week").fetchSemanticsNode().boundsInRoot
        val time = onNodeWithText("Time").fetchSemanticsNode().boundsInRoot
        val day = onNodeWithText("Today").fetchSemanticsNode().boundsInRoot
        assertTrue(summary.right <= day.left, "the log is beside the summary: $summary vs $day")
        assertTrue(day.top < time.top, "and starts level with it, not under it: $time vs $day")
    }

    @Test
    fun `a phone keeps the summary over the log`() = runUiTest {
        setThemedContent { HistoryScreen(oneWorkout, actions) }
        val summary = onNodeWithText("Week").fetchSemanticsNode().boundsInRoot
        val day = onNodeWithText("Today").fetchSemanticsNode().boundsInRoot
        assertTrue(day.top > summary.bottom, "the log is under the summary: $summary vs $day")
    }

    @Test
    fun `a row names its preset or leads with its shape and says when it ended early or was planned`() = runUiTest {
        val custom = IntervalConfig(workSeconds = 25, restSeconds = 5, rounds = 4)
        val state = oneWorkout.copy(
            sections = listOf(
                HistorySection(
                    HistoryDay.Yesterday,
                    dayNumber = 20_740,
                    listOf(
                        HistoryItem("Tabata", tabata.config, endedEarly = true, "7:42 PM", "1:30", planned = true),
                        HistoryItem(null, custom, endedEarly = false, "6:15 PM", "1:40"),
                    ),
                ),
                HistorySection(HistoryDay.On("Sunday, Oct 11"), dayNumber = 20_737, emptyList(), listOf(Sport.Bjj)),
            ),
        )
        setThemedContent { HistoryScreen(state, actions) }
        // One card for the week, a day a column: yesterday's first row says "Wed", the second nothing.
        onNode(hasText("Wed") and hasText("Tabata")).assertExists()
        onNodeWithText("Yesterday").assertDoesNotExist()
        onNode(hasText("Sun") and hasText("BJJ class")).assertExists()
        onNodeWithText("Conditioning · 20s on · 10s off · ×8 · ended early · 7:42 PM").assertExists()
        onNodeWithText("25s on · 5s off · ×4").assertExists()
        onNodeWithText("Conditioning · 6:15 PM").assertExists()
    }

    @Test
    fun `the week strip names a sport under its day and reads out how it stands`() = runUiTest {
        val days = week(
            DayState.Done,
            DayState.Rest,
            DayState.Done,
            DayState.Rest,
            DayState.Rest,
            DayState.Rest,
            DayState.Planned,
        )
        val state = oneWorkout.copy(
            // A class logged on a day with a workout, one that went by, one logged alone, one to come.
            week = days.mapIndexed { index, day ->
                when (index) {
                    0 -> day.copy(sport = Sport.Bjj, sportLogged = true)
                    1 -> day.copy(sport = Sport.Bjj, sportPassed = true)
                    2 -> day.copy(seconds = 0, sport = Sport.Custom("Swimming"), sportLogged = true)
                    3 -> day.copy(sport = Sport.Climbing)
                    else -> day
                }
            },
        )
        setThemedContent { WeekStrip(state.week, onDay = {}) }

        onNodeWithContentDescription("Monday, done, BJJ logged").assertExists()
        onNodeWithContentDescription("Tuesday, BJJ not logged").assertExists()
        onNodeWithContentDescription("Wednesday, done, Swimming logged").assertExists()
        onNodeWithContentDescription("Today, Climbing planned").assertExists()
        onNodeWithContentDescription("Friday, no workout").assertExists()
        // The names under the days, short enough for their columns.
        onAllNodesWithText("BJJ", useUnmergedTree = true).assertCountEquals(2)
        onNodeWithText("Swimm", useUnmergedTree = true).assertExists()
        onNodeWithText("Climb", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a week with nothing done yet shows no time and the month keys only what it draws`() = runUiTest {
        var state by mutableStateOf(
            oneWorkout.copy(sections = emptyList(), summary = RangeSummary(0, "0:00", 0, null, 0, 0), hasPlans = true),
        )
        setThemedContent { HistoryScreen(state, actions) }
        onNodeWithText("Time").assertDoesNotExist()
        onNodeWithText("0:00").assertDoesNotExist()

        state = oneWorkout
        onNodeWithText("Time").assertExists()
        // The week's time under its caption, and the workout's own on its row.
        onAllNodesWithText("4:00").assertCountEquals(2)

        state = oneWorkout.copy(range = TrainingRange.Month)
        onNodeWithText("Done", useUnmergedTree = true).assertExists()
        onNodeWithText("Planned", useUnmergedTree = true).assertExists()
        onNodeWithText("Missed", useUnmergedTree = true).assertExists()
        onNodeWithText("Interval", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Lift", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the week strip reads each day's state and opens a day`() = runUiTest {
        val state = oneWorkout.copy(
            week = week(
                DayState.Done,
                DayState.Missed,
                DayState.Rest,
                DayState.Planned,
                DayState.Rest,
                DayState.Rest,
                DayState.Planned,
            ),
        )
        setThemedContent { WeekStrip(state.week, onDay = { events += "day $it" }) }
        onNodeWithContentDescription("Monday, done").assertExists()
        onNodeWithContentDescription("Tuesday, missed").assertExists()
        onNodeWithContentDescription("Wednesday, no workout").assertExists()
        onNodeWithContentDescription("Today, planned").assertExists()
        onNodeWithContentDescription("Sunday, planned").assertExists()
        onNodeWithContentDescription("Monday, done").performClick()
        assertEquals(listOf("day 20738"), events)
    }

    @Test
    fun `with a goal the ring and streak show and without one it can be set`() = runUiTest {
        var state by mutableStateOf(oneWorkout.copy(weekCount = 3, goal = 4, streak = Streak(5, 7)))
        setThemedContent { HistoryScreen(state, actions) }
        onNodeWithContentDescription("3 of 4 workouts this week").assertExists()
        onNodeWithText("1 to go").assertExists()
        onNodeWithText("5-week streak").assertExists()

        state = state.copy(weekCount = 4)
        onNodeWithText("Goal met").assertExists()
        // The ring closes on the goal, ignites and pops; the figure still reads.
        mainClock.advanceTimeBy(Motion.ENTER_MILLIS + Motion.CONTENT_MILLIS.toLong())
        onNodeWithContentDescription("4 of 4 workouts this week").assertExists()

        state = oneWorkout
        onNodeWithText("1 workout").assertExists()
        onNodeWithText("Set a weekly goal").performClick()
        assertEquals(listOf("plan"), events)
    }

    @Test
    fun `the month's up next starts the next plan and lists the ones after`() = runUiTest {
        val state = oneWorkout.copy(
            range = TrainingRange.Month,
            upNext = listOf(
                UpNextItem.Planned(1, 20_741, HistoryDay.Today, tabata.name, tabata.config),
                UpNextItem.Planned(2, 20_742, HistoryDay.Tomorrow, hiit.name, hiit.config),
            ),
            hasPlans = true,
        )
        setThemedContent { HistoryScreen(state, actions) }
        onNodeWithText("Up next · Today").assertExists()
        onNodeWithText("Then tomorrow · HIIT 30/30").assertExists()
        onNodeWithText("Start").performClick()
        onNodeWithText("Edit plan").performClick()
        assertEquals(listOf("start Tabata", "plan"), events)
    }

    @Test
    fun `up next starts the lift program's day or resumes the session in progress`() = runUiTest {
        val day = checkNotNull(program(ProgramId.PressLadders).dayAfter(done = 0))
        var state by mutableStateOf(
            oneWorkout.copy(
                range = TrainingRange.Month,
                upNext = listOf(
                    UpNextItem.Lift(20_741, HistoryDay.Today, day),
                    UpNextItem.Planned(1, 20_742, HistoryDay.Tomorrow, tabata.name, tabata.config),
                ),
                hasPlans = true,
            ),
        )
        setThemedContent { HistoryScreen(state, actions) }
        onNodeWithText("Press ladders 1-2-3").assertExists()
        onNode(hasText("Day 1 of", substring = true)).assertExists()
        onNodeWithText("Then tomorrow · Tabata").assertExists()
        onNodeWithText("Start").performClick()

        state = state.copy(upNext = listOf(UpNextItem.Lift(20_741, HistoryDay.Today, day, resume = true)))
        onNodeWithText("Resume").performClick()
        assertEquals(listOf("lift ${day.program.key.stored}", "lift null"), events)
    }

    @Test
    fun `the week lists what is coming up a row a day with Start on today's and a look at the rest`() = runUiTest {
        val day = checkNotNull(program(ProgramId.PressLadders).dayAfter(done = 0))
        val state = oneWorkout.copy(
            upNext = listOf(
                UpNextItem.Lift(20_741, HistoryDay.Today, day),
                UpNextItem.Planned(2, 20_742, HistoryDay.Tomorrow, hiit.name, hiit.config),
                UpNextItem.SportDay(3, 20_743, HistoryDay.On("Saturday, Oct 17"), Sport.Bjj),
                UpNextItem.Lift(20_744, HistoryDay.On("Sunday, Oct 18"), day, minutes = 15),
            ),
            hasPlans = true,
        )
        setThemedContent { HistoryScreen(state, actions) }
        onNodeWithText("Coming up").assertExists()
        onNodeWithText("Up next · Today").assertDoesNotExist()
        // Each row keyed by its day: today by name, the rest by weekday, tomorrow too.
        onNode(hasText("Today") and hasText("Press ladders 1-2-3")).assertExists()
        onNodeWithText("Tomorrow").assertDoesNotExist()
        onNodeWithText("Fri").assertExists()
        onNodeWithText("Sat").assertExists()
        onNodeWithText("Sun").assertExists()
        // One line each, led by its kind, with its length as the figure on a later row.
        onAllNodes(hasText("Strength · Day 1 of", substring = true)).assertCountEquals(2)
        onNodeWithText("Conditioning · 30s on · 30s off · ×10").assertExists()
        onNodeWithText("Sport day · Counts toward your week").assertExists()
        onNodeWithText(formatClock(hiit.config.totalSeconds)).assertExists()
        onNodeWithText("15 min").assertExists()
        // Start on today's row alone; a later row opens the look, where Start is.
        onAllNodesWithText("Start").assertCountEquals(1)
        onNodeWithText("HIIT 30/30").performClick()
        onNodeWithText("Plan for later").assertExists()
        onAllNodesWithText("Start").onLast().performClick()
        onNodeWithText("Edit plan").performClick()
        assertEquals(listOf("start HIIT 30/30", "plan"), events)
    }

    @Test
    fun `the week's bars count each kind against its plan and the log splits this week from earlier`() = runUiTest {
        val state = oneWorkout.copy(
            byKind = listOf(
                KindTally(WorkoutKind.Strength, done = 1, planned = 2),
                KindTally(WorkoutKind.Sport, done = 1, planned = 3),
            ),
            weekStart = 20_738,
            sections = oneWorkout.sections + HistorySection(
                HistoryDay.On("Sunday, Oct 11"),
                dayNumber = 20_737,
                listOf(HistoryItem("HIIT 30/30", hiit.config, false, "6:00 PM", "9:40", id = 8)),
            ),
        )
        setThemedContent { HistoryScreen(state, actions) }
        onNodeWithContentDescription("Strength: 1 of 2").assertExists()
        onNodeWithContentDescription("Sport days: 1 of 3").assertExists()
        onNodeWithText("Conditioning").assertDoesNotExist()
        onNodeWithText("Done this week").assertExists()
        onNodeWithText("Earlier").assertExists()
        val week = onNodeWithText("Done this week").fetchSemanticsNode().boundsInRoot
        val earlier = onNodeWithText("Earlier").fetchSemanticsNode().boundsInRoot
        val old = onNodeWithText("HIIT 30/30").fetchSemanticsNode().boundsInRoot
        assertTrue(week.top < earlier.top && earlier.top < old.top, "this week, then earlier: $week $earlier $old")
    }

    @Test
    fun `plus plans a workout from the title bar`() = runUiTest {
        val state = oneWorkout.copy(choices = listOf(PlanChoice(tabata)), today = 20_741)
        setThemedContent { HistoryScreen(state, actions) }
        onNodeWithContentDescription("Plan a workout").performClick()
        onNodeWithText("Tabata").assertExists()
    }

    @Test
    fun `picking a range asks for it`() = runUiTest {
        setThemedContent { HistoryScreen(oneWorkout, actions) }
        onNodeWithText("Month").performClick()
        assertEquals(listOf("range Month"), events)
    }

    @Test
    fun `the first run offers a plan or a workout`() = runUiTest {
        setThemedContent { HistoryScreen(empty, actions) }
        onNodeWithText("Train to a plan").assertExists()
        onNodeWithText("Plan your week").performClick()
        onNodeWithText("Start a workout").performClick()
        assertEquals(listOf("plan", "timer"), events)
    }

    @Test
    fun `while loading it shows neither the first run nor a summary`() = runUiTest {
        setThemedContent { HistoryScreen(HistoryUiState.Loading, actions) }
        onNodeWithText("Log").assertExists()
        onNodeWithText("Train to a plan").assertDoesNotExist()
        onNodeWithText("Month").assertDoesNotExist()
    }

    @Test
    fun `with a weight set rows and the week show calories`() = runUiTest {
        val item = HistoryItem("Tabata", tabata.config, false, "7:42 PM", "4:00", calories = 48)
        val state = oneWorkout.copy(
            sections = listOf(HistorySection(HistoryDay.Today, dayNumber = 20_741, listOf(item))),
            summary = oneWorkout.summary.copy(calories = 48),
        )
        setThemedContent { HistoryScreen(state, actions) }
        onNodeWithText("Calories").assertExists()
        onNodeWithText("48").assertExists()
        onNodeWithText("48 kcal", substring = true).assertExists()
    }

    @Test
    fun `a row says how hard it felt and its note`() = runUiTest {
        val item = HistoryItem(
            "Tabata",
            tabata.config,
            false,
            "7:42 PM",
            "4:00",
            effort = PerceivedEffort.AllOut,
            note = "New best",
        )
        val state = oneWorkout.copy(
            sections = listOf(HistorySection(HistoryDay.Today, dayNumber = 20_741, listOf(item))),
        )
        setThemedContent { HistoryScreen(state, actions) }
        onNodeWithText("Conditioning · 20s on · 10s off · ×8 · All out · 7:42 PM").assertExists()
        onNodeWithText("New best").assertExists()
    }

    @Test
    fun `without a weight there are no calories`() = runUiTest {
        setThemedContent { HistoryScreen(oneWorkout, actions) }
        onNodeWithText("Calories").assertDoesNotExist()
        onNodeWithText("kcal", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a day sheet's session on Lift starts a free session or the program's day`() = runUiTest {
        val sheet = DaySheet(
            epochDay = 20_742,
            day = HistoryDay.Tomorrow,
            weekday = 5,
            plans = listOf(
                SheetPlan(1, PlanWorkout.Lift.AnySession, weekly = false, state = PlanState.Open),
                SheetPlan(2, PlanWorkout.Lift.ProgramDay, weekly = true, state = PlanState.Missed),
            ),
            workouts = emptyList(),
            past = false,
        )
        setThemedContent { HistoryScreen(oneWorkout.copy(daySheet = sheet), actions) }
        onNodeWithText("Any lift session").assertExists()
        onNodeWithText("Next program day").assertExists()
        onNodeWithText("Missed · Every Friday").assertExists()
        onNodeWithText("Start").performClick()
        assertEquals(listOf("lift null"), events)
    }

    @Test
    fun `a free session on Lift up next starts one`() = runUiTest {
        val upNext = listOf(UpNextItem.Lift(20_742, HistoryDay.Today, programDay = null, planId = 1))
        setThemedContent { HistoryScreen(oneWorkout.copy(upNext = upNext, hasPlans = true), actions) }
        onNodeWithText("Lift session").assertExists()
        onNodeWithText("Strength · Any session on Lift").assertExists()
        onNodeWithText("Start").performClick()
        assertEquals(listOf("lift null"), events)
    }

    @Test
    fun `a day sheet lists its plans and removes a one-off`() = runUiTest {
        val sheet = DaySheet(
            epochDay = 20_742,
            day = HistoryDay.Tomorrow,
            weekday = 5,
            plans = listOf(
                SheetPlan(1, PlanWorkout.Interval(hiit.name, hiit.config), weekly = true, state = PlanState.Open),
                SheetPlan(
                    2,
                    PlanWorkout.Interval("Hangboard", hangboardPreset.config),
                    weekly = false,
                    state = PlanState.Open,
                ),
            ),
            workouts = emptyList(),
            past = false,
        )
        setThemedContent { HistoryScreen(oneWorkout.copy(daySheet = sheet), actions) }
        onNodeWithText("2 planned").assertExists()
        onNodeWithText("Every Friday", substring = true).assertExists()
        onNodeWithText("A routine workout changes in Plan, for every week.").assertExists()
        onNodeWithContentDescription("Remove HIIT 30/30").assertDoesNotExist()
        onNodeWithContentDescription("Remove Hangboard").performClick()
        assertEquals(listOf("remove 2"), events)
    }

    @Test
    fun `a class is logged from its day's sheet and one logged comes off there`() = runUiTest {
        val planned = SheetPlan(1, PlanWorkout.SportDay(Sport.Bjj), weekly = true, state = PlanState.Open)
        val today = DaySheet(20_741, HistoryDay.Today, weekday = 4, listOf(planned), emptyList(), past = false)
        var state by mutableStateOf(oneWorkout.copy(daySheet = today))
        setThemedContent { HistoryScreen(state, actions) }

        onNodeWithText("Start").assertDoesNotExist()
        onNodeWithText("Log it").performClick()
        assertEquals(listOf("log Bjj 20741: BJJ class logged"), events)

        // Logged: the plan is done, and the class sits among what the day holds, with its remove.
        state = state.copy(
            daySheet = today.copy(plans = listOf(planned.copy(state = PlanState.Done)), sports = listOf(Sport.Bjj)),
        )
        onNodeWithText("Done · Every Thursday").assertExists()
        onNodeWithText("Log it").assertDoesNotExist()
        onNodeWithText("Logged").assertExists()
        onNodeWithContentDescription("Remove BJJ class").performClick()
        assertEquals("unlog Bjj 20741: Removed BJJ class", events.last())
    }

    @Test
    fun `a class that went by is logged late from its day and one to come is not`() = runUiTest {
        val passed = SheetPlan(1, PlanWorkout.SportDay(Sport.Climbing), weekly = true, state = PlanState.Passed)
        val day = HistoryDay.On("Monday, Oct 12")
        val monday = DaySheet(20_738, day, weekday = 1, listOf(passed), emptyList(), past = true)
        var state by mutableStateOf(oneWorkout.copy(daySheet = monday))
        setThemedContent { HistoryScreen(state, actions) }

        onNodeWithText("Not logged · Every Monday").assertExists()
        onNodeWithText("Log it").performClick()
        assertEquals(listOf("log Climbing 20738: Climbing logged"), events)

        state = state.copy(
            daySheet = DaySheet(
                20_743,
                HistoryDay.On("Saturday, Oct 17"),
                weekday = 6,
                listOf(passed.copy(state = PlanState.Open)),
                emptyList(),
                past = false,
            ),
        )
        onNodeWithText("Every Saturday").assertExists()
        onNodeWithText("Log it").assertDoesNotExist()
    }

    @Test
    fun `a sport logged is a row of its day in the log with its own remove`() = runUiTest {
        val state = oneWorkout.copy(
            sections = oneWorkout.sections.map { it.copy(sports = listOf(Sport.Custom("Judo"))) } +
                HistorySection(HistoryDay.Yesterday, dayNumber = 20_740, emptyList(), listOf(Sport.Bjj)),
        )
        setThemedContent { HistoryScreen(state, actions) }

        onNodeWithText("Judo").assertExists()
        onNodeWithText("BJJ class").assertExists()
        onNodeWithContentDescription("Remove Judo").performClick()
        onNodeWithContentDescription("Remove BJJ class").performClick()
        assertEquals(
            listOf("unlog Custom(name=Judo) 20741: Removed Judo", "unlog Bjj 20740: Removed BJJ class"),
            events,
        )
    }

    @Test
    fun `a day to come plans a workout on that day`() = runUiTest {
        val sheet =
            DaySheet(20_743, HistoryDay.On("Saturday, Oct 17"), weekday = 6, emptyList(), emptyList(), past = false)
        var state by mutableStateOf(oneWorkout.copy(daySheet = sheet, choices = builtInPresets.map { PlanChoice(it) }))
        val closing = actions
        setThemedContent {
            HistoryScreen(
                state,
                HistoryActions(
                    onOpenDay = { if (it == null) state = state.copy(daySheet = null) },
                    onPlanOnce = closing.onPlanOnce,
                ),
            )
        }
        onNodeWithText("Add workout").performClick()
        onNodeWithText("HIIT 30/30").performClick()
        onNode(hasText("Add to plan") and hasClickAction()).performClick()
        assertEquals(listOf("once HIIT 30/30 20743"), events)
    }

    @Test
    fun `a kettlebell session opens its own page`() = runUiTest {
        val swings = HistoryItem("Swings", IntervalConfig(), false, "7:42 AM", "26:10", liftUid = "b1")
        val state = oneWorkout.copy(sections = listOf(HistorySection(HistoryDay.Today, 20_741, listOf(swings))))
        val opened = mutableListOf<String>()
        setThemedContent { HistoryScreen(state, HistoryActions(onOpenLift = { opened += it })) }
        onNodeWithText("Strength · 7:42 AM").assertExists()
        onNode(hasClickAction() and hasText("Swings")).performClick()
        assertEquals(listOf("b1"), opened)
    }
}
