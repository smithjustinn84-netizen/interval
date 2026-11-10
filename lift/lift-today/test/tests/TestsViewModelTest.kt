@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.lift.tests

import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.lift.data.SportDay
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.domain.Retest
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.TestId
import dev.justinsmith.lift.domain.standardBells
import dev.justinsmith.lift.domain.strengthTest
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DAY = 86_400_000L

class TestsViewModelTest {
    private var now = 10 * DAY
    private val repository = FakeLiftStorage().repository { now }
    private val intervals = FakeIntervalActivity()

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun tests() =
        TestsViewModel(repository.sessions, repository.gear, intervals, FixedTimestampFormatter, { now })

    /** A press test done at [kg] for [reps], finished as the clock stands. */
    private fun pressed(kg: Double, reps: Int) {
        repository.sessions.startTest(strengthTest(TestId.PressReps), Load.Bell(kg))
        repository.sets.logSet(BuiltInExercise.Press, Load.Bell(kg), reps, Side.Left)
        repository.sessions.finishSession("Press")
    }

    @Test
    fun `every test is listed and none has been done`() {
        val state = tests().state.value
        assertEquals(TestId.entries, state.tests.map { it.test.id })
        assertTrue(state.tests.all { it.last == null && it.retest == Retest.Never && it.daysSince == null })
        assertFalse(state.inProgress)
        assertFalse(state.taxed)
    }

    @Test
    fun `a test with a bell offers the bells owned and one without offers none`() {
        val tests = tests()
        assertEquals(standardBells, tests.state.value.row(TestId.SnatchTest).bells)
        assertEquals(emptyList(), tests.state.value.row(TestId.PlankHold).bells)
        assertNull(tests.state.value.row(TestId.PushUpReps).bellKg)

        repository.gear.setOwned(16.0, owned = true)
        repository.gear.setOwned(24.0, owned = true)
        assertEquals(listOf(16.0, 24.0), tests.state.value.row(TestId.SnatchTest).bells)
        // Two bells of a size is a pair: the complex is done with those alone.
        repository.gear.setPair(24.0, pair = true)
        assertEquals(listOf(24.0), tests.state.value.row(TestId.ArmorBuilding).bells)
        assertEquals(24.0, tests.state.value.row(TestId.ArmorBuilding).bellKg)
    }

    @Test
    fun `attempts are kept by bell and the sheet opens on the last one's`() {
        repository.gear.setOwned(16.0, owned = true)
        repository.gear.setOwned(20.0, owned = true)
        pressed(16.0, 8)
        now += 30 * DAY
        pressed(20.0, 5)
        now += 3 * DAY
        val row = tests().state.value.row(TestId.PressReps)
        assertEquals(listOf(8, 5), row.attempts.map { it.result })
        assertEquals(20.0, row.bellKg)
        assertEquals(listOf(5), row.attemptsAt(20.0).map { it.result })
        assertEquals(listOf(8), row.attemptsAt(16.0).map { it.result })
        assertEquals(3, row.daysSince)
        assertEquals(Retest.TooSoon, row.retest)
    }

    @Test
    fun `starting a test opens a session of it at the bell picked`() {
        val tests = tests()
        tests.start(TestId.SnatchTest, 24.0)
        val active = repository.sessions.active.value
        assertEquals(strengthTest(TestId.SnatchTest).key, active?.program)
        assertEquals(Load.Bell(24.0), active?.plan?.blocks?.single()?.block?.load)
        assertTrue(tests.state.value.inProgress)
        repository.sessions.discardSession()
        // The complex is done with two.
        tests.start(TestId.ArmorBuilding, 16.0)
        assertEquals(
            Load.Bell(16.0, double = true),
            repository.sessions.active.value?.plan?.blocks?.single()?.block?.load,
        )
        repository.sessions.discardSession()
        // A test with no bell is as it stands.
        tests.start(TestId.PlankHold, null)
        assertNull(repository.sessions.active.value?.plan)
    }

    @Test
    fun `a very hard session the day before makes today a day to leave a test`() {
        repository.sessions.startSession()
        repository.sets.logSet(BuiltInExercise.TwoHandSwing, Load.Bell(24.0), 100, null)
        repository.sessions.finishSession("Swings")
        val uid = repository.sessions.sessions.value.single().uid
        repository.sessions.reflectOnSession(uid, PerceivedEffort.VeryHard, "")
        now += DAY
        assertTrue(tests().state.value.taxed)
        intervals.sportDays.value = listOf(SportDay(0, grappling = false))
        now += 3 * DAY
        assertFalse(tests().state.value.taxed)
    }
}
