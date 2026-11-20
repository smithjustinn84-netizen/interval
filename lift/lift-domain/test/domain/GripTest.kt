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
    fun `the month's four weeks count back from today and add up to it`() {
        val sets = listOf(
            set(BuiltInExercise.TwoHandSwing, 100, 0),
            set(BuiltInExercise.TwoHandSwing, 50, 6),
            set(BuiltInExercise.Snatch, 100, 20),
            set(BuiltInExercise.Snatch, 10, 27),
        )
        // Day 0 is the month's first day, in the oldest week with day 6; day 20 is the week before this one.
        val grip = gripLoad(sets, grappling = listOf(13), today = 27, dayOf)
        assertEquals(listOf(450.0, GRAPPLING_GRIP_LOAD, 450.0, 45.0), grip.weeks)
        assertEquals(grip.acute, grip.weeks.last())
        assertEquals(grip.chronic, grip.weeks.sum())
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

    @Test
    fun `an interval workout's snatches and a session's on the same day want the hands looked after together`() {
        // 15:15 on day 3: eight sets of eight, 64 snatches; a session's 40 the same day come to 104.
        val intervals = intervalSets(listOf(IntervalSnatches(8, 16.0, List(8) { 3 * DAY + it * 30_000L }, null)))
        val session = List(2) { set(BuiltInExercise.Snatch, 20, 3) }

        assertFalse(intervals.sets.wantsHandCare)
        assertFalse(session.wantsHandCare)
        assertTrue(wantsHandCare(session, intervals.sets, today = 3, dayOf = dayOf))
        assertTrue(wantsHandCare(intervals.sets, session, today = 3, dayOf = dayOf))
        // The day before's count for nothing, and a session of squats says nothing of the hands.
        assertFalse(wantsHandCare(session, intervals.sets, today = 4, dayOf = dayOf))
        val squats = listOf(set(BuiltInExercise.GobletSquat, 10, 3))
        assertFalse(wantsHandCare(squats, intervals.sets + session, today = 3, dayOf = dayOf))
    }

    @Test
    fun `an interval workout's snatches load the grip as sets logged do`() {
        val ends = List(40) { 3 * DAY + it * 30_000L }
        val intervals = intervalSets(listOf(IntervalSnatches(8, 16.0, ends, "15:15 MVO2")))
        val grip = gripLoad(intervals.sets, emptyList(), today = 3, dayOf = dayOf)

        // 320 snatches at 4.5 a rep, all of them ballistic.
        assertEquals(1_440.0, grip.acute)
        assertEquals(1_440.0, grip.ballistic)
    }

    @Test
    fun `a recorded class weighs on the grip by the time its rounds came to`() {
        // Five rounds of five minutes is the usual class; half that is half, and a long open mat stops at double.
        assertEquals(GRAPPLING_GRIP_LOAD, grapplingGripLoad(null))
        assertEquals(GRAPPLING_GRIP_LOAD, grapplingGripLoad(USUAL_MAT_SECONDS))
        assertEquals(GRAPPLING_GRIP_LOAD / 2, grapplingGripLoad(USUAL_MAT_SECONDS / 2))
        assertEquals(GRAPPLING_GRIP_LOAD / 2, grapplingGripLoad(0))
        assertEquals(GRAPPLING_GRIP_LOAD * 2, grapplingGripLoad(USUAL_MAT_SECONDS * 5))
    }

    @Test
    fun `the week's load takes a recorded day by its rounds and any other as a usual class`() {
        val grip = gripLoad(emptyList(), grappling = listOf(26, 27), today = 27, dayOf, mapOf(27L to 3_000))

        assertEquals(GRAPPLING_GRIP_LOAD + GRAPPLING_GRIP_LOAD * 2, grip.acute)
    }
}
