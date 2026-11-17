package dev.justinsmith.lift.data

import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.fakes.logSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The followed program's next day logged after the fact, done as planned: Lift's [LiftLogging]. */
class DayAsPlannedTest {
    private var now = 1_000L
    private val repository = FakeLiftStorage().repository { now }
    private val ladders = ProgramKey.BuiltIn(ProgramId.PressLadders)

    private fun finishDayOf(key: ProgramKey) {
        repository.sessions.startSession(key)
        repository.sets.logSet(BuiltInExercise.Press, 16.0, 1, Side.Left)
        repository.sessions.finishSession("Press")
    }

    @Test
    fun `a day logged as planned after the fact is the followed program's next and moves it on`() = runTest {
        repository.programs.follow(ladders)
        finishDayOf(ladders)
        now = 10 * DAY
        // A session in progress is left as it is.
        repository.sessions.startSession()
        repository.sets.logSet(BuiltInExercise.TwoHandSwing, 24.0, 10, null)
        val planned = repository.programs.programDay.value
        val uid = assertNotNull(repository.sessions.logDayAsPlanned("Press", 5 * DAY))
        val logged = repository.sessions.sessions.value.first { it.uid == uid }
        assertEquals(ladders, logged.program)
        assertEquals(2, logged.day)
        assertEquals("Press", logged.title)
        assertEquals(5 * DAY, logged.finishedAtEpochMillis)
        assertEquals(planned?.plannedSets()?.size, logged.sets.size)
        // The press at the bell it was done with last.
        assertTrue(logged.sets.filter { it.exercise == BuiltInExercise.Press }.all { it.load.kg == 16.0 })
        assertEquals(3, repository.programs.programDay.value?.number)
        assertEquals(1, repository.sessions.active.value?.sets?.size)

        repository.sessions.unlog(uid)
        assertEquals(2, repository.programs.programDay.value?.number)
        assertTrue(repository.sessions.sessions.value.none { it.uid == uid })
    }

    @Test
    fun `a day logged straight after following counts as its first`() = runTest {
        now = 10 * DAY
        repository.programs.follow(ladders)
        now += 60_000
        val uid = assertNotNull(repository.sessions.logDayAsPlanned("Press", now))
        val logged = repository.sessions.sessions.value.single { it.uid == uid }
        assertTrue(logged.startedAtEpochMillis >= 10 * DAY)
        assertTrue(assertNotNull(logged.finishedAtEpochMillis) <= now)
        assertEquals(2, repository.programs.programDay.value?.number)
    }

    @Test
    fun `the morning of the afternoon a program was followed counts as its first day`() = runTest {
        now = 10 * DAY + 15 * HOUR
        repository.programs.follow(ladders)
        now = 12 * DAY
        val uid = assertNotNull(repository.sessions.logDayAsPlanned("Press", 10 * DAY + 12 * HOUR))
        val logged = repository.sessions.sessions.value.single { it.uid == uid }
        assertTrue(logged.startedAtEpochMillis >= 10 * DAY + 15 * HOUR)
        assertEquals(2, repository.programs.programDay.value?.number)
    }

    @Test
    fun `nothing is logged as planned while a session of the program is under way`() = runTest {
        repository.programs.follow(ladders)
        now = 10 * DAY
        repository.sessions.startSession(ladders)
        assertNull(repository.sessions.logDayAsPlanned("Press", 5 * DAY))
    }

    @Test
    fun `nothing is logged as planned with no program followed`() = runTest {
        assertNull(repository.sessions.logDayAsPlanned("Press", 5 * DAY))
        assertNull(repository.sessions.followedSince.first())
        repository.programs.follow(ladders)
        assertEquals(now, repository.sessions.followedSince.first())
    }

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
    }
}
