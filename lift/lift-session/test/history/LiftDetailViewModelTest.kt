@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.lift.history

import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.RecordingHealthStore
import dev.justinsmith.interval.platform.HealthStatus
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.domain.PersonalRecord
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.fakes.FakeIntervalActivity
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val DAY = 24L * 60 * 60 * 1000

/** A finished session's page: the session, when it was, what it set, and its deletion with undo. */
class LiftDetailViewModelTest {
    private val storage = FakeLiftStorage()
    private val repository = storage.repository()
    private val health = RecordingHealthStore(HealthStatus.Connected)
    private val intervals = FakeIntervalActivity()

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun viewModel(uid: String) = LiftDetailViewModel(
        uid,
        repository.sessions,
        repository.sets,
        repository.gear,
        repository.programs,
        FixedTimestampFormatter,
        health,
        intervals,
    )

    @Test
    fun `the session shows when it finished and the records it set over those before it`() {
        storage.finished("a", "Swings", 0, 60_000, 16.0 to 10)
        storage.finished("b", "Heavier swings", DAY, DAY + 60_000, 24.0 to 5, 16.0 to 10)

        val state = viewModel("b").state.value

        assertEquals("Heavier swings", state.session?.title)
        assertEquals("day 1", state.date)
        assertEquals("at ${DAY + 60_000}", state.time)
        assertEquals(listOf(24.0), state.records.map { (it as PersonalRecord.Weighted).heaviestKg })
        assertNull(state.program)
    }

    @Test
    fun `a session that beat nothing set no record`() {
        storage.finished("a", "Swings", 0, 60_000, 24.0 to 10)
        storage.finished("b", "Lighter swings", DAY, DAY + 60_000, 16.0 to 5)

        assertEquals(emptyList(), viewModel("b").state.value.records)
    }

    @Test
    fun `a day of a program names the program`() {
        val key = ProgramKey.BuiltIn(ProgramId.DailySwingsGetUps)
        storage.finished("a", "Day 1", 0, 60_000, 16.0 to 10, program = key)

        assertEquals(key, viewModel("a").state.value.program?.key)
    }

    @Test
    fun `deleting takes the session away and restoring brings it back`() {
        storage.finished("a", "Swings", 0, 60_000, 16.0 to 10)
        val viewModel = viewModel("a")

        viewModel.delete()
        assertNull(viewModel.state.value.session)

        viewModel.restore()
        assertEquals("Swings", viewModel.state.value.session?.title)
    }

    @Test
    fun `a session never there is an empty page`() {
        assertEquals(LiftDetailUiState(), viewModel("gone").state.value)
    }

    @Test
    fun `the page reads a watch's heart rate over the session and keeps it`() {
        storage.finished("a", "Swings", 0, 60_000, 16.0 to 10)
        health.heartRate = HeartRate(averageBpm = 128, peakBpm = 160)
        val viewModel = viewModel("a")

        viewModel.readHeartRate()

        assertEquals(listOf(0L..60_000L), health.heartRateReads)
        assertEquals(128, viewModel.state.value.session?.averageBpm)
        assertEquals(160, viewModel.state.value.session?.peakBpm)
        viewModel.readHeartRate()
        assertEquals(1, health.heartRateReads.size)
    }

    @Test
    fun `with none in the store the session shows none`() {
        storage.finished("a", "Swings", 0, 60_000, 16.0 to 10)
        viewModel("a").readHeartRate()
        assertNull(viewModel("a").state.value.session?.averageBpm)
    }

    @Test
    fun `editing effort and note updates the finished session`() {
        storage.finished("a", "Swings", 0, 60_000, 16.0 to 10)
        val viewModel = viewModel("a")

        viewModel.setEffort(PerceivedEffort.Hard)
        assertEquals(PerceivedEffort.Hard, viewModel.state.value.session?.effort)

        viewModel.setNote("Crisp hinge")
        assertEquals("Crisp hinge", viewModel.state.value.session?.note)
        assertEquals(PerceivedEffort.Hard, viewModel.state.value.session?.effort)

        viewModel.setEffort(null)
        assertNull(viewModel.state.value.session?.effort)
        assertEquals("Crisp hinge", viewModel.state.value.session?.note)

        viewModel.setNote("   ")
        assertNull(viewModel.state.value.session?.note)
    }

    @Test
    fun `editing a logged set updates its load and reps and recalculates records`() {
        var now = 0L
        val timed = storage.repository(now = { now })
        storage.finished("a", "Swings", 0, 60_000, 16.0 to 10)
        now = DAY
        timed.sessions.startSession()
        timed.sets.logSet(BuiltInExercise.TwoHandSwing, Load.Bell(16.0), reps = 5, side = null)
        now = DAY + 60_000
        timed.sessions.finishSession("Swings")
        val uid = timed.sessions.sessions.value.first().uid
        val viewModel = LiftDetailViewModel(
            uid,
            timed.sessions,
            timed.sets,
            timed.gear,
            timed.programs,
            FixedTimestampFormatter,
            health,
            intervals,
        )
        assertEquals(emptyList(), viewModel.state.value.records)

        val set = requireNotNull(viewModel.state.value.session?.sets?.single())
        viewModel.updateSet(set.copy(load = Load.Bell(24.0), reps = 10))

        val updated = requireNotNull(viewModel.state.value.session?.sets?.single())
        assertEquals(24.0, updated.load.kg)
        assertEquals(10, updated.reps)
        assertEquals(listOf(24.0), viewModel.state.value.records.map { (it as PersonalRecord.Weighted).heaviestKg })
    }

    @Test
    fun `removing a logged set takes it off and restoring brings it back while keeping the last set`() {
        repository.sessions.startSession()
        repository.sets.logSet(BuiltInExercise.TwoHandSwing, Load.Bell(16.0), reps = 10, side = null)
        repository.sets.logSet(BuiltInExercise.TwoHandSwing, Load.Bell(24.0), reps = 5, side = null)
        repository.sessions.finishSession("Swings")
        val uid = repository.sessions.sessions.value.single().uid
        val viewModel = viewModel(uid)
        val second = viewModel.state.value.session!!.sets[1]

        val removed = viewModel.removeSet(second.id)
        assertEquals(second, removed)
        assertEquals(1, viewModel.state.value.session?.sets?.size)

        // The last remaining set cannot be removed.
        val remaining = viewModel.state.value.session!!.sets.single()
        assertNull(viewModel.removeSet(remaining.id))
        assertEquals(1, viewModel.state.value.session?.sets?.size)

        viewModel.restoreSet(second)
        assertEquals(2, viewModel.state.value.session?.sets?.size)
    }
}
