@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.lift.session

import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.RecordingHealthStore
import dev.justinsmith.interval.platform.HealthStatus
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.fakes.FakeIntervalActivity
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A session finishing: saved with its records, written to the health store, then what its summary says. */
class FinishSessionTest {
    private var now = 100_000L
    private val repository = FakeLiftStorage().repository { now }
    private val health = RecordingHealthStore(HealthStatus.Connected)

    private fun finishing(scope: CoroutineScope) = FinishSession(
        repository,
        repository,
        repository,
        FakeIntervalActivity(),
        FixedTimestampFormatter,
        { now },
        SessionHealth(health, { now }, scope, settleMillis = SETTLE),
    )

    private fun logged() {
        repository.startSession(null)
        repository.logSet(BuiltInExercise.GobletSquat, 16.0, 10, null)
    }

    @Test
    fun `a session finished is saved with its records and written to the health store`() = runTest {
        logged()
        val finishing = finishing(backgroundScope)
        now = 160_000

        assertTrue(finishing.finish("Squats"))

        val finished = assertNotNull(finishing.finished.value)
        assertEquals("Squats", finished.session.title)
        assertNull(repository.active.value)
        assertEquals("Squats", repository.sessions.value.single().title)
        assertEquals(1, health.workouts.size)
    }

    @Test
    fun `a session with no sets is thrown away and written nowhere`() = runTest {
        repository.startSession(null)
        val finishing = finishing(backgroundScope)

        assertFalse(finishing.finish("Nothing"))

        assertNull(finishing.finished.value)
        assertNull(repository.active.value)
        assertTrue(health.workouts.isEmpty())
    }

    @Test
    fun `the summary's effort and note are kept with the session as they are said`() = runTest {
        logged()
        val finishing = finishing(backgroundScope)
        finishing.finish("Squats")

        finishing.setEffort(PerceivedEffort.Hard)
        finishing.setNote("  Felt strong ")

        val finished = assertNotNull(finishing.finished.value)
        assertEquals("  Felt strong ", finished.note)
        assertEquals("Felt strong", finished.session.note)
        val kept = repository.sessions.value.single()
        assertEquals(PerceivedEffort.Hard, kept.effort)
        assertEquals("Felt strong", kept.note)
    }

    @Test
    fun `the summary's words reach the health store once they settle and at a later version`() = runTest {
        logged()
        val finishing = finishing(backgroundScope)
        finishing.finish("Squats")

        // Worded as each word is typed: only the last, once typing stops, is written.
        finishing.describeForHealth("Hard")
        now = 200_000
        finishing.describeForHealth("Hard · Felt")
        finishing.describeForHealth("Hard · Felt strong")
        settle()

        assertEquals(2, health.workouts.size)
        val again = health.workouts.last()
        assertEquals(health.workouts.first().id, again.id)
        assertEquals("Hard · Felt strong", again.notes)
        assertEquals(200_000, again.version)
    }

    @Test
    fun `with nothing said the health store is not written again`() = runTest {
        logged()
        val finishing = finishing(backgroundScope)
        finishing.finish("Squats")

        finishing.describeForHealth(null)
        settle()

        assertEquals(1, health.workouts.size)
    }

    private fun TestScope.settle() {
        advanceTimeBy(SETTLE + 1)
        runCurrent()
    }

    private companion object {
        const val SETTLE = 1_000L
    }
}
