@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.data

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.fakes.deferredStorageScope
import dev.justinsmith.interval.fakes.noonOf
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Thursday 15 October 2026, at noon: the week began on Monday the 12th, TODAY - 3.

class WeekStandingSourceTest {
    private var now = noonOf(TODAY)
    private val storage = FakeStorage()
    private val lift = FakeLiftStorage()

    private fun TestScope.source(repository: IntervalRepository = storage.repository(now = { now })) =
        WeekStandingSource(
            repository,
            lift.repository(now = { now }).sessions,
            FixedTimestampFormatter,
            { now },
            unconfined(),
        )

    /**
     * A scope the test ends with: the source checks the day on a timer for as long as it lives, and `runTest`
     * would run that timer on forever as it drained the scheduler.
     */
    private fun TestScope.unconfined() =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `nothing until both sides have read their storage`() = runTest {
        // The interval side reads its storage on the scheduler, so nothing has landed until the test moves it.
        val repository = storage.repository(now = { now }, scope = deferredStorageScope())
        val source = source(repository)
        assertNull(source.standing.value)

        runCurrent()
        assertEquals(WeekStanding(count = 0, goal = null, streak = 0), source.standing.value)
    }

    @Test
    fun `a workout and a session this week count and last week's do not`() = runTest {
        val repository = storage.repository(now = { now })
        val source = source(repository)
        runCurrent()

        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 300)
        lift.finished("b1", "Swings", noonOf(TODAY - 1), noonOf(TODAY - 1) + 600_000, 24.0 to 10)
        lift.finished("b0", "Swings", noonOf(TODAY - 7), noonOf(TODAY - 7) + 600_000, 24.0 to 10)
        runCurrent()
        assertEquals(2, source.standing.value?.count)
    }

    @Test
    fun `a sport day logged this week counts and comes off again`() = runTest {
        val repository = storage.repository(now = { now })
        val source = source(repository)
        runCurrent()

        repository.history.logSport(Sport.Bjj, TODAY)
        repository.history.logSport(Sport.Climbing, TODAY - 7)
        runCurrent()
        assertEquals(1, source.standing.value?.count)

        repository.history.unlogSport(Sport.Bjj, TODAY)
        runCurrent()
        assertEquals(0, source.standing.value?.count)
    }

    @Test
    fun `the goal set on Plan and the streak of weeks that met it`() = runTest {
        val repository = storage.repository(now = { now })
        repository.settings.updateAppSettings { it.copy(weeklyGoal = 2) }
        lift.finished("b0", "Swings", noonOf(TODAY - 7), noonOf(TODAY - 7) + 600_000, 24.0 to 10)
        lift.finished("b1", "Swings", noonOf(TODAY - 8), noonOf(TODAY - 8) + 600_000, 24.0 to 10)
        val source = source(repository)
        runCurrent()
        assertEquals(WeekStanding(count = 0, goal = 2, streak = 1), source.standing.value)

        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 300)
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 300)
        runCurrent()
        assertEquals(WeekStanding(count = 2, goal = 2, streak = 2), source.standing.value)
    }

    @Test
    fun `the week turns at Monday's midnight`() = runTest {
        val repository = storage.repository(now = { now })
        val source = source(repository)
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 300)
        runCurrent()
        assertEquals(1, source.standing.value?.count)

        now = noonOf(TODAY + 4)
        advanceTimeBy(DAY_CHECK_MILLIS)
        runCurrent()
        assertEquals(0, source.standing.value?.count)
    }

    @Test
    fun `a setting that leaves the goal alone changes nothing`() = runTest {
        val repository = storage.repository(now = { now })
        val source = source(repository)
        val seen = mutableListOf<WeekStanding?>()
        source.standing.onEach { seen += it }.launchIn(unconfined())
        runCurrent()

        repository.settings.updateAppSettings { it.copy(reminderMinuteOfDay = 600) }
        runCurrent()
        assertEquals(listOf(WeekStanding(0, null, 0)), seen.filterNotNull())
    }
}
