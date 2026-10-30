package dev.justinsmith.lift.domain

/**
 * Of [planned], the set [logged] did each, or null for one not done: the n-th planned set of a lift (and side)
 * by the n-th logged one, as [nextPlanned] counts them.
 */
fun plannedMatches(planned: List<PlannedSet>, logged: List<SetLog>): List<SetLog?> {
    val byKey = logged.groupBy { it.exercise.key to it.side }.mapValues { (_, sets) -> ArrayDeque(sets) }
    return planned.map { set -> byKey[set.exercise.key to set.side]?.removeFirstOrNull() }
}

/** What weight added to bodyweight goes up by. */
const val ADDED_KG_STEP = 2.5

/** A hold goes up by five seconds, up to ten minutes, as the Log sheet steps it. */
private const val HOLD_STEP = 5
private const val HOLD_MAX = 600

/** An EMOM runs no longer than an hour, as the Log sheet allows. */
private const val EMOM_MAX_MINUTES = 60

/**
 * What [block] could ask for next time, [done] being the set logged for each of its planned sets: null unless
 * every one was done, at its reps (or a hold's seconds) or more and at its load or more. The load is the plan's,
 * or with none named the lightest done. Next time is a step up:
 * - a block building within a [Block.ranged] range, a rep more until its top, then the next load at the
 *   range's bottom; at the top with no heavier load to go to, nothing;
 * - a kettlebell lift in sets, the next bell of [bells] heavier; with none, a rep more;
 * - a barbell lift, [BAR_KG_STEP] more on the bar;
 * - weight added to bodyweight, [ADDED_KG_STEP] more; bodyweight without, and the mat, a rep more;
 * - a band, the next of [bands] heavier; with none, a rep more;
 * - a hold, five seconds more; an EMOM, a minute more; a ladder, nothing.
 */
fun nextStep(block: Block, done: List<SetLog?>, bells: List<Double>, bands: List<Band>): Block? {
    if (block.kind == BlockKind.Ladder) return null
    val sets = doneInFull(block, done) ?: return null
    val equipment = block.exercise.equipment
    val band = if (equipment == Equipment.Band) bandUsed(block, sets, bands) ?: return null else null
    return stepUp(block, kg = block.kg ?: sets.minOf { it.bellKg }, band, bells, bands)
}

/** [block] a step up from [kg] or [band], as [nextStep] lays the steps out. */
private fun stepUp(block: Block, kg: Double, band: Band?, bells: List<Double>, bands: List<Band>): Block? {
    block.ranged?.let { range ->
        return if (block.reps < range.top) {
            block.copy(reps = block.reps + 1)
        } else {
            heavier(block, kg, band, bells, bands)?.copy(reps = range.bottom)
        }
    }
    return when {
        block.exercise.measure == Measure.Time ->
            block.copy(reps = (block.reps + HOLD_STEP).coerceAtMost(HOLD_MAX)).takeIf { it != block }

        block.kind == BlockKind.Emom ->
            block.copy(rounds = (block.rounds + 1).coerceAtMost(EMOM_MAX_MINUTES)).takeIf { it != block }

        else -> heavier(block, kg, band, bells, bands) ?: block.copy(reps = block.reps + 1)
    }
}

/**
 * [block] at the next load up from [kg] or [band]: the next of [bells] heavier, [BAR_KG_STEP] more on the bar,
 * [ADDED_KG_STEP] more added to bodyweight, the next of [bands] heavier. Null with none heavier, or none to add.
 */
private fun heavier(block: Block, kg: Double, band: Band?, bells: List<Double>, bands: List<Band>): Block? =
    when (block.exercise.equipment) {
        Equipment.Kettlebell -> bells.filter { it > kg }.minOrNull()?.let { block.copy(kg = it) }

        Equipment.Barbell -> (kg + BAR_KG_STEP).coerceAtMost(MAX_BAR_KG).takeIf { it > kg }
            ?.let { block.copy(kg = it) }

        Equipment.Bodyweight -> block.copy(kg = kg + ADDED_KG_STEP).takeIf { kg > 0 }

        Equipment.Band -> band?.let { b -> bands.firstOrNull { it.position > b.position && !it.archived } }
            ?.let { block.copy(band = it.id) }

        Equipment.Mat -> null
    }

/** The sets [done] for [block], when every one was done and met its plan; else null. */
private fun doneInFull(block: Block, done: List<SetLog?>): List<SetLog>? =
    done.filterNotNull().takeIf { it.isNotEmpty() && it.size == done.size && metPlan(block, it) }

/** Whether every one of [sets] was at [block]'s reps (a hold's seconds) or more, and its load or more. */
private fun metPlan(block: Block, sets: List<SetLog>): Boolean {
    val held = block.exercise.measure == Measure.Time
    if (sets.any { (if (held) it.seconds ?: 0 else it.reps) < block.reps }) return false
    val kg = block.kg ?: return true
    return !block.exercise.equipment.takesKg || sets.none { it.bellKg < kg }
}

/** The band [block] was done with: the plan's, if every set used it or a heavier one, else the lightest used. */
private fun bandUsed(block: Block, sets: List<SetLog>, bands: List<Band>): Band? {
    val used = sets.map { set -> set.band?.let { b -> bands.firstOrNull { it.id == b.id } ?: b } ?: return null }
    val lightest = used.minBy { it.position }
    val planned = block.band?.let { id -> bands.firstOrNull { it.id == id } } ?: return lightest
    return planned.takeIf { lightest.position >= planned.position }
}

/**
 * The step up each of [day]'s blocks earned, done as [logged]: one for each block of today's plan done in full
 * at its plan, as [nextStep] has it, from the block as it was done. A block swapped part way earns none.
 */
fun progressions(day: ProgramDay, logged: List<SetLog>, bells: List<Double>, bands: List<Band>): List<BlockChange> =
    blocksDone(day, logged).mapNotNull { (i, block, done) ->
        nextStep(block, done, bells, bands)?.let { BlockChange(i, block, it) }
    }

/**
 * Of [day]'s blocks, done as [logged], those done in full at the top of their [Block.ranged] range with no
 * heavier load to go to: none of [bells] or [bands] heavier, nor more to add. They have earned more than
 * [nextStep] can offer.
 */
fun toppedOut(day: ProgramDay, logged: List<SetLog>, bells: List<Double>, bands: List<Band>): List<Block> =
    blocksDone(day, logged).mapNotNull { (_, block, done) ->
        val range = block.ranged?.takeIf { block.reps >= it.top } ?: return@mapNotNull null
        val sets = doneInFull(block, done) ?: return@mapNotNull null
        // Done with a lighter band than planned is short of the plan, not at the top of it.
        val band = if (block.exercise.equipment ==
            Equipment.Band
        ) {
            bandUsed(block, sets, bands) ?: return@mapNotNull null
        } else {
            null
        }
        block.takeIf { heavier(block, block.kg ?: sets.minOf { it.bellKg }, band, bells, bands) == null }
    }

/** Block [index] of a day's plan, and the set logged for each of its planned sets, null for one not done. */
private data class BlockDone(val index: Int, val block: Block, val done: List<SetLog?>)

/** Each block of today's plan neither swapped part way nor skipped, with what was done of it. */
private fun blocksDone(day: ProgramDay, logged: List<SetLog>): List<BlockDone> {
    val sets = day.blockSets()
    val matches = plannedMatches(sets.flatten(), logged)
    var start = 0
    return day.today.blocks.mapIndexedNotNull { i, entry ->
        val done = matches.subList(start, start + sets[i].size)
        start += sets[i].size
        BlockDone(i, entry.block, done).takeIf { entry.before == null && !entry.skipped }
    }
}
