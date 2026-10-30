package dev.justinsmith.lift.domain

/**
 * The next set of a program day: the [index]-th (from 0) of its [total], in its [block]-th block (from 0),
 * and, when it opens an EMOM, the [emomMinutes] the EMOM runs for.
 */
data class NextSet(val set: PlannedSet, val index: Int, val total: Int, val block: Int, val emomMinutes: Int?)

/**
 * One of the day's blocks as today's plan has it: [done] of its [total] sets done, and whether the session
 * [changed] it from the program (its load, its count or its exercise).
 */
data class PlanRow(val entry: PlanBlock, val done: Int, val total: Int, val changed: Boolean) {
    val block: Block get() = entry.block

    /** Whether something is left to change: not every set done, or skipped and so to bring back. */
    val open: Boolean get() = entry.skipped || done < total
}

/** The set to do next once [logged] are done; null once every set of the day is. */
fun ProgramDay.nextSet(logged: List<SetLog>): NextSet? {
    val blocks = blockSets()
    val planned = blocks.flatten()
    val index = nextPlanned(planned, logged) ?: return null
    val starts = blocks.starts()
    val block = starts.indexOfLast { it <= index }
    // An EMOM's first minute opens it; after a swap, the first minute of the exercise swapped in.
    val entry = today.blocks[block]
    val emomMinutes = if (entry.block.kind == BlockKind.Emom && index == starts[block] + entry.firstOfBlock) {
        blocks[block].size - entry.firstOfBlock
    } else {
        null
    }
    return NextSet(planned[index], index, planned.size, block, emomMinutes)
}

/** The day's blocks as today's plan has them, with the sets of each [logged] has done. */
fun ProgramDay.planRows(logged: List<SetLog>): List<PlanRow> {
    val sets = blockSets()
    val done = plannedDone(sets.flatten(), logged)
    val starts = sets.starts()
    return today.blocks.mapIndexed { i, entry ->
        val size = sets[i].size
        val count = done.subList(starts[i], starts[i] + size).count { it }
        PlanRow(entry, count, size, changed = entry.block != blocks.getOrNull(i))
    }
}

/** Where each block's sets start among the day's, laid end to end. */
private fun List<List<PlannedSet>>.starts(): List<Int> = runningFold(0) { at, block -> at + block.size }

/**
 * Of [planned], the first set not yet done by [logged]: the n-th planned set of a lift (and side) is done by
 * the n-th logged one, whatever its bell or reps, so going off plan never blocks what comes next. A set
 * with two bells has no side, so for a one-sided lift a planned double set is done only by a double one; for
 * a lift with no sides, one bell or two does it.
 */
fun nextPlanned(planned: List<PlannedSet>, logged: List<SetLog>): Int? =
    plannedDone(planned, logged).indexOfFirst { !it }.takeIf { it >= 0 }
