package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DAY = 86_400_000L

class GripTest {
    private val dayOf: (Long) -> Long = { it / DAY }

    private fun set(exercise: Exercise, reps: Int, day: Long, double: Boolean = false, seconds: Int? = null) =
        SetLog.of(0, exercise, 16.0, reps, null, day * DAY, double = double, seconds = seconds)

    @Test
    fun `a set's grip load counts the lifts in a rep the grip and the throw`() {
        // 10 snatches: grip 3, ballistic half again.
        assertEquals(45.0, set(BuiltInExercise.Snatch, 10, 0).gripLoad)
        // 10 goblet squats: grip 1, a grind.
        assertEquals(10.0, set(BuiltInExercise.GobletSquat, 10, 0).gripLoad)
        // 5 ABC with a pair: 6 lifts a rep, grip 2, ballistic, two hands.
        assertEquals(180.0, set(BuiltInExercise.ArmorBuildingComplex, 5, 0, double = true).gripLoad)
        // 30 seconds of a bottoms-up carry: 3 reps' worth at grip 3.
        assertEquals(9.0, set(BuiltInExercise.BottomsUpCarry, 0, 0, seconds = 30).gripLoad)
        assertEquals(0.0, set(BuiltInExercise.PushUp, 20, 0).gripLoad)
    }

    @Test
    fun `this week is weighed against the three before and BJJ days count`() {
        val weeksBefore = (1..20L).filter { it % 3 == 0L }.map { set(BuiltInExercise.TwoHandSwing, 100, it) }
        val thisWeek = listOf(set(BuiltInExercise.Snatch, 100, 24), set(BuiltInExercise.GobletSquat, 30, 25))
        val grip = gripLoad(weeksBefore + thisWeek, grappling = listOf(26, 27), today = 27, dayOf)
        // 6 days of 300 in the three weeks before: 600 a week.
        assertEquals(600.0, grip.weeksBefore)
        assertEquals(450.0, grip.ballistic)
        assertEquals(30.0, grip.grind)
        assertEquals(480.0 + 2 * GRAPPLING_GRIP_LOAD, grip.acute)
        assertTrue(grip.overreached)
        assertEquals(450.0 / 480.0, grip.ballisticShare)
    }

    @Test
    fun `a first week never reads as overreached`() {
        val grip = gripLoad(List(5) { set(BuiltInExercise.Snatch, 100, 22L + it) }, emptyList(), today = 27, dayOf)
        assertNull(grip.ratio)
        assertFalse(grip.overreached)
    }

    @Test
    fun `load past the month is forgotten`() {
        val grip = gripLoad(listOf(set(BuiltInExercise.Snatch, 100, 0)), listOf(0), today = 40, dayOf)
        assertEquals(GripLoad(), grip)
    }

    @Test
    fun `a heavy day is half again a usual one and hand care follows many snatches`() {
        val usual = GripLoad(chronic = 900.0, trainingDays = 3)
        assertTrue(usual.heavyDay(451.0))
        assertFalse(usual.heavyDay(450.0))
        assertFalse(GripLoad(chronic = 900.0, trainingDays = 2).heavyDay(10_000.0))
        val snatches = LiftSession("a", 0, 1, "", List(5) { set(BuiltInExercise.Snatch, 20, 0) })
        assertTrue(snatches.wantsHandCare)
        assertFalse(snatches.copy(sets = snatches.sets.drop(1)).wantsHandCare)
    }
}
