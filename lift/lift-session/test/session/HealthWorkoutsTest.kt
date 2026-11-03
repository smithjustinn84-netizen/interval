package dev.justinsmith.lift.session

import dev.justinsmith.interval.platform.HealthMove
import dev.justinsmith.interval.platform.HealthSegment
import dev.justinsmith.interval.platform.HealthWorkoutKind
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Focus
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Measure
import dev.justinsmith.lift.domain.SetLog
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthWorkoutsTest {
    private fun set(id: Long, exercise: Exercise, loggedAt: Long, reps: Int = 10, seconds: Int? = null) =
        SetLog.of(id, exercise, kg = 16.0, reps = reps, side = null, loggedAtEpochMillis = loggedAt, seconds = seconds)

    @Test
    fun `a session becomes strength training with a segment per set up to its logging`() {
        val session = LiftSession(
            uid = "abc",
            startedAtEpochMillis = 100_000,
            finishedAtEpochMillis = 400_000,
            title = "Swings and squats",
            sets = listOf(
                set(1, BuiltInExercise.TwoHandSwing, 160_000),
                set(2, BuiltInExercise.GobletSquat, 220_000, reps = 5),
                set(3, BuiltInExercise.Plank, 300_000, reps = 0, seconds = 30),
            ),
        )
        val workout = session.healthWorkout(400_000, calories = 90)

        assertEquals("lift-abc", workout.id)
        assertEquals(HealthWorkoutKind.Strength, workout.kind)
        assertEquals("Swings and squats", workout.title)
        assertEquals(100_000, workout.startedAtEpochMillis)
        assertEquals(400_000, workout.endedAtEpochMillis)
        assertEquals(90, workout.calories)
        assertEquals(
            listOf(
                HealthSegment(100_000, 160_000, HealthMove.KettlebellSwing, repetitions = 10),
                HealthSegment(160_000, 220_000, HealthMove.Squat, repetitions = 5),
                // A hold's segment is as long as it was held, not the rest before it.
                HealthSegment(270_000, 300_000, HealthMove.Plank, repetitions = 0),
            ),
            workout.segments,
        )
    }

    @Test
    fun `sets are taken in the order logged and kept within the session`() {
        val session = LiftSession(
            uid = "abc",
            startedAtEpochMillis = 100_000,
            finishedAtEpochMillis = 200_000,
            title = "Presses",
            sets = listOf(
                set(2, BuiltInExercise.Press, 150_000),
                set(1, BuiltInExercise.Press, 120_000),
                set(3, BuiltInExercise.Press, 250_000),
                set(4, BuiltInExercise.Press, 260_000),
            ),
        )
        assertEquals(
            listOf(
                HealthSegment(100_000, 120_000, HealthMove.ShoulderPress, repetitions = 10),
                HealthSegment(120_000, 150_000, HealthMove.ShoulderPress, repetitions = 10),
                HealthSegment(150_000, 200_000, HealthMove.ShoulderPress, repetitions = 10),
            ),
            session.healthWorkout(200_000, calories = null).segments,
        )
    }

    @Test
    fun `lifts take the store's words and the rest fall back on their equipment`() {
        assertEquals(HealthMove.KettlebellSwing, BuiltInExercise.OneHandSwing.healthMove)
        assertEquals(HealthMove.Squat, BuiltInExercise.BarbellBackSquat.healthMove)
        assertEquals(HealthMove.Deadlift, BuiltInExercise.BarbellRomanianDeadlift.healthMove)
        assertEquals(HealthMove.BenchPress, BuiltInExercise.BarbellBenchPress.healthMove)
        assertEquals(HealthMove.Row, BuiltInExercise.InvertedRow.healthMove)
        assertEquals(HealthMove.PullUp, BuiltInExercise.ChinUp.healthMove)
        assertEquals(HealthMove.Lunge, BuiltInExercise.StepUp.healthMove)
        assertEquals(HealthMove.HipThrust, BuiltInExercise.GluteBridge.healthMove)
        assertEquals(HealthMove.Burpee, BuiltInExercise.Burpee.healthMove)
        assertEquals(HealthMove.MountainClimber, BuiltInExercise.MountainClimber.healthMove)
        assertEquals(HealthMove.Weightlifting, BuiltInExercise.Snatch.healthMove)
        assertEquals(HealthMove.Weightlifting, BuiltInExercise.BarbellCleanAndJerk.healthMove)
        assertEquals(HealthMove.Other, BuiltInExercise.PushUp.healthMove)
        assertEquals(HealthMove.Other, BuiltInExercise.BandPullApart.healthMove)
        assertEquals(HealthMove.Weightlifting, custom(Equipment.Kettlebell).healthMove)
        assertEquals(HealthMove.Other, custom(Equipment.Mat).healthMove)
    }

    private fun custom(equipment: Equipment) = CustomExercise(
        uid = "mine",
        name = "Mine",
        equipment = equipment,
        measure = Measure.Reps,
        perSide = false,
        focus = Focus.FullBody,
    )

    @Test
    fun `a session finished before it started by a clock set back has no length and no segments`() {
        val session = LiftSession("abc", 100_000, 50_000, "Odd", listOf(set(1, BuiltInExercise.Press, 60_000)))
        val workout = session.healthWorkout(50_000, calories = null)
        assertEquals(100_000, workout.startedAtEpochMillis)
        assertEquals(100_000, workout.endedAtEpochMillis)
        assertEquals(emptyList(), workout.segments)
    }
}
