package dev.justinsmith.interval.timer

/**
 * One interval of a workout's [Course]: its phase, the set and round it falls in, and its planned length in whole
 * seconds. Get ready and the warm-up are in the first round, the cool-down in the last, as [WorkoutState] has them.
 */
data class Interval(val phase: Phase, val set: Int, val round: Int, val seconds: Int)

/**
 * Every interval a workout passes through, in order, get ready to the cool-down, worked out once from its config:
 * the one source of what a config looks like, which the engine walks, the outline and the Timeline draw and the
 * totals sum. A part of no length is left out, the rest after the last round too.
 */
object Course {
    fun of(config: IntervalConfig): List<Interval> = buildList {
        if (config.prepSeconds > 0) add(Interval(Phase.Prep, 1, 1, config.prepSeconds))
        if (config.warmUpSeconds > 0) add(Interval(Phase.WarmUp, 1, 1, config.warmUpSeconds))
        for (round in 1..config.rounds) {
            add(Interval(Phase.Work, 1, round, config.workSeconds))
            if (round < config.rounds && config.restSeconds > 0) add(Interval(Phase.Rest, 1, round, config.restSeconds))
        }
        if (config.coolDownSeconds > 0) add(Interval(Phase.CoolDown, 1, config.rounds, config.coolDownSeconds))
    }

    /** The seconds of work in [config]'s course. */
    fun workSeconds(config: IntervalConfig): Int = of(config).filter { it.phase == Phase.Work }.sumOf { it.seconds }
}

/**
 * Where this interval stands against [state] in the course's order, which is by set, then round, then phase
 * ([Phase] lists a round's phases in the order they run): below 0 when this one comes first, 0 when it is the
 * state's own. So a state the course doesn't hold, such as [Phase.Done], still falls between two intervals.
 */
internal fun Interval.comparedTo(state: WorkoutState): Int = when {
    set != state.set -> set.compareTo(state.set)
    round != state.round -> round.compareTo(state.round)
    else -> phase.compareTo(state.phase)
}
