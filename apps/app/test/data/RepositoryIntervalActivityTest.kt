package dev.justinsmith.interval.data

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.model.SnatchCount
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.snatchIntervals
import dev.justinsmith.lift.data.IntervalWorkout
import dev.justinsmith.lift.data.SportDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RepositoryIntervalActivityTest {
    private val storage = FakeStorage()
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun `Lift sees the workouts already logged and each one finished after`() {
        val repository = storage.repository(WallClock { 1_000L })
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 300)
        val activity = RepositoryIntervalActivity(repository.history, repository.settings, repository.presets, scope)
        val first = activity.workouts.value.single()
        assertEquals(1_000L to 300, first.finishedAtEpochMillis to first.elapsedSeconds)

        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 600)
        assertEquals(setOf(300, 600), activity.workouts.value.map(IntervalWorkout::elapsedSeconds).toSet())
    }

    @Test
    fun `Lift sees the days a sport was logged on`() {
        val repository = storage.repository()
        repository.history.logSport(Sport.Bjj, epochDay = 20_744)
        val activity = RepositoryIntervalActivity(repository.history, repository.settings, repository.presets, scope)
        assertEquals(listOf(SportDay(20_744L, grappling = true)), activity.sportDays.value)

        repository.history.logSport(Sport.Climbing, epochDay = 20_746)
        repository.history.unlogSport(Sport.Bjj, epochDay = 20_744)
        assertEquals(listOf(SportDay(20_746L, grappling = false)), activity.sportDays.value)
    }

    @Test
    fun `Lift sees the weekly goal set on Plan`() {
        val repository = storage.repository()
        val activity = RepositoryIntervalActivity(repository.history, repository.settings, repository.presets, scope)
        assertNull(activity.weeklyGoal.value)

        repository.settings.updateAppSettings { it.copy(weeklyGoal = 3) }
        assertEquals(3, activity.weeklyGoal.value)
    }

    @Test
    fun `Lift sees a counted 15-15's snatches set by set with the preset's name`() {
        val repository = storage.repository(WallClock { 1_195_000L })
        val uid = repository.history.recordWorkout(snatchIntervals.config, elapsedSeconds = 1_195)
        val activity = RepositoryIntervalActivity(repository.history, repository.settings, repository.presets, scope)
        assertNull(activity.workouts.value.single().snatches)

        repository.history.countSnatches(uid, SnatchCount(8, 16.0))
        val snatches = assertNotNull(activity.workouts.value.single().snatches)
        assertEquals(8 to 16.0, snatches.perSet to snatches.bellKg)
        assertEquals("15:15 MVO2", snatches.source)
        assertEquals(40, snatches.setEnds.size)
        assertEquals(25_000L, snatches.setEnds.first())
    }
}
