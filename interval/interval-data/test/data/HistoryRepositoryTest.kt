@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.data

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.SequentialIds
import dev.justinsmith.interval.fakes.saveCurrentAsPreset
import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.estimateCalories
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The finished workouts through [HistoryRepository]: recording, reflecting, deleting and tombstones. */
class HistoryRepositoryTest {
    private val storage = FakeStorage()
    private var clock = 1_000L
    private fun repository() = storage.repository(now = { clock })

    @Test
    fun `each workout recorded is stored under a new id from the generator`() {
        val repository = storage.repository(ids = SequentialIds())

        assertEquals("id-1", repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 240))
        assertEquals("id-2", repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 300))
    }

    @Test
    fun `a deleted workout restored is back in the history`() {
        val repository = repository()
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 240)
        val id = requireNotNull(repository.history.history.value.single().id)
        repository.history.deleteWorkout(id)
        assertEquals(emptyList(), repository.history.history.value)
        repository.history.restoreWorkout(id)
        assertEquals(listOf(240), repository.history.history.value.map { it.elapsedSeconds })
    }

    @Test
    fun `history is newest first keeps every workout and clears`() {
        val repository = repository()
        repeat(50) {
            clock += 1
            repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = it)
        }
        val history = repository.history.history.value
        assertEquals(50, history.size)
        assertEquals(clock, history.first().finishedAtEpochMillis)
        repository.history.clearHistory()
        assertEquals(emptyList(), repository().history.history.value)
    }

    @Test
    fun `a workout keeps its time and whether it ended early`() {
        repository().history.recordWorkout(IntervalConfig(), elapsedSeconds = 42)
        repository().history.recordWorkout(IntervalConfig(), elapsedSeconds = 7, endedEarly = true)
        val (early, full) = repository().history.history.value
        assertEquals(42, full.elapsedSeconds)
        assertFalse(full.endedEarly)
        assertEquals(7, early.elapsedSeconds)
        assertTrue(early.endedEarly)
    }

    @Test
    fun `a workout keeps its work time and older ones have none`() {
        repository().history.recordWorkout(IntervalConfig(), elapsedSeconds = 300, workSeconds = 240)
        repository().history.recordWorkout(IntervalConfig(), elapsedSeconds = 300)
        val (older, newer) = repository().history.history.value
        assertEquals(240, newer.workActiveSeconds)
        assertEquals(null, older.workActiveSeconds)
    }

    @Test
    fun `a workout keeps when it began and the first heart rate found for it`() {
        val uid = repository().history.recordWorkout(
            IntervalConfig(),
            elapsedSeconds = 300,
            startedAtEpochMillis = 1_000,
        )
        repository().history.keepHeartRate(uid, HeartRate(averageBpm = 140, peakBpm = 170))
        repository().history.keepHeartRate(uid, HeartRate(averageBpm = 100, peakBpm = 120))
        val entry = repository().history.history.value.single()
        assertEquals(1_000L, entry.startedAtEpochMillis)
        assertEquals(140 to 170, entry.averageBpm to entry.peakBpm)

        repository().history.recordWorkout(IntervalConfig(), elapsedSeconds = 300)
        val id = requireNotNull(repository().history.history.value.first { it.startedAtEpochMillis == null }.id)
        repository().history.keepHeartRateOf(id, HeartRate(averageBpm = 120, peakBpm = 150))
        assertEquals(120, repository().history.history.value.first { it.id == id }.averageBpm)
    }

    @Test
    fun `a history entry has calories only with a weight`() {
        val config = IntervalConfig(prepSeconds = 0, workSeconds = 60, restSeconds = 60, rounds = 2)
        val entry = HistoryEntry(0, config, elapsedSeconds = 180, endedEarly = false, workActiveSeconds = 120)
        assertEquals(null, entry.calories(BodySettings(weightKg = null)))
        val body = BodySettings(weightKg = 80.0, effort = Effort.Hard)
        assertEquals(estimateCalories(120, 60, 80.0, Effort.Hard), entry.calories(body))
        // Recorded before work time was kept: the plan's work time stands in.
        assertEquals(entry.calories(body), entry.copy(workActiveSeconds = null).calories(body))
    }

    @Test
    fun `presets and history keep their activity`() {
        val config = IntervalConfig(activity = Activity.JumpRope)
        repository().settings.updateConfig { config }
        repository().saveCurrentAsPreset("Rope")
        repository().history.recordWorkout(config, elapsedSeconds = 60)
        assertEquals(Activity.JumpRope, repository().presets.customPresets.value.single().config.activity)
        assertEquals(Activity.JumpRope, repository().history.history.value.single().config.activity)
        assertEquals(Activity.JumpRope, repository().settings.config.value.activity)
    }

    @Test
    fun `one workout can be deleted by its id`() {
        val repository = repository()
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 1)
        clock += 1
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 2)
        val (newer, older) = repository.history.history.value
        repository.history.deleteWorkout(requireNotNull(older.id))
        assertEquals(listOf(newer), repository().history.history.value)
    }

    @Test
    fun `a deleted workout stays stored as a tombstone and leaves the history`() = runTest {
        val repository = repository()
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 1)
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 2)
        val kept = storage.history.all().first { it.elapsedSeconds == 2 }
        clock = 5_000
        repository.history.deleteWorkout(storage.history.all().first { it.elapsedSeconds == 1 }.id)

        val (deleted, untouched) = storage.history.all()
        assertTrue(deleted.deleted)
        assertEquals(5_000, deleted.updatedAtEpochMillis)
        assertEquals(kept, untouched)
        assertEquals(listOf(2), repository().history.history.value.map { it.elapsedSeconds })
    }

    @Test
    fun `clearing the history leaves every workout as a tombstone`() = runTest {
        val repository = repository()
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 1)
        repository.history.deleteWorkout(requireNotNull(repository.history.history.value.single().id))
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 2)
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 3)
        clock = 9_000
        repository.history.clearHistory()

        val rows = storage.history.all()
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.deleted })
        // One already deleted keeps the time it was deleted.
        assertEquals(listOf(1_000L, 9_000L, 9_000L), rows.map { it.updatedAtEpochMillis })
        assertEquals(emptyList(), repository().history.history.value)
    }

    @Test
    fun `each new workout gets a uid of its own and the time it was recorded`() = runTest {
        val repository = repository()
        repeat(20) {
            clock += 1
            repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = it)
        }
        val rows = storage.history.all()
        val uids = rows.map { it.uid }
        assertEquals(20, uids.toSet().size)
        assertTrue(uids.all { it.length == 32 && it.all { c -> c in "0123456789abcdef" } })
        assertEquals(rows.map { it.finishedAtEpochMillis }, rows.map { it.updatedAtEpochMillis })
    }

    @Test
    fun `a sport is logged once a day and comes off again`() {
        val repository = repository()
        repository.history.logSport(Sport.Bjj, epochDay = 20_744)
        clock += 500
        repository.history.logSport(Sport.Bjj, epochDay = 20_744)
        repository.history.logSport(Sport.Custom("Judo"), epochDay = 20_744)
        repository.history.logSport(Sport.Custom("Yoga"), epochDay = 20_744)

        val logged = repository().history.sportDays.value
        assertEquals(setOf(Sport.Bjj, Sport.Custom("Judo"), Sport.Custom("Yoga")), logged.map { it.sport }.toSet())
        // The first log of the day stands: the second changed nothing.
        assertEquals(1_000L, logged.single { it.sport == Sport.Bjj }.loggedAtEpochMillis)

        repository.history.unlogSport(Sport.Custom("Judo"), epochDay = 20_744)
        repository.history.unlogSport(Sport.Bjj, epochDay = 20_745)
        assertEquals(
            setOf(Sport.Bjj, Sport.Custom("Yoga")),
            repository.history.sportDays.value.map { it.sport }.toSet(),
        )
    }

    @Test
    fun `sport days are the latest day first whenever they were logged`() {
        val repository = repository()
        repository.history.logSport(Sport.Climbing, epochDay = 20_746)
        repository.history.logSport(Sport.Bjj, epochDay = 20_744)
        repository.history.logSport(Sport.Running, epochDay = 20_745)

        assertEquals(listOf(20_746L, 20_745L, 20_744L), repository.history.sportDays.value.map { it.epochDay })
    }

    @Test
    fun `clearing the history clears the sport days with it`() {
        val repository = repository()
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 1)
        repository.history.logSport(Sport.Bjj, epochDay = 20_744)
        repository.history.clearHistory()

        assertEquals(emptyList(), repository().history.sportDays.value)
        assertEquals(emptyList(), repository().history.history.value)
    }

    @Test
    fun `a sport day of a sport this build does not know is left out`() = runTest {
        storage.sports.insert(
            listOf(
                SportLogEntity(epochDay = 20_744, sport = "fencing", name = "", loggedAtEpochMillis = 1),
                SportLogEntity(epochDay = 20_744, sport = "custom", name = " ", loggedAtEpochMillis = 1),
                SportLogEntity(epochDay = 20_744, sport = "climbing", name = "", loggedAtEpochMillis = 1),
            ),
        )
        assertEquals(listOf(Sport.Climbing), repository().history.sportDays.value.map { it.sport })
    }
}
