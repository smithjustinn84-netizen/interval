@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.lift.today

import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.lift.data.IntervalWorkout
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.data.SetEntity
import dev.justinsmith.lift.data.SportDay
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Challenge
import dev.justinsmith.lift.domain.DEFAULT_BAR_KG
import dev.justinsmith.lift.domain.GRAPPLING_GRIP_LOAD
import dev.justinsmith.lift.domain.IntervalSnatches
import dev.justinsmith.lift.domain.MuscleGroup
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.Readiness
import dev.justinsmith.lift.domain.barbellLifts
import dev.justinsmith.lift.fakes.FakeIntervalActivity
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.fakes.logSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TodayViewModelTest {
    private val storage = FakeLiftStorage()
    private val intervals = FakeIntervalActivity()
    private val repository = with(storage) {
        LiftRepository(
            sessions,
            bells,
            following,
            programs,
            exercises,
            bands,
            imports,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            clock = { 1_000L },
        )
    }

    private val main = UnconfinedTestDispatcher()

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(main)

    private fun today(clock: WallClock, computation: CoroutineDispatcher) = TodayViewModel(
        repository.sessions,
        repository.programs,
        repository.gear,
        repository.sets,
        intervals,
        FixedTimestampFormatter,
        clock,
        computation = computation,
    )

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun `a barbell lift never done suggests the bar the user owns and follows a change of it`() {
        val today =
            today(clock = { 1_000L }, main)
        val lift = barbellLifts.first() to false
        assertEquals(DEFAULT_BAR_KG, today.state.value.bells[lift])

        repository.gear.setBar(15.0)
        assertEquals(15.0, today.state.value.bells[lift])
    }

    @Test
    fun `the last session's today moves on at midnight with the screen open`() = runTest {
        val main = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        // Finished on epoch day 3: said by its time while that is today, by its date after.
        finished("last", day = 3, BuiltInExercise.GobletSquat)
        val finishedAt = 3 * DAY_MILLIS + 61_000
        var now = 4 * DAY_MILLIS - 1_000
        val today =
            today(clock = { now }, main)
        assertEquals("at $finishedAt", today.state.value.lastWhen)

        now = 4 * DAY_MILLIS
        advanceTimeBy(DAY_CHECK_MILLIS + 1)

        assertEquals("day 3", today.state.value.lastWhen)
        today.viewModelScope.cancel()
    }

    @Test
    fun `a BJJ day logged counts on the grip and a run does not`() {
        val today = today(clock = { 1_000L }, main)
        assertNull(today.state.value.grip)

        intervals.sportDays.value = listOf(SportDay(0, grappling = true), SportDay(-1, grappling = false))
        assertEquals(GRAPPLING_GRIP_LOAD, today.state.value.grip?.acute)
        assertEquals(Readiness.Normal, today.state.value.readiness.readiness)
    }

    @Test
    fun `15-15's counted snatches load the grip and set the snatch test's best`() {
        val today = today(clock = { 1_200_000L }, main)
        val ends = List(40) { 25_000L + it * 30_000L }

        intervals.workouts.value = listOf(
            IntervalWorkout("1", ends.last(), 1_195, IntervalSnatches(8, 16.0, ends, "15:15 MVO2")),
        )

        assertEquals(1_440.0, today.state.value.grip?.acute)
        assertEquals(
            Challenge.SnatchTest(80, fromIntervals = true, preset = "15:15 MVO2"),
            today.state.value.challenges.filterIsInstance<Challenge.SnatchTest>().single(),
        )
    }

    @Test
    fun `the balance counts the sessions finished in the last four weeks once two are`() {
        // Today is epoch day 100: day 73 is the window's first, day 72 just outside it.
        val now = 100 * DAY_MILLIS + 1_000
        finished("old", day = 72, BuiltInExercise.BarbellBenchPress)
        finished("first", day = 73, BuiltInExercise.GobletSquat)
        val today =
            today(clock = { now }, main)
        assertNull(today.state.value.balance)

        finished("second", day = 100, BuiltInExercise.GobletSquat)
        // An open session's sets wait until it finishes.
        repository.sessions.startSession(null)
        repository.sets.logSet(BuiltInExercise.BarbellBenchPress, 60.0, 5, null)

        val balance = today.state.value.balance!!
        assertEquals(2.0, balance.loads.single { it.group == MuscleGroup.Legs }.worked)
        assertEquals(0.0, balance.loads.single { it.group == MuscleGroup.Chest }.total)
    }

    @Test
    fun `the day to do says about how long it takes and longer with a longer rest`() {
        // Goblet squat and row: 3 sets of 10 squats, then 3 of 10 rows a side.
        repository.programs.follow(ProgramKey.BuiltIn(ProgramId.GobletSquatRow))
        val today = today(clock = { 1_000L }, main)
        assertEquals(10, today.state.value.dayMinutes)

        repository.sets.setRestSeconds(180)
        assertEquals(20, today.state.value.dayMinutes)
    }

    @Test
    fun `with no program followed no day says how long it takes`() {
        assertNull(today(clock = { 1_000L }, main).state.value.dayMinutes)
    }

    private fun finished(uid: String, day: Long, exercise: BuiltInExercise) {
        val at = day * DAY_MILLIS + 1_000
        storage.sessions.sets.value += SetEntity(0, uid, exercise.key, 24.0, 10, null, at)
        storage.sessions.sessions.value += SessionEntity(uid, at, at + 60_000, "", at)
    }
}

private const val DAY_MILLIS = 86_400_000L
