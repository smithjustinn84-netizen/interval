package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE = 60_000L

/**
 * A warm-up is kept with the session's sets but counts toward none of what working sets do: totals, records,
 * a planned set ticked off, pace, the challenges and tests, a suggested load, an exercise's trend. What is about
 * the body (the grip, the muscles worked, a first set at a new bell) still takes it.
 */
class WarmUpTest {
    private var nextId = 1L

    private fun swings(reps: Int, kg: Double, at: Long, warmUp: Boolean = false) =
        SetLog.of(nextId++, BuiltInExercise.TwoHandSwing, kg, reps, null, at, warmUp = warmUp)

    private fun snatches(reps: Int, at: Long, warmUp: Boolean = false) =
        SetLog.of(nextId++, BuiltInExercise.Snatch, 16.0, reps, Side.Left, at, warmUp = warmUp)

    private fun session(vararg sets: SetLog, at: Long = 0L) =
        LiftSession("s$at", at, at + MINUTE, "", sets.toList())

    @Test
    fun `a session's tonnage and reps leave the warm-ups out`() {
        val session = session(swings(10, 12.0, MINUTE, warmUp = true), swings(10, 24.0, 2 * MINUTE))
        assertEquals(240.0, session.tonnageKg)
        assertEquals(10, session.reps)
        assertEquals(2, session.sets.size)
        assertEquals(1, session.workingSets.size)
    }

    @Test
    fun `a warm-up sets no record and is never tagged as one`() {
        val heavier = swings(10, 32.0, MINUTE, warmUp = true)
        val work = swings(10, 24.0, 2 * MINUTE)
        val records = personalRecords(listOf(heavier, work))
        assertEquals(listOf(24.0), records.map { (it as PersonalRecord.Weighted).heaviestKg })
        val before = listOf(swings(10, 20.0, 0))
        assertEquals(setOf(work.id), recordSetIds(before, listOf(heavier, work)))
        assertTrue(newRecords(before, listOf(swings(10, 32.0, MINUTE, warmUp = true))).isEmpty())
    }

    @Test
    fun `a warm-up ticks off no planned set`() {
        val planned =
            listOf(
                PlannedSet(BuiltInExercise.TwoHandSwing, 10, null),
                PlannedSet(BuiltInExercise.TwoHandSwing, 10, null),
            )
        val logged = listOf(swings(10, 12.0, MINUTE, warmUp = true), swings(10, 24.0, 2 * MINUTE))
        assertEquals(listOf(true, false), plannedDone(planned, logged))
        assertEquals(1, nextPlanned(planned, logged))
    }

    @Test
    fun `pace and the snatch test count working sets alone`() {
        val warmUps = listOf(snatches(10, MINUTE, warmUp = true), snatches(10, 2 * MINUTE, warmUp = true))
        assertTrue(paces(warmUps).isEmpty())
        val work = listOf(snatches(20, 3 * MINUTE), snatches(20, 4 * MINUTE))
        val test = strengthTest(TestId.SnatchTest)
        assertEquals(40, testResult(test, warmUps + work)?.second)
        assertNull(testResult(test, warmUps))
        assertEquals(40, lifetimeReps(BuiltInExercise.Snatch, warmUps + work))
        assertEquals(40, mostInWindow(BuiltInExercise.Snatch, listOf(session(*(warmUps + work).toTypedArray())), 10))
    }

    @Test
    fun `the load suggested is the last worked and not the last warmed up at`() {
        val history = listOf(swings(10, 24.0, MINUTE), swings(10, 12.0, 2 * MINUTE, warmUp = true))
        assertEquals(24.0, suggestedBell(BuiltInExercise.TwoHandSwing, double = false, history, fallback = 16.0))
    }

    @Test
    fun `an exercise's trend skips a session of warm-ups alone`() {
        val sessions = listOf(
            PastSession("c", 3 * MINUTE, listOf(swings(10, 8.0, 3 * MINUTE, warmUp = true))),
            PastSession(
                "b",
                2 * MINUTE,
                listOf(swings(10, 12.0, 2 * MINUTE, warmUp = true), swings(10, 24.0, 2 * MINUTE)),
            ),
            PastSession("a", MINUTE, listOf(swings(10, 20.0, MINUTE))),
        )
        val trend = exerciseTrend(BuiltInExercise.TwoHandSwing, sessions)
        assertEquals(listOf(200.0, 240.0), trend?.points?.map { it.value })
    }

    @Test
    fun `the grip still takes a warm-up's load`() {
        val warmUp = swings(10, 16.0, MINUTE, warmUp = true)
        assertEquals(swings(10, 16.0, MINUTE).gripLoad, warmUp.gripLoad)
    }
}
