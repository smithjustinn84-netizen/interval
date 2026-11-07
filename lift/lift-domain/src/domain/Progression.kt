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

/** The next bell this much heavier is a big jump: the rest shortens first, the same work done denser. */
private const val BIG_JUMP = 1.2

/** The shortest a denser block rests: a thrown lift's, and any other's. */
private const val BALLISTIC_REST_FLOOR = 30
private const val GRIND_REST_FLOOR = 60

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
 *
 * Bells come in fixed sizes. Where a bell lift in sets would step to a bell more than a fifth heavier, it is done
 * denser first: its rest ([Block.rest], else the [rest] set on Lift) [RestClock.STEP_SECONDS] shorter each time, down
 * to 30 seconds for a thrown lift and 60 for any other; only then the bell, its rest back to Lift's.
 *
 * A block done with two bells steps only to a weight of [pairs], the bells owned two of, once the user has said
 * which those are; with none said, to any of [bells].
 */
fun nextStep(
    block: Block,
    done: List<SetLog?>,
    bells: List<Double>,
    bands: List<Band>,
    pairs: Set<Double> = emptySet(),
    rest: Int = RestClock.DEFAULT_SECONDS,
): Block? {
    if (block.kind == BlockKind.Ladder) return null
    val sets = doneInFull(block, done) ?: return null
    return stepUp(block, loadDone(block, sets, bands) ?: return null, bellsFor(block, bells, pairs), bands, rest)
}

/** The bells [block] can step to: for two bells, only those owned two of, once any are; else any of [bells]. */
private fun bellsFor(block: Block, bells: List<Double>, pairs: Set<Double>): List<Double> =
    if (block.double && pairs.isNotEmpty()) bells.filter { it in pairs } else bells

/**
 * The load [block] was done at in [sets], of its lift's equipment: the plan's, or with none named the lightest
 * done. Band work was done at the plan's band if every set used it or a heavier one, else at the lightest used;
 * null when a set of it kept no band.
 */
private fun loadDone(block: Block, sets: List<SetLog>, bands: List<Band>): Load? {
    val kg = block.load?.kg ?: sets.minOf { it.load.kg }
    return when (val at = Load.of(block.exercise.equipment, kg, block.double)) {
        is Load.Banded -> bandUsed(block, sets, bands)?.let(Load::Banded)
        is Load.Bell, is Load.Bar, is Load.Bodyweight, Load.Mat -> at
    }
}

/** [block] a step up from [at], the load it was done at, as [nextStep] lays the steps out. */
private fun stepUp(block: Block, at: Load, bells: List<Double>, bands: List<Band>, rest: Int): Block? {
    block.ranged?.let { range ->
        return if (block.reps < range.top) {
            block.copy(reps = block.reps + 1)
        } else {
            denser(block, at, bells, rest) ?: heavier(block, at, bells, bands)?.copy(reps = range.bottom, rest = null)
        }
    }
    return when {
        block.exercise.measure == Measure.Time ->
            block.copy(reps = (block.reps + HOLD_STEP).coerceAtMost(HOLD_MAX)).takeIf { it != block }

        block.kind == BlockKind.Emom ->
            block.copy(rounds = (block.rounds + 1).coerceAtMost(EMOM_MAX_MINUTES)).takeIf { it != block }

        else -> denser(block, at, bells, rest)
            ?: heavier(block, at, bells, bands)?.copy(rest = null)
            ?: block.copy(reps = block.reps + 1)
    }
}

/**
 * [block], a bell lift in sets, resting [RestClock.STEP_SECONDS] less than it does now (its own, else [rest]), when
 * the next of [bells] after [at] is a big jump and the rest is above its floor; else null.
 */
private fun denser(block: Block, at: Load, bells: List<Double>, rest: Int): Block? {
    if (block.kind != BlockKind.Sets || at !is Load.Bell) return null
    val next = bells.filter { it > at.kg }.minOrNull() ?: return null
    if (next <= at.kg * BIG_JUMP) return null
    val floor = if (block.exercise.movement.tempo == Tempo.Ballistic) BALLISTIC_REST_FLOOR else GRIND_REST_FLOOR
    return ((block.rest ?: rest) - RestClock.STEP_SECONDS).takeIf { it >= floor }?.let { block.copy(rest = it) }
}

/**
 * [block] at the next load up from [at]: the next of [bells] heavier, [BAR_KG_STEP] more on the bar,
 * [ADDED_KG_STEP] more added to bodyweight, the next of [bands] heavier. Null with none heavier, or none to add.
 */
private fun heavier(block: Block, at: Load, bells: List<Double>, bands: List<Band>): Block? {
    val next = when (at) {
        is Load.Bell -> bells.filter { it > at.kg }.minOrNull()?.let { at.copy(kg = it) }

        is Load.Bar -> (at.kg + BAR_KG_STEP).coerceAtMost(MAX_BAR_KG).takeIf { it > at.kg }?.let(Load::Bar)

        is Load.Bodyweight -> Load.Bodyweight(at.kg + ADDED_KG_STEP).takeIf { at.kg > 0 }

        is Load.Banded -> at.band?.let { b -> bands.firstOrNull { it.position > b.position && !it.archived } }
            ?.let(Load::Banded)

        Load.Mat -> null
    }
    return next?.let { block.copy(load = it) }
}

/** The sets [done] for [block], when every one was done and met its plan; else null. */
private fun doneInFull(block: Block, done: List<SetLog?>): List<SetLog>? =
    done.filterNotNull().takeIf { it.isNotEmpty() && it.size == done.size && metPlan(block, it) }

/** Whether every one of [sets] was at [block]'s reps (a hold's seconds) or more, and its load or more. */
private fun metPlan(block: Block, sets: List<SetLog>): Boolean {
    val held = block.exercise.measure == Measure.Time
    if (sets.any { (if (held) it.seconds ?: 0 else it.reps) < block.reps }) return false
    val planned = block.load ?: return true
    // A band weighs nothing, so none is under it here: whether one was lighter is bandUsed's to say.
    return sets.none { it.load.kg < planned.kg }
}

/** The band [block] was done with: the plan's, if every set used it or a heavier one, else the lightest used. */
private fun bandUsed(block: Block, sets: List<SetLog>, bands: List<Band>): Band? {
    val used = sets.map { set -> set.load.band?.let { b -> bands.firstOrNull { it.id == b.id } ?: b } ?: return null }
    val lightest = used.minBy { it.position }
    val planned = block.load?.band?.let { named -> bands.firstOrNull { it.id == named.id } } ?: return lightest
    return planned.takeIf { lightest.position >= planned.position }
}

/**
 * The step up each of [day]'s blocks earned, done as [logged]: one for each block of today's plan done in full
 * at its plan, as [nextStep] has it, from the block as it was done. A block swapped part way earns none.
 */
fun progressions(
    day: ProgramDay,
    logged: List<SetLog>,
    bells: List<Double>,
    bands: List<Band>,
    pairs: Set<Double> = emptySet(),
    rest: Int = RestClock.DEFAULT_SECONDS,
): List<BlockChange> =
    blocksDone(day, logged).mapNotNull { (i, block, done) ->
        nextStep(block, done, bells, bands, pairs, rest)?.let { BlockChange(i, block, it) }
    }

/**
 * Of [day]'s blocks, done as [logged], those done in full at the top of their [Block.ranged] range with no
 * heavier load to go to: none of [bells] or [bands] heavier, nor more to add. They have earned more than
 * [nextStep] can offer.
 */
fun toppedOut(
    day: ProgramDay,
    logged: List<SetLog>,
    bells: List<Double>,
    bands: List<Band>,
    pairs: Set<Double> = emptySet(),
): List<Block> =
    blocksDone(day, logged).mapNotNull { (_, block, done) ->
        val range = block.ranged?.takeIf { block.reps >= it.top } ?: return@mapNotNull null
        val sets = doneInFull(block, done) ?: return@mapNotNull null
        // Done with a lighter band than planned is short of the plan, not at the top of it.
        val at = loadDone(block, sets, bands) ?: return@mapNotNull null
        block.takeIf { heavier(block, at, bellsFor(block, bells, pairs), bands) == null }
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
