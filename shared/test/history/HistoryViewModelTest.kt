@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.history

import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.BodySettings
import dev.justinsmith.interval.data.PlanSchedule
import dev.justinsmith.interval.fakes.DAY_MILLIS
import dev.justinsmith.interval.fakes.FakeLiftStorage
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.fakes.hiitPreset
import dev.justinsmith.interval.fakes.noonOf
import dev.justinsmith.interval.fakes.tabataPreset
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.epochDayOf
import dev.justinsmith.interval.timer.estimateCalories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val tabata = tabataPreset.config
private val hiit = hiitPreset

// Thursday 15 October 2026, and the Monday of its week.
private val MONDAY = TODAY - 3

class HistoryViewModelTest : MainDispatcherTest() {
    private var clock = noonOf(TODAY)
    private val repository = FakeStorage().repository(now = { clock })
    private val lift = FakeLiftStorage()
    private val bells = lift.repository(now = { clock })
    private val viewModel by lazy { HistoryViewModel(repository, bells, FixedTimestampFormatter, clock = { clock }) }

    private fun at(day: Long) {
        clock = noonOf(day)
    }

    @Test
    fun `workouts are grouped by day newest first and name their preset`() {
        // Calories are covered below; off here, the rows compare whole.
        repository.updateAppSettings { it.copy(body = it.body.copy(weightKg = null)) }
        val custom = IntervalConfig(prepSeconds = 0, workSeconds = 25, restSeconds = 5, rounds = 4)
        at(TODAY - 10)
        repository.recordWorkout(tabata, tabata.totalSeconds)
        at(TODAY - 1)
        repository.recordWorkout(custom, custom.totalSeconds)
        repository.recordWorkout(tabata, elapsedSeconds = 90, endedEarly = true)
        at(TODAY)

        val sections = viewModel.state.value.sections
        assertEquals(listOf(HistoryDay.Yesterday, HistoryDay.On("day ${TODAY - 10}")), sections.map { it.day })
        assertEquals(listOf(TODAY - 1, TODAY - 10), sections.map { it.dayNumber })
        val time = "at ${noonOf(TODAY - 1)}"
        val (ended, customRow) = sections[0].items
        assertEquals(HistoryItem("Tabata", tabata, endedEarly = true, time = time, total = "1:30", id = 3), ended)
        assertEquals(HistoryItem(null, custom, endedEarly = false, time = time, total = "1:55", id = 2), customRow)
    }

    @Test
    fun `kettlebell sessions are listed with the interval workouts and counted in the week`() {
        repository.updateAppSettings { it.copy(body = it.body.copy(weightKg = null)) }
        at(TODAY)
        repository.recordWorkout(tabata, tabata.totalSeconds)
        val finished = TODAY * DAY_MILLIS + DAY_MILLIS / 4
        lift.finished("b1", "Swings and get-ups", finished - 1_570_000, finished, 24.0 to 10)

        val state = viewModel.state.value
        val (interval, swings) = state.sections.single().items
        assertEquals("Tabata", interval.presetName)
        assertEquals(null, interval.liftUid)
        assertEquals(
            HistoryItem(
                "Swings and get-ups",
                IntervalConfig(activity = Activity.Kettlebell),
                endedEarly = false,
                time = "at ${TODAY * DAY_MILLIS + DAY_MILLIS / 4}",
                total = "26:10",
                liftUid = "b1",
            ),
            swings,
        )
        assertEquals(2, state.weekCount)
        assertEquals(2, state.summary.count)
    }

    @Test
    fun `today's workouts are headed Today`() {
        repository.recordWorkout(tabata, tabata.totalSeconds)
        assertEquals(listOf(HistoryDay.Today), viewModel.state.value.sections.map { it.day })
    }

    @Test
    fun `the week runs Monday to Sunday and counts only its own days`() {
        at(MONDAY - 1)
        repository.recordWorkout(tabata, tabata.totalSeconds)
        at(MONDAY)
        repository.recordWorkout(tabata, tabata.totalSeconds)
        repository.recordWorkout(tabata, elapsedSeconds = 60)
        at(TODAY)

        val state = viewModel.state.value
        assertEquals(2, state.weekCount)
        assertEquals("5:00", state.summary.time)
        assertEquals((1..7).toList(), state.week.map { it.weekday })
        assertEquals(listOf(true, false, false, false, false, false, false), state.week.map { it.seconds > 0 })
        assertEquals(listOf(false, false, false, true, false, false, false), state.week.map { it.today })
        assertEquals(DayState.Done, state.week.first().state)
    }

    @Test
    fun `calories show only with a weight`() {
        repository.updateAppSettings { it.copy(body = it.body.copy(weightKg = null)) }
        repository.recordWorkout(tabata, tabata.totalSeconds, workSeconds = 160)
        assertNull(viewModel.state.value.summary.calories)
        assertNull(viewModel.state.value.sections.single().items.single().calories)

        repository.updateAppSettings { it.copy(body = BodySettings(weightKg = 70.0, effort = Effort.Hard)) }
        val expected = estimateCalories(160, tabata.totalSeconds - 160, 70.0, Effort.Hard)
        assertEquals(expected, viewModel.state.value.sections.single().items.single().calories)
        assertEquals(expected, viewModel.state.value.summary.calories)
    }

    @Test
    fun `a weekly plan is up next and its workout reads as planned once done`() {
        at(MONDAY)
        repository.planWeekly(hiit, setOf(4, 5))
        at(TODAY)
        val before = viewModel.state.value
        assertEquals(listOf(TODAY, TODAY + 1), before.upNext.map { it.epochDay })
        assertEquals(listOf(HistoryDay.Today, HistoryDay.Tomorrow), before.upNext.map { it.day })
        assertEquals(DayState.Planned, before.week[3].state)
        assertTrue(before.hasPlans)
        assertTrue(!before.isEmpty)

        repository.recordWorkout(hiit.config, hiit.config.totalSeconds)
        val after = viewModel.state.value
        assertEquals(listOf(TODAY + 1), after.upNext.map { it.epochDay })
        assertTrue(after.sections.single().items.single().planned)
        assertEquals(1, after.summary.plannedDone)
    }

    @Test
    fun `the goal and streak show once a goal is set`() {
        at(MONDAY - 7)
        repository.recordWorkout(tabata, tabata.totalSeconds)
        at(TODAY)
        repository.recordWorkout(tabata, tabata.totalSeconds)
        assertNull(viewModel.state.value.goal)
        assertNull(viewModel.state.value.streak)

        repository.updateAppSettings { it.copy(weeklyGoal = 1) }
        assertEquals(1, viewModel.state.value.goal)
        assertEquals(Streak(current = 2, best = 2), viewModel.state.value.streak)
    }

    @Test
    fun `the month moves back and forth and its range sums its days`() {
        at(epochDayOf(2026, 9, 30))
        repository.recordWorkout(tabata, elapsedSeconds = 60)
        at(TODAY)
        repository.recordWorkout(tabata, elapsedSeconds = 120)
        viewModel.setRange(TrainingRange.Month)
        val october = viewModel.state.value
        assertEquals("10/2026", october.month.title)
        assertTrue(october.month.current)
        assertEquals(1, october.summary.count)
        assertEquals(35, october.month.cells.size)

        viewModel.shiftMonth(-1)
        val september = viewModel.state.value
        assertEquals("9/2026", september.month.title)
        assertEquals(false, september.month.current)
        assertEquals("1:00", september.summary.time)

        viewModel.setRange(TrainingRange.AllTime)
        assertEquals(2, viewModel.state.value.summary.count)
        assertEquals("day ${epochDayOf(2026, 9, 30)}", viewModel.state.value.summary.since)
    }

    @Test
    fun `a day opens with its plans and a one-off can be removed`() {
        repository.planOnce(hiit, TODAY + 2)
        viewModel.openDay(TODAY + 2)
        val sheet = requireNotNull(viewModel.state.value.daySheet)
        assertEquals(HistoryDay.On("day ${TODAY + 2}"), sheet.day)
        val plan = sheet.plans.single()
        assertEquals(PlanState.Open, plan.state)
        assertEquals(false, plan.weekly)

        viewModel.removePlanned(plan.planId)
        assertEquals(emptyList(), viewModel.state.value.daySheet?.plans)
        assertEquals(emptyList(), repository.planned.value.filter { it.schedule == PlanSchedule.Once(TODAY + 2) })
        viewModel.openDay(null)
        assertNull(viewModel.state.value.daySheet)
    }

    @Test
    fun `starting a plan puts its shape on the Timer`() {
        viewModel.putOnTimer(hiit.name, hiit.config)
        assertEquals(hiit.config.workSeconds, repository.config.value.workSeconds)
        assertEquals(hiit.config.rounds, repository.config.value.rounds)
    }

    @Test
    fun `clearing empties the history`() {
        repository.recordWorkout(IntervalConfig(), elapsedSeconds = 60)
        viewModel.clear()
        assertTrue(viewModel.state.value.isEmpty)
    }

    @Test
    fun `the headings and the week move on at midnight with the screen open`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        // Sunday night, then Monday: a new week.
        val sunday = MONDAY + 6
        clock = sunday * DAY_MILLIS + DAY_MILLIS - 1_000
        repository.recordWorkout(tabata, tabata.totalSeconds)
        val viewModel = HistoryViewModel(repository, bells, FixedTimestampFormatter, clock = { clock })
        assertEquals(listOf(HistoryDay.Today), viewModel.state.value.sections.map { it.day })
        assertEquals(1, viewModel.state.value.weekCount)

        clock = (sunday + 1) * DAY_MILLIS
        advanceTimeBy(60_001)

        assertEquals(listOf(HistoryDay.Yesterday), viewModel.state.value.sections.map { it.day })
        assertEquals(0, viewModel.state.value.weekCount)
        assertEquals(sunday + 1, viewModel.state.value.week.first().epochDay)
        viewModel.viewModelScope.cancel()
    }
}
