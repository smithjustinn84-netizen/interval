@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.domain.program
import dev.justinsmith.interval.fakes.hangboardPreset
import dev.justinsmith.interval.fakes.hiitPreset
import dev.justinsmith.interval.fakes.tabataPreset
import dev.justinsmith.interval.plan.PlanChoice
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

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

class HistoryScreenTest {
    private val events = mutableListOf<String>()

    private val actions = HistoryActions(
        onClear = { events += "clear" },
        onRange = { events += "range $it" },
        onOpenDay = { events += "day $it" },
        onStart = { name, _ -> events += "start $name" },
        onRemovePlanned = { events += "remove $it" },
        onPlanOnce = { preset, day -> events += "once ${preset.name} $day" },
        onOpenWorkout = { events += "open $it" },
        onPlan = { events += "plan" },
        onShowTimer = { events += "timer" },
        onStartLift = { events += "lift ${it?.stored}" },
    )

    @Test
    fun `finished workouts show under their day and open`() = runUiTest {
        setThemedContent { HistoryScreen(oneWorkout, actions) }
        onNodeWithText("Today").assertExists()
        onNodeWithText("Week").assertExists()
        onNodeWithText("Tabata").performClick()
        assertEquals(listOf("open 7"), events)
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
                HistorySection(HistoryDay.On("Sunday, Oct 11"), dayNumber = 20_737, emptyList()),
            ),
        )
        setThemedContent { HistoryScreen(state, actions) }
        onNodeWithText("Yesterday").assertExists()
        onNodeWithText("Sunday, Oct 11").assertExists()
        onNodeWithText("Planned · 20s on · 10s off · ×8 · ended early · 7:42 PM").assertExists()
        onNodeWithText("25s on · 5s off · ×4").assertExists()
        onNodeWithText("6:15 PM").assertExists()
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
        setThemedContent { HistoryScreen(state, actions) }
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
        onNodeWithText("1 more to hit 4").assertExists()
        onNodeWithText("5-week streak").assertExists()

        state = state.copy(weekCount = 4)
        onNodeWithText("Goal met this week").assertExists()

        state = oneWorkout
        onNodeWithText("1 workout").assertExists()
        onNodeWithText("Set a weekly goal").performClick()
        assertEquals(listOf("plan"), events)
    }

    @Test
    fun `up next starts the next plan and lists the ones after`() = runUiTest {
        val state = oneWorkout.copy(
            upNext = listOf(
                UpNextItem.Planned(1, 20_741, HistoryDay.Today, tabata.name, tabata.config),
                UpNextItem.Planned(2, 20_742, HistoryDay.Tomorrow, hiit.name, hiit.config),
            ),
            hasPlans = true,
        )
        setThemedContent { HistoryScreen(state, actions) }
        onNodeWithText("Up next · Today").assertExists()
        onNodeWithText("Then Tomorrow · HIIT 30/30").assertExists()
        onNodeWithText("Start").performClick()
        onNodeWithText("Edit plan").performClick()
        assertEquals(listOf("start Tabata", "plan"), events)
    }

    @Test
    fun `up next starts the lift program's day or resumes the session in progress`() = runUiTest {
        val day = checkNotNull(program(ProgramId.PressLadders).dayAfter(done = 0))
        var state by mutableStateOf(
            oneWorkout.copy(
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
        onNodeWithText("Then Tomorrow · Tabata").assertExists()
        onNodeWithText("Start").performClick()

        state = state.copy(upNext = listOf(UpNextItem.Lift(20_741, HistoryDay.Today, day, resume = true)))
        onNodeWithText("Resume").performClick()
        assertEquals(listOf("lift ${day.program.key.stored}", "lift null"), events)
    }

    @Test
    fun `picking a range asks for it`() = runUiTest {
        setThemedContent { HistoryScreen(oneWorkout, actions) }
        onNodeWithText("Month").performClick()
        assertEquals(listOf("range Month"), events)
    }

    @Test
    fun `the first run offers a plan or a workout and has nothing to clear`() = runUiTest {
        setThemedContent { HistoryScreen(empty, actions) }
        onNodeWithText("Train to a plan").assertExists()
        onNodeWithContentDescription("More options").assertIsNotEnabled()
        onNodeWithText("Plan your week").performClick()
        onNodeWithText("Start a workout").performClick()
        assertEquals(listOf("plan", "timer"), events)
    }

    @Test
    fun `clearing asks first`() = runUiTest {
        setThemedContent { HistoryScreen(oneWorkout, actions) }
        onNodeWithContentDescription("More options").performClick()
        onNodeWithText("Clear history").performClick()
        onNodeWithText("Clear history?").assertExists()
        onNodeWithText("Every finished workout is removed. This can't be undone.").assertExists()
        onNodeWithText("Cancel").performClick()
        assertEquals(emptyList(), events)

        onNodeWithContentDescription("More options").performClick()
        onNodeWithText("Clear history").performClick()
        onNodeWithText("Clear").performClick()
        assertEquals(listOf("clear"), events)
        onNodeWithText("Clear history?").assertDoesNotExist()
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
    fun `without a weight there are no calories`() = runUiTest {
        setThemedContent { HistoryScreen(oneWorkout, actions) }
        onNodeWithText("Calories").assertDoesNotExist()
        onNodeWithText("kcal", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a day sheet lists its plans and removes a one-off`() = runUiTest {
        val sheet = DaySheet(
            epochDay = 20_742,
            day = HistoryDay.Tomorrow,
            weekday = 5,
            plans = listOf(
                SheetPlan(1, hiit.name, hiit.config, weekly = true, state = PlanState.Open),
                SheetPlan(2, "Hangboard", hangboardPreset.config, weekly = false, state = PlanState.Open),
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
        onNodeWithText("Kettlebell · 7:42 AM").assertExists()
        onNode(hasClickAction() and hasText("Swings")).performClick()
        assertEquals(listOf("b1"), opened)
    }
}
