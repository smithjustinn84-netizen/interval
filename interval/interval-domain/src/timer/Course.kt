package dev.justinsmith.interval.timer

/**
 * One interval of a workout's [Course]: its phase, the set and round it falls in, and its planned length in whole
 * seconds. Get ready and the warm-up are in the first round, the cool-down in the last, as [WorkoutState] has them.
 */
data class Interval(val phase: Phase, val set: Int, val round: Int, val seconds: Int)

/**
 * Every interval a workout passes through, in order, get ready to the cool-down, worked out once from its config:
 * the one source of what a config looks like, which the engine walks, the outline and the Timeline draw and the
 * totals sum. Each set is its rounds with the rest between them, each round's work its own on a ladder, and a
 * [Phase.SetRest] after every set but the last. A part of no length is left out, the rest after the last round too.
 */
object Course {
    fun of(config: IntervalConfig): List<Interval> = buildList {
        if (config.prepSeconds > 0) add(Interval(Phase.Prep, 1, 1, config.prepSeconds))
        if (config.warmUpSeconds > 0) add(Interval(Phase.WarmUp, 1, 1, config.warmUpSeconds))
        for (set in 1..config.sets) {
            for (round in 1..config.rounds) {
                add(Interval(Phase.Work, set, round, config.workOf(round)))
                val after = when {
                    round < config.rounds -> Phase.Rest

                    set == config.sets -> null

                    config.setRestSeconds > 0 -> Phase.SetRest

                    // A set rest of none is the ordinary rest, between the sets as between their rounds.
                    else -> Phase.Rest
                }
                val seconds = if (after == Phase.SetRest) config.setRestSeconds else config.restSeconds
                if (after != null && seconds > 0) add(Interval(after, set, round, seconds))
            }
        }
        if (config.coolDownSeconds > 0) {
            add(Interval(Phase.CoolDown, config.sets, config.rounds, config.coolDownSeconds))
        }
    }

    /** The interval after the one [state] is in; null in the last, and once done. */
    fun next(state: WorkoutState): Interval? = of(state.config).firstOrNull { it.comparedTo(state) > 0 }

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
