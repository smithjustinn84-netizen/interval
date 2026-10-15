@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.history

import dev.justinsmith.interval.data.FakeKeyValueStore
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.platform.TimestampFormatter
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

private const val DAY = 24L * 60 * 60 * 1000

/** Days are numbered from the epoch in UTC, so tests don't depend on a time zone. Day 0 is a Monday. */
private object DayNumbers : TimestampFormatter {
    override fun day(epochMillis: Long, nowMillis: Long): String = "day ${(nowMillis - epochMillis) / DAY}"

    override fun time(epochMillis: Long): String = "at $epochMillis"

    override fun dayNumber(epochMillis: Long): Long = epochMillis / DAY

    override fun weekday(epochMillis: Long): Int = (dayNumber(epochMillis) % 7).toInt() + 1
}

class HistoryViewModelTest {
    private var clock = 30 * DAY
    private val repository = IntervalRepository(FakeKeyValueStore(), now = { clock })
    private val viewModel by lazy { HistoryViewModel(repository, DayNumbers, now = { clock }) }

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
        repository.recordWorkout(builtInPresets.first().config, elapsedSeconds = 90)
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
    }

    @Test
    fun `clearing empties the history`() {
        repository.recordWorkout(IntervalConfig())
        viewModel.clear()
        assertTrue(viewModel.state.value.isEmpty)
    }
}
