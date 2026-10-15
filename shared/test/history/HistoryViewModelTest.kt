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

/** Days are numbered back from now, so tests don't depend on a time zone. */
private object DayNumbers : TimestampFormatter {
    override fun day(epochMillis: Long, nowMillis: Long): String = "day ${(nowMillis - epochMillis) / DAY}"

    override fun time(epochMillis: Long): String = "at $epochMillis"
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
        assertEquals("Tabata · 20s on · 10s off · ×8 · ended early", ended.title)
        assertEquals("1:30", ended.total)
        assertEquals("25s on · 5s off · ×4", customRow.title)
        assertEquals("at ${29 * DAY}", customRow.time)
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
    fun `clearing empties the history`() {
        repository.recordWorkout(IntervalConfig())
        viewModel.clear()
        assertTrue(viewModel.state.value.isEmpty)
    }
}
