package dev.justinsmith.lift.domain

import kotlin.math.roundToInt

// Density: the same work in less time, how a lifter progresses between fixed bell sizes.

private const val MILLIS_PER_MINUTE = 60_000.0
private const val TENTHS = 10
private const val MILLIS_PER_SECOND = 1_000L

/**
 * When [this] set began, as its time logged less its reps at its lift's pace ([secondsPerRep]): a set is logged as
 * it ends.
 */
private val SetLog.startedAt: Long get() = loggedAtEpochMillis - reps * exercise.secondsPerRep * MILLIS_PER_SECOND

/**
 * Whether [sets], in the order logged, were logged as they were done: each one's span, its reps at its lift's pace
 * ending when it was logged, begins once the one before ended. Sets entered together after the fact overlap, and
 * say nothing of how fast they were done.
 */
fun loggedAsDone(sets: List<SetLog>): Boolean =
    sets.zipWithNext().all { (before, after) -> after.startedAt >= before.loggedAtEpochMillis }

/**
 * The reps a minute [sets] of one lift came to, from the first one's start to the last one logged; null for fewer
 * than two, for holds, or for sets not [loggedAsDone].
 */
fun density(sets: List<SetLog>): Double? {
    val counted = sets.filter { it.seconds == null && it.reps > 0 }.sortedBy { it.loggedAtEpochMillis }
    if (counted.size < 2 || !loggedAsDone(counted)) return null
    val span = counted.last().loggedAtEpochMillis - counted.first().startedAt
    return if (span > 0) counted.sumOf { it.reps } / (span / MILLIS_PER_MINUTE) else null
}

/** How fast an [exercise] was done at a weight, [kg] (with two bells when [double]): [perMinute] reps a minute. */
data class Pace(val exercise: Exercise, val kg: Double, val double: Boolean, val perMinute: Double)

/** The paces [session] set, one for each lift and weight it did twice or more as it was logged. */
fun paces(session: LiftSession): List<Pace> = session.sets
    .filter { it.exercise !is UnknownExercise }
    .groupBy { Triple(it.exercise.key, it.load.kg, it.double) }
    .mapNotNull { (lift, sets) ->
        density(sets)?.let { Pace(sets.first().exercise, lift.second, lift.third, it) }
    }

/** The best [Pace] of each lift and weight over [sessions]. */
fun bestPaces(sessions: List<LiftSession>): List<Pace> = sessions.flatMap(::paces)
    .groupBy { Triple(it.exercise.key, it.kg, it.double) }
    .map { (_, paces) -> paces.maxBy { it.perMinute } }

/** The paces [session] set that beat the best of [before] at the same lift and weight; a first time sets none. */
fun newPaces(before: List<LiftSession>, session: LiftSession): List<Pace> {
    val best = bestPaces(before).associateBy { Triple(it.exercise.key, it.kg, it.double) }
    return paces(session).filter { pace ->
        best[Triple(pace.exercise.key, pace.kg, pace.double)]?.let { pace.perMinute > it.perMinute } == true
    }
}

/** A pace as Lift writes it, to the tenth: "9.4". */
fun formatPace(perMinute: Double): String = formatKg((perMinute * TENTHS).roundToInt().toDouble() / TENTHS)

/** [exercise]'s best pace at any weight over [sessions]; null with none timed. */
fun bestPaceOf(exercise: Exercise, sessions: List<LiftSession>): Pace? =
    bestPaces(sessions).filter { it.exercise.key == exercise.key }.maxByOrNull { it.perMinute }
