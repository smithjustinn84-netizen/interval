package dev.justinsmith.lift.domain

/**
 * Of [planned], the set [logged] did each, or null for one not done: the n-th planned set of a lift (and side)
 * by the n-th logged one, as [nextPlanned] counts them.
 */
fun plannedMatches(planned: List<PlannedSet>, logged: List<SetLog>): List<SetLog?> =
    plannedRounds(planned, logged).map { it.firstOrNull() }

/**
 * Of [planned], the sets [logged] did each: one at most, as [plannedMatches] has it, except a density block's first
 * round ([PlannedSet.capMinutes]). That is the first set of its lift left on either side, and it takes every later
 * one logged within the cap of it, timed from that round's log: never earlier than the clock's, so a round tapped
 * as the cap ends counts however quick the first was, and a round past it counts toward the block after. Empty
 * for one not done.
 */
fun plannedRounds(planned: List<PlannedSet>, logged: List<SetLog>): List<List<SetLog>> {
    val byKey = logged.groupBy { it.exercise.key to it.side }.mapValues { (_, sets) -> ArrayDeque(sets) }
    return planned.map { set ->
        val cap = set.capMinutes
            ?: return@map listOfNotNull(byKey[set.exercise.key to set.side]?.removeFirstOrNull())
        val ofLift = byKey.filterKeys { (key, _) -> key == set.exercise.key }.values
        val first = ofLift.mapNotNull { it.firstOrNull() }.minByOrNull { it.loggedAtEpochMillis }
            ?: return@map emptyList()
        val until = first.loggedAtEpochMillis + cap * EmomClock.MINUTE_MILLIS
        ofLift.flatMap { sets ->
            sets.filter { it.loggedAtEpochMillis in first.loggedAtEpochMillis..until }.also { sets.removeAll(it) }
        }.sortedBy { it.loggedAtEpochMillis }
    }
}

/** What weight added to bodyweight goes up by. */
const val ADDED_KG_STEP = 2.5

/** A hold goes up by five seconds, up to ten minutes, as the Log sheet steps it. */
private const val HOLD_STEP = 5
private const val HOLD_MAX = 600

/** An EMOM runs no longer than an hour, as the Log sheet allows. */
private const val EMOM_MAX_MINUTES = 60

/** Where a block done in full stands: a [Step] to take next time, or [ToppedOut], nothing left to step to. */
sealed interface Rung {
    data class Step(val to: Block) : Rung

    /** Every step at this load used, and nothing heavier to go to. */
    data object ToppedOut : Rung
}

/**
 * What [block] could ask for next time, [done] being the set logged for each of its planned sets: null unless
 * every one was done, at its reps (or a hold's seconds) or more and at its load or more; see [rungOf].
 */
fun nextStep(
    block: Block,
    done: List<SetLog?>,
    bells: List<Double>,
    bands: List<Band>,
    pairs: Set<Double> = emptySet(),
    rest: Int = RestClock.DEFAULT_SECONDS,
): Block? = (rungOf(block, done, bells, bands, pairs, rest) as? Rung.Step)?.to

/**
 * Where [block] stands, [done] being the set logged for each of its planned sets: null unless every one was done at
 * its plan (a ladder's every rung), and for a density block, which steps by its rounds ([densityStep]). The load is
 * the plan's, or with none named the lightest done.
 *
 * Bells come in fixed sizes, so a bell lift climbs at the same bell first, by how big the jump to the next one of
 * [bells] is ([SMALL_JUMP], [BIG_JUMP]), each rung tried in turn:
 * 1. a block building within a [Block.ranged] range, a rep more until its top;
 * 2. denser, before a medium or big jump: rest [RestClock.STEP_SECONDS] shorter (its own, else [rest]), down to 30
 *    seconds for a thrown lift and 60 for any other; an EMOM a rep more a minute instead, up to 10 or 5;
 * 3. more, before a medium or big jump: a set, up to 10 or 5; an EMOM a minute, up to 30; a ladder another, up to 5,
 *    then one that climbs from 1 a rung, up to 5;
 * 4. before a big jump, or with none heavier owned, a harder lift at the same bell ([variationOf]), once;
 * 5. the next bell, back as the block started ([Block.origin]), its own lift again, at the bottom of its range.
 * With no bell heavier and nothing left, it has [Rung.ToppedOut], except sets with no range, which take a rep more.
 *
 * Every other load keeps its own steps: a hold five seconds more; within a range a rep more, then the next load at
 * its bottom; else the next load: [BAR_KG_STEP] more on the bar, [ADDED_KG_STEP] more added to bodyweight, the next of
 * [bands] heavier, else a rep more; an EMOM a minute more; a ladder another, then a rung.
 *
 * A block done with two bells steps only to a weight of [pairs], the bells owned two of, once the user has said
 * which those are; with none said, to any of [bells]. It moves to two bells at the same bell only once [pairs] has it.
 */
@Suppress("detekt.LongParameterList") // What a step up goes by.
fun rungOf(
    block: Block,
    done: List<SetLog?>,
    bells: List<Double>,
    bands: List<Band>,
    pairs: Set<Double> = emptySet(),
    rest: Int = RestClock.DEFAULT_SECONDS,
): Rung? {
    if (block.kind == BlockKind.Density) return null
    val sets = doneInFull(block, done) ?: return null
    val at = loadDone(block, sets, bands) ?: return null
    return when {
        block.exercise.measure == Measure.Time ->
            block.copy(reps = (block.reps + HOLD_STEP).coerceAtMost(HOLD_MAX)).takeIf { it != block }?.let(Rung::Step)

        at is Load.Bell -> bellRung(block, at, bells, pairs, rest)

        else -> otherRung(block, at, bands)
    }
}

/** [block], done at [at], a load other than a bell, a rung up, as [rungOf] lays its steps out. */
private fun otherRung(block: Block, at: Load, bands: List<Band>): Rung? {
    val range = block.ranged
    val next = when {
        range != null && block.reps < range.top -> block.copy(reps = block.reps + 1)

        range != null -> return heavier(block, at, bands)?.copy(reps = range.bottom, rest = null)?.let(Rung::Step)
            ?: Rung.ToppedOut

        block.kind == BlockKind.Emom ->
            block.copy(rounds = (block.rounds + 1).coerceAtMost(EMOM_MAX_MINUTES)).takeIf { it != block }

        block.kind == BlockKind.Ladder -> moreLadder(block)

        block.kind == BlockKind.Sets -> heavier(block, at, bands)?.copy(rest = null)
            ?: block.copy(reps = block.reps + 1)

        else -> null
    }
    return next?.let(Rung::Step)
}

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

/**
 * [block] at the next load up from [at], not a bell: [BAR_KG_STEP] more on the bar, [ADDED_KG_STEP] more added to
 * bodyweight, the next of [bands] heavier. Null with none heavier, or none to add.
 */
private fun heavier(block: Block, at: Load, bands: List<Band>): Block? {
    val next = when (at) {
        is Load.Bar -> (at.kg + BAR_KG_STEP).coerceAtMost(MAX_BAR_KG).takeIf { it > at.kg }?.let(Load::Bar)

        is Load.Bodyweight -> Load.Bodyweight(at.kg + ADDED_KG_STEP).takeIf { at.kg > 0 }

        is Load.Banded -> at.band?.let { b -> bands.firstOrNull { it.position > b.position && !it.archived } }
            ?.let(Load::Banded)

        is Load.Bell, Load.Mat -> null
    }
    return next?.let { block.copy(load = it) }
}

/** The sets [done] for [block], when every one was done and met its plan; else null. */
internal fun doneInFull(block: Block, done: List<SetLog?>): List<SetLog>? =
    done.filterNotNull().takeIf { it.isNotEmpty() && it.size == done.size && metPlan(block, it) }

/**
 * Whether every one of [sets], the n-th done for the n-th of [block]'s planned sets, was at its reps (a hold's
 * seconds, a ladder's rung) or more, and the block's load or more.
 */
private fun metPlan(block: Block, sets: List<SetLog>): Boolean {
    val held = block.exercise.measure == Measure.Time
    val planned = block.plannedSets()
    val short = sets.withIndex().any { (i, set) ->
        (if (held) set.seconds ?: 0 else set.reps) < (planned.getOrNull(i)?.reps ?: block.reps)
    }
    if (short) return false
    val load = block.load ?: return true
    // A band weighs nothing, so none is under it here: whether one was lighter is bandUsed's to say.
    return sets.none { it.load.kg < load.kg }
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
 * at its plan, as [nextStep] has it, from the block as it was done. A block swapped part way earns none. A density
 * block steps by [densityStep], its rounds today after those in the sessions [before] this one.
 */
@Suppress("detekt.LongParameterList") // What a step up goes by.
fun progressions(
    day: ProgramDay,
    logged: List<SetLog>,
    bells: List<Double>,
    bands: List<Band>,
    pairs: Set<Double> = emptySet(),
    rest: Int = RestClock.DEFAULT_SECONDS,
    before: List<LiftSession> = emptyList(),
): List<BlockChange> =
    // A test is done as it stands each time, or two attempts measure different things.
    if (day.program.test) {
        emptyList()
    } else {
        blocksDone(day, logged).mapNotNull { done ->
            val block = done.block
            val next = if (block.kind == BlockKind.Density) {
                densityStep(block, done.done, densityRounds(block, day.program, before) + done.rounds)
            } else {
                nextStep(block, done.done, bells, bands, pairs, rest)
            }
            next?.let { BlockChange(done.index, block, it) }
        }
    }

/**
 * Of [day]'s blocks, done as [logged], those with every step at their load used and nothing heavier to go to:
 * [Rung.ToppedOut]. They have earned more than [rungOf] can offer.
 */
@Suppress("detekt.LongParameterList") // What a step up goes by.
fun toppedOut(
    day: ProgramDay,
    logged: List<SetLog>,
    bells: List<Double>,
    bands: List<Band>,
    pairs: Set<Double> = emptySet(),
    rest: Int = RestClock.DEFAULT_SECONDS,
): List<Block> =
    if (day.program.test) {
        emptyList()
    } else {
        blocksDone(day, logged).mapNotNull { (_, block, done) ->
            block.takeIf { rungOf(block, done, bells, bands, pairs, rest) == Rung.ToppedOut }
        }
    }

/**
 * Block [index] of a day's plan, the set logged for each of its planned sets (null for one not done), and the
 * [rounds] it came to: the sets logged for it, a density block's every round.
 */
internal data class BlockDone(val index: Int, val block: Block, val done: List<SetLog?>, val rounds: Int)

/** Each block of today's plan neither swapped part way nor skipped, with what was done of it. */
internal fun blocksDone(day: ProgramDay, logged: List<SetLog>): List<BlockDone> {
    val sets = day.blockSets()
    val matches = plannedRounds(sets.flatten(), logged)
    var start = 0
    return day.today.blocks.mapIndexedNotNull { i, entry ->
        val done = matches.subList(start, start + sets[i].size)
        start += sets[i].size
        BlockDone(i, entry.block, done.map { it.firstOrNull() }, done.sumOf { it.size })
            .takeIf { entry.before == null && !entry.skipped }
    }
}
