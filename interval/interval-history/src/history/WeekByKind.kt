package dev.justinsmith.interval.history

import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.timer.Activity

/** What a workout or a plan is, as the week's bars count it. */
enum class WorkoutKind {
    /** A session on Lift. */
    Strength,

    /** An interval workout that isn't mobility. */
    Conditioning,

    /** A flow, or anything else timed as mobility. */
    Mobility,

    /** A day of a sport: a class, the wall, a run. */
    Sport,
}

/** One kind's share of the week: [done] of it, against the [planned]. */
data class KindTally(val kind: WorkoutKind, val done: Int, val planned: Int) {
    /** What the bar fills toward: the plan, or what was done once more was done than planned. */
    val of: Int get() = maxOf(done, planned)
}

/**
 * The week by kind, in [WorkoutKind]'s order: each kind's workouts done and its plans on the week's days, whatever
 * became of them. A kind with nothing done and nothing planned is left out.
 */
fun List<TrainingDay>.byKind(): List<KindTally> {
    val done = mapOf(
        WorkoutKind.Strength to sumOf { it.liftWorkouts.size },
        WorkoutKind.Conditioning to sumOf { it.workouts.size - it.mobility },
        WorkoutKind.Mobility to sumOf { it.mobility },
        WorkoutKind.Sport to sumOf { it.sports.size },
    )
    val planned = flatMap { it.plans }.groupingBy { it.plan.workout.kind }.eachCount()
    return WorkoutKind.entries
        .map { KindTally(it, done[it] ?: 0, planned[it] ?: 0) }
        .filter { it.of > 0 }
}

/** The kind of workout [this] plans. */
val PlanWorkout.kind: WorkoutKind
    get() = when (this) {
        is PlanWorkout.Lift -> WorkoutKind.Strength

        is PlanWorkout.SportDay -> WorkoutKind.Sport

        is PlanWorkout.Interval ->
            if (config.activity == Activity.Mobility) WorkoutKind.Mobility else WorkoutKind.Conditioning
    }
