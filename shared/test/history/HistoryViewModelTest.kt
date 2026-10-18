@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.history

import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.fakes.DAY_MILLIS
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.builtInPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val DAY = DAY_MILLIS
private val tabata = builtInPresets.first().config

class HistoryViewModelTest : MainDispatcherTest() {
    private var clock = 30 * DAY
    private val repository = FakeStorage().repository(now = { clock })
    private val viewModel by lazy { HistoryViewModel(repository, FixedTimestampFormatter, clock = { clock }) }

    @Test
    fun `workouts are grouped by day newest first and name their preset`() {
        val custom = IntervalConfig(prepSeconds = 0, workSeconds = 25, restSeconds = 5, rounds = 4)
        clock = 20 * DAY
        repository.recordWorkout(tabata, tabata.totalSeconds)
        clock = 29 * DAY
        repository.recordWorkout(custom, custom.totalSeconds)
        repository.recordWorkout(tabata, elapsedSeconds = 90, endedEarly = true)
        clock = 30 * DAY

        val sections = viewModel.state.value.sections
        assertEquals(listOf(HistoryDay.Yesterday, HistoryDay.On("day 20")), sections.map { it.day })
        assertEquals(listOf(29L, 20L), sections.map { it.dayNumber })
        val (ended, customRow) = sections[0].items
        assertEquals(HistoryItem("Tabata", tabata, endedEarly = true, time = "at ${29 * DAY}", total = "1:30"), ended)
        assertEquals(HistoryItem(null, custom, endedEarly = false, time = "at ${29 * DAY}", total = "1:55"), customRow)
    }

    @Test
    fun `today's workouts are headed Today`() {
        repository.recordWorkout(tabata, tabata.totalSeconds)
        assertEquals(listOf(HistoryDay.Today), viewModel.state.value.sections.map { it.day })
    }

    @Test
    fun `the week tally counts only the last seven days`() {
        clock = 20 * DAY
        repository.recordWorkout(tabata, tabata.totalSeconds)
        clock = 29 * DAY
        repository.recordWorkout(tabata, tabata.totalSeconds)
        repository.recordWorkout(tabata, elapsedSeconds = 60)
        clock = 30 * DAY

        val state = viewModel.state.value
        assertEquals(2, state.weekCount)
        assertEquals("5:00", state.weekTotal)
    }

    @Test
    fun `the week chart has a bar per day ending today`() {
        clock = 24 * DAY + DAY / 2
        repository.recordWorkout(tabata, tabata.totalSeconds)
        clock = 30 * DAY + DAY / 2
        repository.recordWorkout(tabata, elapsedSeconds = 60)
        repository.recordWorkout(tabata, elapsedSeconds = 30)

        val week = viewModel.state.value.week
        // Day 30 is a Wednesday (30 % 7 == 2), so the chart runs Thursday to Wednesday.
        assertEquals(listOf(4, 5, 6, 7, 1, 2, 3), week.map { it.weekday })
        assertEquals(listOf(240, 0, 0, 0, 0, 0, 90), week.map { it.seconds })
        assertEquals(listOf(false, false, false, false, false, false, true), week.map { it.today })
        assertEquals(240, viewModel.state.value.weekMaxSeconds)
    }

    @Test
    fun `a week with no workouts has no time on any day`() {
        clock = 20 * DAY
        repository.recordWorkout(IntervalConfig(), elapsedSeconds = 60)
        clock = 30 * DAY
        assertTrue(viewModel.state.value.week.none { it.seconds > 0 })
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
        clock = 30 * DAY + DAY - 1_000
        repository.recordWorkout(tabata, tabata.totalSeconds)
        val viewModel = HistoryViewModel(repository, FixedTimestampFormatter, clock = { clock })
        assertEquals(listOf(HistoryDay.Today), viewModel.state.value.sections.map { it.day })

        clock = 31 * DAY
        advanceTimeBy(60_001)

        assertEquals(listOf(HistoryDay.Yesterday), viewModel.state.value.sections.map { it.day })
        assertEquals(
            listOf(false, false, false, false, false, true, false),
            viewModel.state.value.week.map {
                it.seconds >
                    0
            },
        )
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `a week tally fills days without workouts with zero`() {
        val week = weekTally(secondsByDay = mapOf(10L to 60, 4L to 30, 3L to 99), today = 10, todayWeekday = 1)
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 1), week.map { it.weekday })
        assertEquals(listOf(30, 0, 0, 0, 0, 0, 60), week.map { it.seconds })
    }
}
