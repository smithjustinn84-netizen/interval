package dev.justinsmith.interval.workout

import dev.justinsmith.interval.model.CourseSpan
import dev.justinsmith.interval.platform.HealthMove
import dev.justinsmith.interval.platform.HealthSegment
import dev.justinsmith.interval.platform.HealthWorkoutKind
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HealthWorkoutsTest {
    @Test
    fun `a course becomes a HIIT session with a segment per interval rest and pause and none for get ready`() {
        val spans = listOf(
            CourseSpan(Phase.Prep, 1_000, 11_000),
            CourseSpan(Phase.Work, 11_000, 41_000),
            CourseSpan(null, 41_000, 50_000),
            CourseSpan(Phase.Work, 50_000, 56_000),
            CourseSpan(Phase.Rest, 56_000, 71_000),
            CourseSpan(Phase.Work, 71_000, 101_000),
        )
        val workout = healthWorkout(Activity.Rower, "Tabata", 1_000, 101_000, spans, calories = 42)

        assertEquals("interval-1000", workout.id)
        assertEquals(HealthWorkoutKind.Intervals, workout.kind)
        assertEquals("Tabata", workout.title)
        assertEquals(1_000, workout.startedAtEpochMillis)
        assertEquals(101_000, workout.endedAtEpochMillis)
        assertEquals(42, workout.calories)
        assertEquals(
            listOf(
                HealthSegment(11_000, 41_000, HealthMove.Rowing),
                HealthSegment(41_000, 50_000, HealthMove.Pause),
                HealthSegment(50_000, 56_000, HealthMove.Rowing),
                HealthSegment(56_000, 71_000, HealthMove.Rest),
                HealthSegment(71_000, 101_000, HealthMove.Rowing),
            ),
            workout.segments,
        )
    }

    @Test
    fun `a rest between sets is a rest segment`() {
        val spans = listOf(
            CourseSpan(Phase.Work, 0, 20_000),
            CourseSpan(Phase.SetRest, 20_000, 80_000),
            CourseSpan(Phase.Work, 80_000, 100_000),
        )
        val workout = healthWorkout(Activity.Kettlebell, null, 0, 100_000, spans, calories = null)

        assertEquals(
            listOf(
                HealthSegment(0, 20_000, HealthMove.KettlebellSwing),
                HealthSegment(20_000, 80_000, HealthMove.Rest),
                HealthSegment(80_000, 100_000, HealthMove.KettlebellSwing),
            ),
            workout.segments,
        )
    }

    @Test
    fun `segments keep within the session and never overlap`() {
        val spans = listOf(
            CourseSpan(Phase.Work, 0, 5_000),
            CourseSpan(Phase.Rest, 4_000, 8_000),
            CourseSpan(Phase.Work, 8_000, 8_000),
            CourseSpan(Phase.Work, 8_000, 20_000),
        )
        val workout = healthWorkout(Activity.General, null, 1_000, 12_000, spans, calories = null)

        assertNull(workout.title)
        assertNull(workout.calories)
        assertEquals(
            listOf(
                HealthSegment(1_000, 5_000, HealthMove.Work),
                HealthSegment(5_000, 8_000, HealthMove.Rest),
                HealthSegment(8_000, 12_000, HealthMove.Work),
            ),
            workout.segments,
        )
    }

    @Test
    fun `each activity has the store's word for its work`() {
        assertEquals(HealthMove.Work, Activity.General.healthMove)
        assertEquals(HealthMove.Biking, Activity.Bike.healthMove)
        assertEquals(HealthMove.Rowing, Activity.Rower.healthMove)
        assertEquals(HealthMove.Work, Activity.SkiErg.healthMove)
        assertEquals(HealthMove.Running, Activity.Running.healthMove)
        assertEquals(HealthMove.JumpRope, Activity.JumpRope.healthMove)
        assertEquals(HealthMove.Work, Activity.Bodyweight.healthMove)
        assertEquals(HealthMove.KettlebellSwing, Activity.Kettlebell.healthMove)
        assertEquals(HealthMove.Weightlifting, Activity.Barbell.healthMove)
        assertEquals(HealthMove.Work, Activity.Climbing.healthMove)
    }
}
