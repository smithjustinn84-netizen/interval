package dev.justinsmith.lift.session

import dev.justinsmith.interval.platform.HealthMove
import dev.justinsmith.interval.platform.HealthSegment
import dev.justinsmith.interval.platform.HealthWorkout
import dev.justinsmith.interval.platform.HealthWorkoutKind
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.LiftSession

/**
 * A finished session as the health store takes it: one strength-training session, titled as the summary
 * titled it, with a segment per set. A set is logged as it ends, so its segment runs from the set before
 * it (or the session's start) to its logging, rests included; a hold's runs for the time it was held. The
 * reps go with the segment. [calories] is the app's estimate for a session of this length, if it makes one.
 * [named] is its title there: the session's own, or a program day's name.
 */
internal fun LiftSession.healthWorkout(
    finishedAtEpochMillis: Long,
    calories: Int?,
    named: String = title,
): HealthWorkout {
    // A clock set back can finish a session before it started: then it is a session of no length.
    val finishedAt = maxOf(finishedAtEpochMillis, startedAtEpochMillis)
    val segments = mutableListOf<HealthSegment>()
    var last = startedAtEpochMillis
    for (set in sets.sortedBy { it.loggedAtEpochMillis }) {
        val end = set.loggedAtEpochMillis.coerceIn(startedAtEpochMillis, finishedAt)
        val held = set.seconds?.let { end - it * MILLIS_PER_SECOND }
        val start = if (held != null) maxOf(held, last) else last
        if (end <= start) continue
        segments += HealthSegment(start, end, set.exercise.healthMove, repetitions = set.reps)
        last = end
    }
    return HealthWorkout(
        id = "lift-$uid",
        kind = HealthWorkoutKind.Strength,
        title = named,
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = finishedAt,
        segments = segments,
        calories = calories,
    )
}

/** The store's word for [this] lift, where it has one; the rest are weightlifting, or other work with no weight. */
internal val Exercise.healthMove: HealthMove
    get() = if (this is BuiltInExercise) builtInMove else equipment.healthMove

private val Equipment.healthMove: HealthMove
    get() = when (this) {
        Equipment.Kettlebell, Equipment.Barbell -> HealthMove.Weightlifting
        Equipment.Bodyweight, Equipment.Band, Equipment.Mat -> HealthMove.Other
    }

@Suppress("detekt.CyclomaticComplexMethod") // One branch per lift the store has a word for.
private val BuiltInExercise.builtInMove: HealthMove
    get() = when (this) {
        BuiltInExercise.TwoHandSwing, BuiltInExercise.OneHandSwing -> HealthMove.KettlebellSwing

        BuiltInExercise.GobletSquat, BuiltInExercise.FrontSquat, BuiltInExercise.AirSquat,
        BuiltInExercise.BarbellBackSquat, BuiltInExercise.BarbellFrontSquat, BuiltInExercise.BarbellOverheadSquat,
        -> HealthMove.Squat

        BuiltInExercise.Deadlift, BuiltInExercise.BarbellDeadlift, BuiltInExercise.BarbellRomanianDeadlift,
        -> HealthMove.Deadlift

        BuiltInExercise.Press, BuiltInExercise.PushPress, BuiltInExercise.BottomsUpPress,
        BuiltInExercise.BarbellOverheadPress, BuiltInExercise.BarbellPushPress, BuiltInExercise.BandOverheadPress,
        -> HealthMove.ShoulderPress

        BuiltInExercise.BarbellBenchPress, BuiltInExercise.BandChestPress -> HealthMove.BenchPress

        BuiltInExercise.Row, BuiltInExercise.BarbellRow, BuiltInExercise.InvertedRow, BuiltInExercise.BandSeatedRow,
        -> HealthMove.Row

        BuiltInExercise.PullUp, BuiltInExercise.ChinUp, BuiltInExercise.BandLatPulldown -> HealthMove.PullUp

        BuiltInExercise.Plank, BuiltInExercise.SidePlank, BuiltInExercise.HollowHold -> HealthMove.Plank

        BuiltInExercise.RackedLunge, BuiltInExercise.SplitSquat, BuiltInExercise.ReverseLunge,
        BuiltInExercise.BulgarianSplitSquat, BuiltInExercise.StepUp,
        -> HealthMove.Lunge

        BuiltInExercise.GluteBridge, BuiltInExercise.SingleLegGluteBridge -> HealthMove.HipThrust

        BuiltInExercise.Burpee -> HealthMove.Burpee

        BuiltInExercise.MountainClimber -> HealthMove.MountainClimber

        else -> equipment.healthMove
    }
