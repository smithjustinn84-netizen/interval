package dev.justinsmith.bells.domain

/**
 * Today's take on one of a program day's blocks, as the session changed it. [block] is the block as it now
 * stands: its load, rounds or reps changed, or another exercise swapped in. Swapped once some of its sets were
 * done, [before] is the block as it was and [beforeDone] how many of its sets were done under it: those stay
 * planned as they were, and the rounds left go to the new exercise. Skipped, it keeps only its first [kept]
 * planned sets, the ones done when it was skipped, so what comes next moves on.
 */
data class PlanBlock(
    val block: Block,
    val kept: Int? = null,
    val before: Block? = null,
    val beforeDone: Int = 0,
) {
    val skipped: Boolean get() = kept != null

    /** The sets it now comes to, in order. */
    fun plannedSets(): List<PlannedSet> {
        val sets = if (before == null) {
            block.plannedSets()
        } else {
            val left = block.rounds - roundsOf(before, beforeDone)
            before.plannedSets().take(beforeDone) +
                if (left > 0) block.copy(rounds = left).plannedSets() else emptyList()
        }
        return kept?.let { sets.take(it) } ?: sets
    }

    /** The rounds [done] of its sets reach into, those before a swap included: as few as it can be cut to. */
    fun roundsDone(done: Int): Int = if (before == null) {
        roundsOf(block, done)
    } else {
        roundsOf(before, beforeDone) + roundsOf(block, done - beforeDone)
    }

    /** Where in [plannedSets] the block as it now stands starts: after the sets done before a swap. */
    val firstOfBlock: Int get() = if (before == null) 0 else beforeDone
}

/** The rounds [sets] planned sets of [block] reach into: a round begun counts. */
fun roundsOf(block: Block, sets: Int): Int {
    if (sets <= 0) return 0
    val perRound = (block.plannedSets().size / block.rounds.coerceAtLeast(1)).coerceAtLeast(1)
    return (sets + perRound - 1) / perRound
}

/** A program day as a session has changed it, a [PlanBlock] for each of its blocks, in order. */
data class SessionPlan(val blocks: List<PlanBlock>) {
    /** With block [index] changed by [transform]: a swapped block's new exercise. */
    fun change(index: Int, transform: (Block) -> Block): SessionPlan =
        map(index) { it.copy(block = transform(it.block)) }

    /** With block [index] skipped, [done] of its sets done: the rest are dropped. */
    fun skip(index: Int, done: Int): SessionPlan = map(index) { it.copy(kept = done.coerceAtLeast(0)) }

    /** With block [index] planned in full again. */
    fun unskip(index: Int): SessionPlan = map(index) { it.copy(kept = null) }

    /**
     * With block [index]'s sets still to do swapped to [exercise], [done] of them done: those keep the
     * exercise they were done with. Once sets of a swapped-in exercise are done it stays.
     */
    fun swap(index: Int, exercise: Exercise, done: Int): SessionPlan = map(index) { entry ->
        when {
            entry.before == null && done == 0 -> entry.copy(block = entry.block.withExercise(exercise))

            entry.before == null -> entry.copy(
                block = entry.block.withExercise(exercise),
                before = entry.block,
                beforeDone = done,
            )

            done <= entry.beforeDone -> entry.copy(block = entry.block.withExercise(exercise))

            else -> entry
        }
    }

    /** Whether block [index], [done] of its sets done, can still take another exercise. */
    fun canSwap(index: Int, done: Int): Boolean {
        val entry = blocks.getOrNull(index) ?: return false
        if (entry.skipped || done >= entry.plannedSets().size) return false
        return entry.before == null || done <= entry.beforeDone
    }

    /** The blocks as they now stand, skipped ones and swaps included: what saving it to a program keeps. */
    val changedBlocks: List<Block> get() = blocks.map { it.block }

    private fun map(index: Int, transform: (PlanBlock) -> PlanBlock) =
        copy(blocks = blocks.mapIndexed { i, entry -> if (i == index) transform(entry) else entry })

    companion object {
        /** [blocks] as programmed, nothing changed. */
        fun of(blocks: List<Block>): SessionPlan = SessionPlan(blocks.map(::PlanBlock))
    }
}

/**
 * Of [planned], which are done by [logged]: the n-th planned set of a lift (and side) by the n-th logged one,
 * whatever its bell or reps, as [nextPlanned] counts them.
 */
fun plannedDone(planned: List<PlannedSet>, logged: List<SetLog>): List<Boolean> =
    plannedMatches(planned, logged).map { it != null }
