package dev.justinsmith.bells.domain

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

/**
 * The load to suggest for [set]: the plan's, when it names one, else what was used last in [history]. A bell
 * falls back to [bellKg], a bar to the user's [bar]; weight added to bodyweight starts at none, and a band
 * and the mat take no weight.
 */
fun suggestedLoad(set: PlannedSet, history: List<SetLog>, bar: Double?, bellKg: Double): Double =
    when (set.exercise.equipment) {
        Equipment.Kettlebell -> set.kg ?: suggestedBell(set.exercise, set.double, history, bellKg)
        Equipment.Barbell -> set.kg ?: suggestedBar(set.exercise, history, bar)
        Equipment.Bodyweight -> set.kg ?: suggestedBell(set.exercise, double = false, history, fallback = 0.0)
        Equipment.Band, Equipment.Mat -> 0.0
    }

/**
 * The band for [set], when it is band work: the one the plan names while still [offered], else the one
 * last used for it, else [fallback]. Null for any other set.
 */
fun plannedBand(set: PlannedSet, history: List<SetLog>, offered: List<Band>, fallback: Band): Band? =
    if (set.exercise.equipment == Equipment.Band) {
        offered.firstOrNull { it.id == set.band } ?: suggestedBand(set.exercise, history, offered, fallback)
    } else {
        null
    }

/** Where each block's sets start among the day's, laid end to end. */
private fun List<List<PlannedSet>>.starts(): List<Int> = runningFold(0) { at, block -> at + block.size }
