package dev.justinsmith.interval.timer

/**
 * One block of a workout's rounds: work and rest for so many rounds, run in sets with a longer rest between them,
 * each round's work a ladder's step on from the one before. A workout is its bookends around its blocks
 * ([IntervalConfig.blocks]), one for every workout today. Whole seconds, each value in [IntervalConfig]'s ranges.
 */
data class Block(
    val workSeconds: Int,
    val restSeconds: Int,
    val rounds: Int,
    val sets: Int = 1,
    /** The longer rest after every set but the last, 0 for an ordinary rest between the sets. */
    val setRestSeconds: Int = 0,
    /** The seconds each round works more than the one before (less when below 0), starting over with each set. */
    val workStepSeconds: Int = 0,
) {
    /** A ladder: the rounds' work climbs or falls. */
    val isLadder: Boolean get() = workStepSeconds != 0 && rounds > 1

    /** The work of [round] (1-based) of each set: [workSeconds], and a [workStepSeconds] more each round after. */
    fun workOf(round: Int): Int = workSeconds + (round - 1) * workStepSeconds
}
