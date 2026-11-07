package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE = 60_000L

class DensityTest {
    private fun swings(reps: Int, at: Long, kg: Double = 24.0) =
        SetLog.of(0, BuiltInExercise.TwoHandSwing, kg, reps, null, at)

    @Test
    fun `reps a minute run from the first set's start to the last logged`() {
        // 15 swings at 2 s a rep: the first began 30 s before it was logged, at 0:30; three sets over 3 minutes.
        val sets = listOf(swings(15, MINUTE), swings(15, 2 * MINUTE), swings(15, 3 * MINUTE + 30_000))
        assertEquals(15.0, density(sets))
    }

    @Test
    fun `sets on the minute come to their reps a minute`() {
        val emom = (1..10).map { swings(10, it * MINUTE) }
        assertEquals(10 * 10 / ((10 * MINUTE - (MINUTE - 20_000)) / 60_000.0), density(emom))
    }

    @Test
    fun `sets entered together afterwards say nothing of pace`() {
        val batch = listOf(swings(20, MINUTE), swings(20, MINUTE + 5_000), swings(20, MINUTE + 10_000))
        assertNull(density(batch))
    }

    @Test
    fun `one set or a hold has no pace`() {
        assertNull(density(listOf(swings(20, MINUTE))))
        val carries = listOf(
            SetLog.of(0, BuiltInExercise.SuitcaseCarry, 24.0, 0, null, MINUTE, seconds = 30),
            SetLog.of(0, BuiltInExercise.SuitcaseCarry, 24.0, 0, null, 2 * MINUTE, seconds = 30),
        )
        assertNull(density(carries))
    }

    @Test
    fun `a faster session at the same bell is a new best pace and a first time is none`() {
        fun session(uid: String, gap: Long) =
            LiftSession(uid, 0, 10 * MINUTE, "", listOf(swings(15, MINUTE), swings(15, MINUTE + gap)))
        val slow = session("a", 2 * MINUTE)
        val fast = session("b", MINUTE)
        assertTrue(newPaces(emptyList(), slow).isEmpty())
        val best = newPaces(listOf(slow), fast).single()
        assertEquals(BuiltInExercise.TwoHandSwing, best.exercise)
        assertEquals(24.0, best.kg)
        assertTrue(newPaces(listOf(fast), slow).isEmpty())
        assertEquals(best, bestPaceOf(BuiltInExercise.TwoHandSwing, listOf(slow, fast)))
    }

    @Test
    fun `an interval workout's snatches set a pace beside the sessions'`() {
        val ends = List(40) { 25_000L + it * 30_000L }
        val intervals = intervalSets(listOf(IntervalSnatches(8, 16.0, ends, "15:15 MVO2")))

        val pace = assertNotNull(bestPaceOf(BuiltInExercise.Snatch, emptyList(), intervals))
        // 320 snatches from the first set's start, 9 s in, to the last one's end at 19:55.
        assertEquals(16.19, pace.perMinute, 0.01)
        assertEquals(16.0, pace.kg)
        assertNull(bestPaceOf(BuiltInExercise.Snatch, emptyList()))
    }
}
