package dev.justinsmith.interval.data

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.lift.data.IntervalWorkout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RepositoryIntervalActivityTest {
    private val storage = FakeStorage()
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun `Lift sees the workouts already logged and each one finished after`() {
        val repository = storage.repository(WallClock { 1_000L })
        repository.recordWorkout(IntervalConfig(), elapsedSeconds = 300)
        val activity = RepositoryIntervalActivity(repository, scope)
        val first = activity.workouts.value.single()
        assertEquals(1_000L to 300, first.finishedAtEpochMillis to first.elapsedSeconds)

        repository.recordWorkout(IntervalConfig(), elapsedSeconds = 600)
        assertEquals(setOf(300, 600), activity.workouts.value.map(IntervalWorkout::elapsedSeconds).toSet())
    }

    @Test
    fun `Lift sees the weekly goal set on Plan`() {
        val repository = storage.repository()
        val activity = RepositoryIntervalActivity(repository, scope)
        assertNull(activity.weeklyGoal.value)

        repository.updateAppSettings { it.copy(weeklyGoal = 3) }
        assertEquals(3, activity.weeklyGoal.value)
    }
}
