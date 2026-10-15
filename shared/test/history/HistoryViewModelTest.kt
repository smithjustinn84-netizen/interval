@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.history

import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.fakes.DAY_MILLIS
import dev.justinsmith.interval.fakes.FakeKeyValueStore
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.builtInPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val DAY = DAY_MILLIS

class HistoryViewModelTest {
    private var clock = 30 * DAY
    private val repository = IntervalRepository(FakeKeyValueStore(), now = { clock })
    private val viewModel by lazy { HistoryViewModel(repository, FixedTimestampFormatter, now = { clock }) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `workouts are grouped by day newest first and name their preset`() {
        val custom = IntervalConfig(prepSeconds = 0, workSeconds = 25, restSeconds = 5, rounds = 4)
        clock = 20 * DAY
        repository.recordWorkout(builtInPresets.first().config)
        clock = 29 * DAY
        repository.recordWorkout(custom)
        repository.recordWorkout(builtInPresets.first().config, elapsedSeconds = 90, endedEarly = true)
        clock = 30 * DAY

        val sections = viewModel.state.value.sections
        assertEquals(listOf("day 1", "day 10"), sections.map { it.day })
        val (ended, customRow) = sections[0].items
        assertEquals("Tabata", ended.title)
        assertEquals("20s on · 10s off · ×8 · ended early · at ${29 * DAY}", ended.detail)
        assertEquals("1:30", ended.total)
        assertEquals("25s on · 5s off · ×4", customRow.title)
        assertEquals("at ${29 * DAY}", customRow.detail)
    }

    @Test
    fun `the week tally counts only the last seven days`() {
        clock = 20 * DAY
        repository.recordWorkout(builtInPresets.first().config)
        clock = 29 * DAY
        repository.recordWorkout(builtInPresets.first().config)
        repository.recordWorkout(builtInPresets.first().config, elapsedSeconds = 60)
        clock = 30 * DAY

        val state = viewModel.state.value
        assertEquals(2, state.weekCount)
        assertEquals("5:00", state.weekTotal)
    }

    @Test
    fun `the week chart has a bar per day ending today`() {
        clock = 24 * DAY + DAY / 2
        repository.recordWorkout(builtInPresets.first().config)
        clock = 30 * DAY + DAY / 2
        repository.recordWorkout(builtInPresets.first().config, elapsedSeconds = 60)
        repository.recordWorkout(builtInPresets.first().config, elapsedSeconds = 30)

        val week = viewModel.state.value.week
        // Day 30 is a Wednesday (30 % 7 == 2), so the chart runs Thursday to Wednesday.
        assertEquals("TFSSMTW", week.joinToString("") { it.label })
        assertEquals(listOf(240, 0, 0, 0, 0, 0, 90), week.map { it.seconds })
        assertEquals(listOf(false, false, false, false, false, false, true), week.map { it.today })
        assertEquals(240, viewModel.state.value.weekMaxSeconds)
        assertEquals(
            listOf("Thursday", "Friday", "Saturday", "Sunday", "Monday", "Tuesday", "Today"),
            week.map { it.name },
        )
        // 90 seconds rounds to 2 minutes.
        assertEquals(
            "Time per day this week: Thursday 4 minutes, Today 2 minutes",
            viewModel.state.value.weekDescription,
        )
    }

    @Test
    fun `the week chart reads each day's time in seconds or minutes`() {
        clock = 29 * DAY + DAY / 2
        repository.recordWorkout(builtInPresets.first().config, elapsedSeconds = 45)
        clock = 30 * DAY + DAY / 2
        repository.recordWorkout(builtInPresets.first().config, elapsedSeconds = 1)
        assertEquals(
            "Time per day this week: Tuesday 45 seconds, Today 1 second",
            viewModel.state.value.weekDescription,
        )
    }

    @Test
    fun `a week with no workouts says so`() {
        clock = 20 * DAY
        repository.recordWorkout(IntervalConfig())
        clock = 30 * DAY
        assertEquals("No workouts in the last 7 days", viewModel.state.value.weekDescription)
    }

    @Test
    fun `clearing empties the history`() {
        repository.recordWorkout(IntervalConfig())
        viewModel.clear()
        assertTrue(viewModel.state.value.isEmpty)
    }
}
