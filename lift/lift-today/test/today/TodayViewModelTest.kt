@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.lift.today

import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.data.SetEntity
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.DEFAULT_BAR_KG
import dev.justinsmith.lift.domain.MuscleGroup
import dev.justinsmith.lift.domain.barbellLifts
import dev.justinsmith.lift.fakes.FakeIntervalActivity
import dev.justinsmith.lift.fakes.FakeLiftStorage
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
        ) { 1_000L }
    }

    private val main = UnconfinedTestDispatcher()

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(main)

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun `a barbell lift never done suggests the bar the user owns and follows a change of it`() {
        val today =
            TodayViewModel(repository, FakeIntervalActivity(), FixedTimestampFormatter, clock = {
                1_000L
            }, computation = main)
        val lift = barbellLifts.first() to false
        assertEquals(DEFAULT_BAR_KG, today.state.value.bells[lift])

        repository.setBar(15.0)
        assertEquals(15.0, today.state.value.bells[lift])
    }

    @Test
    fun `the week and its today move on at midnight with the screen open`() = runTest {
        val main = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        // Sunday night, epoch day 3 (a Sunday), then Monday: a new week.
        var now = 4 * DAY_MILLIS - 1_000
        val today =
            TodayViewModel(repository, FakeIntervalActivity(), FixedTimestampFormatter, clock = {
                now
            }, computation = main)
        assertEquals(3L, today.state.value.week.single { it.today }.epochDay)

        now = 4 * DAY_MILLIS
        advanceTimeBy(DAY_CHECK_MILLIS + 1)

        assertEquals(4L, today.state.value.week.single { it.today }.epochDay)
        assertEquals(4L, today.state.value.week.first().epochDay)
        today.viewModelScope.cancel()
    }

    @Test
    fun `the balance counts the sessions finished in the last four weeks once two are`() {
        // Today is epoch day 100: day 73 is the window's first, day 72 just outside it.
        val now = 100 * DAY_MILLIS + 1_000
        finished("old", day = 72, BuiltInExercise.BarbellBenchPress)
        finished("first", day = 73, BuiltInExercise.GobletSquat)
        val today =
            TodayViewModel(repository, FakeIntervalActivity(), FixedTimestampFormatter, clock = {
                now
            }, computation = main)
        assertNull(today.state.value.balance)

        finished("second", day = 100, BuiltInExercise.GobletSquat)
        // An open session's sets wait until it finishes.
        repository.startSession(null)
        repository.logSet(BuiltInExercise.BarbellBenchPress, 60.0, 5, null)

        val balance = today.state.value.balance!!
        assertEquals(2.0, balance.loads.single { it.group == MuscleGroup.Legs }.worked)
        assertEquals(0.0, balance.loads.single { it.group == MuscleGroup.Chest }.total)
    }

    private fun finished(uid: String, day: Long, exercise: BuiltInExercise) {
        val at = day * DAY_MILLIS + 1_000
        storage.sessions.sets.value += SetEntity(0, uid, exercise.key, 24.0, 10, null, at)
        storage.sessions.sessions.value += SessionEntity(uid, at, at + 60_000, "", at)
    }
}

private const val DAY_MILLIS = 86_400_000L
