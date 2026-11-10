package dev.justinsmith.lift.session

import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.data.SetEntity
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.ReadinessReason
import dev.justinsmith.lift.fakes.FakeIntervalActivity
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val HOUR_MILLIS = 3_600_000L
private const val NOW = 100 * HOUR_MILLIS

/** [EasierDay] as the rest of the app reads it: why a day is taxed, and the easier day ahead of a session. */
class EasierDayTest {
    private val storage = FakeLiftStorage()
    private val repository by lazy {
        with(storage) {
            LiftRepository(
                sessions,
                bells,
                following,
                programs,
                exercises,
                bands,
                imports,
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                clock = { NOW },
            )
        }
    }
    private val easier by lazy {
        EasierDay(
            repository.sessions,
            repository.sets,
            repository.programs,
            repository.gear,
            FakeIntervalActivity(),
            FixedTimestampFormatter,
            { NOW },
        )
    }

    /** A session finished an hour ago, said to have felt very hard. */
    private fun afterAHardSession() {
        val at = NOW - HOUR_MILLIS
        storage.sessions.sets.value += SetEntity(0, "hard", BuiltInExercise.Snatch.key, 24.0, 20, null, at)
        storage.sessions.sessions.value += SessionEntity(
            "hard",
            at - HOUR_MILLIS,
            at,
            "Snatches",
            at,
            effort = PerceivedEffort.VeryHard.level,
        )
    }

    @Test
    fun `an ordinary day is no taxed one and has no easier day ahead`() = runTest {
        repository.programs.follow(ProgramKey.BuiltIn(ProgramId.BjjKettlebell))

        assertNull(easier.taxedBy.first())
        assertFalse(easier.easierAhead.first())
    }

    @Test
    fun `after a very hard session the program's day ahead can be eased until it is under way`() = runTest {
        afterAHardSession()
        val program = ProgramKey.BuiltIn(ProgramId.BjjKettlebell)
        assertEquals(ReadinessReason.HardSession, easier.taxedBy.first())
        // With no program followed there is no day to ease.
        assertFalse(easier.easierAhead.first())

        repository.programs.follow(program)
        assertTrue(easier.easierAhead.first())

        // Started and eased from outside Lift: the session's plan is the easier one, and its page offers the rest.
        repository.sessions.startSession(program)
        easier.useEasier()
        assertNotNull(repository.sessions.active.value?.plan)
        assertFalse(easier.easierAhead.first())
    }
}
