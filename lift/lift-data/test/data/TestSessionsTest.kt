package dev.justinsmith.lift.data

import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.TestId
import dev.justinsmith.lift.domain.attempts
import dev.justinsmith.lift.domain.dayOf
import dev.justinsmith.lift.domain.shippedPrograms
import dev.justinsmith.lift.domain.strengthTest
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.fakes.logSet
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A test's session: started as the one day of the test's own program, and read back as an attempt. */
class TestSessionsTest {
    private var now = 1_000L
    private val repository = FakeLiftStorage().repository { now }

    @Test
    fun `a test starts as a day of its own program at the bell picked`() = runTest {
        val press = strengthTest(TestId.PressReps)
        repository.sessions.startTest(press, Load.Bell(20.0))
        val first = repository.sessions.active.value
        assertEquals(press.key, first?.program)
        assertEquals(1, first?.day)
        val day = shippedPrograms.dayOf(first ?: error("No session"))
        assertEquals(press.block.copy(load = Load.Bell(20.0)), day?.today?.blocks?.single()?.block)
        repository.sets.logSet(BuiltInExercise.Press, 20.0, 9, Side.Left)
        repository.sessions.finishSession("Press")
        assertEquals(listOf(9), attempts(press, repository.sessions.sessions.value).map { it.result })
        // The next attempt at that bell opens on the number to beat; one in progress is left alone.
        now += 60_000
        repository.sessions.startTest(press, Load.Bell(20.0))
        repository.sessions.startTest(strengthTest(TestId.PlankHold), null)
        val second = repository.sessions.active.value
        assertEquals(press.key, second?.program)
        assertEquals(9, second?.plan?.blocks?.single()?.block?.reps)
        repository.sessions.discardSession()
        // With no bell picked a test is as it stands, with no plan of its own.
        repository.sessions.startTest(strengthTest(TestId.PlankHold), null)
        assertNull(repository.sessions.active.value?.plan)
    }
}
