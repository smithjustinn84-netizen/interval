package dev.justinsmith.interval.timer

/**
 * One interval of a workout's [Course]: its phase, the block (0-based), set and round it falls in, and its planned
 * length in whole seconds. Get ready and the warm-up are in the first round, the cool-down in the last, as
 * [WorkoutState] has them.
 */
data class Interval(val phase: Phase, val set: Int, val round: Int, val seconds: Int, val block: Int = 0)

/**
 * Every interval a workout passes through, in order, get ready to the cool-down, worked out once from its config:
 * the one source of what a config looks like, which the engine walks, the outline and the Timeline draw and the
 * totals sum. Each block is its sets, each set its rounds with the rest between them, each round's work its own on
 * a ladder, and a [Phase.SetRest] after every set but the last. A part of no length is left out, the rest after the
 * last round too.
 */
object Course {
    fun of(config: IntervalConfig): List<Interval> = buildList {
        if (config.prepSeconds > 0) add(Interval(Phase.Prep, 1, 1, config.prepSeconds))
        if (config.warmUpSeconds > 0) add(Interval(Phase.WarmUp, 1, 1, config.warmUpSeconds))
        config.blocks.forEachIndexed { index, block ->
            addAll(rounds(block, index))
            if (index < config.blocks.lastIndex) {
                // Between blocks: the rest between them, or, of none, the block's own rest between its rounds.
                val (phase, seconds) = if (config.blockRestSeconds > 0) {
                    Phase.SetRest to config.blockRestSeconds
                } else {
                    Phase.Rest to block.restSeconds
                }
                if (seconds > 0) add(Interval(phase, block.sets, block.rounds, seconds, index))
            }
        }
        if (config.coolDownSeconds > 0) {
            val last = config.blocks.last()
            add(Interval(Phase.CoolDown, last.sets, last.rounds, config.coolDownSeconds, config.blocks.lastIndex))
        }
    }

    /** [block]'s sets of rounds, the [index]th of the workout's. */
    private fun rounds(block: Block, index: Int): List<Interval> = buildList {
        for (set in 1..block.sets) {
            for (round in 1..block.rounds) {
                add(Interval(Phase.Work, set, round, block.workOf(round), index))
                val after = when {
                    round < block.rounds -> Phase.Rest

                    set == block.sets -> null

                    block.setRestSeconds > 0 -> Phase.SetRest

                    // A set rest of none is the ordinary rest, between the sets as between their rounds.
                    else -> Phase.Rest
                }
                val seconds = if (after == Phase.SetRest) block.setRestSeconds else block.restSeconds
                if (after != null && seconds > 0) add(Interval(after, set, round, seconds, index))
            }
        }
    }

    /** The interval after the one [state] is in; null in the last, and once done. */
    fun next(state: WorkoutState): Interval? = of(state.config).firstOrNull { it.comparedTo(state) > 0 }

    /** The seconds of work in [config]'s course. */
    fun workSeconds(config: IntervalConfig): Int = of(config).filter { it.phase == Phase.Work }.sumOf { it.seconds }

    /** The work intervals of [state]'s course behind it: those before its place, and its own once it is past it. */
    fun workBehind(state: WorkoutState): Int =
        of(state.config).count { it.phase == Phase.Work && it.comparedTo(state) < 0 }
}

/**
 * Where this interval stands against [state] in the course's order, which is by block, then set, then round, then
 * phase ([Phase] lists a round's phases in the order they run): below 0 when this one comes first, 0 when it is the
 * state's own. So a state the course doesn't hold, such as [Phase.Done], still falls between two intervals.
 */
internal fun Interval.comparedTo(state: WorkoutState): Int = when {
    block != state.block -> block.compareTo(state.block)
    set != state.set -> set.compareTo(state.set)
    round != state.round -> round.compareTo(state.round)
    else -> phase.compareTo(state.phase)
}
