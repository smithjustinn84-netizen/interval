package dev.justinsmith.lift.domain

import kotlin.math.roundToInt

// About how long a program's day takes, before it is started: an estimate from what it asks for and the rest
// the user takes between sets, never a promise. A session's own time is `LiftSession.elapsedSeconds`.

/**
 * About how long this plan takes, in seconds, resting [restSeconds] between sets, or a block's own rest. An EMOM
 * lasts its minutes and its count-in, with no rest inside. A hold lasts its seconds and its count-in. Any other set
 * takes its reps at its lift's pace ([secondsPerRep]). A rest follows each set that isn't on the minute, a
 * one-sided lift's two sides sharing one (the rest timer offers one after each side, which few take), and none
 * follows the last set of the day. A skipped block counts for the sets it kept.
 */
fun SessionPlan.estimatedSeconds(restSeconds: Int): Int {
    val parts = blocks.map { it.block to it.plannedSets() }.filter { (_, sets) -> sets.isNotEmpty() }
    val work = parts.sumOf { (block, sets) ->
        if (block.kind == BlockKind.Emom) emomSeconds(sets) else setsSeconds(sets)
    }
    // Each block rests its own, when it keeps one ([Block.rest]), else the user's.
    val resting = parts.filter { (block, _) -> block.kind != BlockKind.Emom }
    val rested = resting.sumOf { (block, sets) -> rests(sets) * (block.rest ?: restSeconds) }
    val last = parts.lastOrNull()?.first?.takeIf { it.kind != BlockKind.Emom }
    return work + (rested - (last?.let { it.rest ?: restSeconds } ?: 0)).coerceAtLeast(0)
}

/** About how long this day takes as it stands today, in seconds: see [SessionPlan.estimatedSeconds]. */
fun ProgramDay.estimatedSeconds(restSeconds: Int): Int = today.estimatedSeconds(restSeconds)

/**
 * About how long this day takes, in minutes, to say: a day that is all on the minute is its minutes exactly
 * ("12 minutes of swings" is 12); any other is rounded to the nearest five, and never less than five.
 */
fun ProgramDay.estimatedMinutes(restSeconds: Int): Int {
    val blocks = today.blocks.filter { it.plannedSets().isNotEmpty() }
    return if (blocks.isNotEmpty() && blocks.all { it.block.kind == BlockKind.Emom }) {
        blocks.sumOf { it.plannedSets().size }
    } else {
        val minutes = estimatedSeconds(restSeconds) / SECONDS_PER_MINUTE.toDouble()
        ((minutes / ROUND_TO_MINUTES).roundToInt() * ROUND_TO_MINUTES).coerceAtLeast(ROUND_TO_MINUTES)
    }
}

/** About how long this program's days take, the shortest of them to the longest, in minutes. */
fun Program.dayMinutes(restSeconds: Int): IntRange {
    val minutes = days.indices.map { day(it + 1).estimatedMinutes(restSeconds) }
    return minutes.min()..minutes.max()
}

/** An EMOM's minutes, one a set, after its count-in. */
private fun emomSeconds(sets: List<PlannedSet>): Int =
    sets.size * SECONDS_PER_MINUTE + (EmomClock.COUNT_IN_MILLIS / MILLIS_PER_SECOND).toInt()

private fun setsSeconds(sets: List<PlannedSet>): Int = sets.sumOf { set ->
    if (set.held) {
        set.reps + (HoldClock.COUNT_IN_MILLIS / MILLIS_PER_SECOND).toInt()
    } else {
        set.reps * set.exercise.secondsPerRep
    }
}

/** The rests these sets take: one after each, a left side going straight on to the right. */
private fun rests(sets: List<PlannedSet>): Int = sets.count { it.side != Side.Left }

/**
 * About how long a rep of this lift takes, in seconds: the ballistic lifts are quick, a get-up is most of a
 * minute a side, a complex is several lifts to a rep, and a barbell lift is set up for each time. One the user
 * made takes the usual pace.
 */
internal val Exercise.secondsPerRep: Int
    get() = when (this) {
        BuiltInExercise.Hundred, BuiltInExercise.MountainClimber -> 1

        BuiltInExercise.TwoHandSwing, BuiltInExercise.OneHandSwing, BuiltInExercise.Snatch,
        BuiltInExercise.HalfSnatch, BuiltInExercise.Clean, BuiltInExercise.HighPull, BuiltInExercise.Jerk,
        -> 2

        BuiltInExercise.Thruster, BuiltInExercise.BottomsUpPress -> 4

        BuiltInExercise.LongCycle, BuiltInExercise.CleanAndPress -> 5

        BuiltInExercise.Windmill -> 6

        BuiltInExercise.IronCardio -> 8

        BuiltInExercise.ArmorBuildingComplex -> 20

        BuiltInExercise.GetUp -> 40

        BuiltInExercise.BarbellCleanAndJerk -> 12

        is BuiltInExercise -> when {
            equipment != Equipment.Barbell -> USUAL_SECONDS_PER_REP
            focus == Focus.FullBody -> BARBELL_LIFT_SECONDS_PER_REP
            else -> BARBELL_SECONDS_PER_REP
        }

        else -> USUAL_SECONDS_PER_REP
    }

private const val USUAL_SECONDS_PER_REP = 3

/** A squat, a press or a pull under a bar. */
private const val BARBELL_SECONDS_PER_REP = 4

/** An Olympic lift or one of its pulls: reset between reps. */
private const val BARBELL_LIFT_SECONDS_PER_REP = 6
private const val ROUND_TO_MINUTES = 5
private const val SECONDS_PER_MINUTE = 60
private const val MILLIS_PER_SECOND = 1_000L
