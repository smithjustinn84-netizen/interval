package dev.justinsmith.lift.domain

// The ladder a bell lift climbs at the bell it has before the next, which comes in fixed sizes (rungOf, in
// Progression.kt; docs/kettlebell-coaching.md, section 4).

/** The next bell up to this much heavier is a small jump: taken as soon as the block has earned a step. */
const val SMALL_JUMP = 1.1

/**
 * The next bell more than this much heavier is a big jump: the block goes denser, does more, then a harder lift at
 * the same bell, before it. Up to it (and over [SMALL_JUMP]) a medium one: denser and more, then the bell.
 */
const val BIG_JUMP = 1.2

/** The shortest a denser block rests: a thrown lift's, and any other's. */
private const val BALLISTIC_REST_FLOOR = 30
private const val GRIND_REST_FLOOR = 60

/** The most sets a block of a bell lift adds up to: a thrown lift's, and any other's. */
private const val BALLISTIC_MAX_SETS = 10
private const val GRIND_MAX_SETS = 5

/** The most reps a minute an EMOM of a bell lift builds to before it adds minutes: a thrown lift's, any other's. */
private const val BALLISTIC_MAX_EMOM_REPS = 10
private const val GRIND_MAX_EMOM_REPS = 5

/** An EMOM of a bell lift adds minutes up to half an hour before the next bell. */
private const val BELL_EMOM_MAX_MINUTES = 30

/** A ladder adds ladders up to five, and one that climbs from 1 adds rungs up to five, as the Rite of Passage. */
private const val MAX_LADDERS = 5
private const val MAX_RUNG = 5

/** What kind of step a block took: as [stepKind] tells it from the block before and after. */
enum class StepKind {
    /** A rep more, or a hold's seconds. */
    Reps,

    /** The same work in less rest. */
    Denser,

    /** More work at the same load: a set, a minute, a ladder, a rung, or reps a minute. */
    Volume,

    /** A harder lift, or two bells, at the same bell. */
    Variation,

    /** The next bell, back where the block started. */
    Bell,

    /** More weight of another kind: the bar, weight added, a band. */
    Load,
}

/** How big the jump to a heavier bell is. */
private enum class Jump { Small, Medium, Big, None }

private fun jumpTo(from: Double, next: Double?): Jump = when {
    next == null -> Jump.None
    next <= from * SMALL_JUMP -> Jump.Small
    next <= from * BIG_JUMP -> Jump.Medium
    else -> Jump.Big
}

/** [block], a bell lift done at [at], a rung up, as [rungOf] lays the rungs out. */
internal fun bellRung(block: Block, at: Load.Bell, bells: List<Double>, pairs: Set<Double>, rest: Int): Rung {
    val start = block.startingShape()
    val next = bellsFor(start, bells, pairs).filter { it > at.kg }.minOrNull()
    val jump = jumpTo(at.kg, next)
    val reps = block.ranged?.takeIf { block.reps < it.top }?.let { block.copy(reps = block.reps + 1) }
    val atThisBell = when (jump) {
        Jump.Small -> null
        Jump.Medium -> denserOrMore(block, rest)
        Jump.Big, Jump.None -> denserOrMore(block, rest) ?: variation(block, at, pairs)
    }
    val climb = reps ?: atThisBell
    return when {
        climb != null -> Rung.Step(climb)
        next != null -> Rung.Step(start.withLoad(Load.Bell(next, start.double)))
        block.kind == BlockKind.Sets && block.ranged == null -> Rung.Step(block.copy(reps = block.reps + 1))
        else -> Rung.ToppedOut
    }
}

/**
 * [block] denser, else doing more, at the same bell, its [Block.origin] kept, or set to it as it stands: rungs 2
 * and 3 of [rungOf]. Null with neither left.
 */
private fun denserOrMore(block: Block, rest: Int): Block? {
    val ballistic = block.exercise.movement.tempo == Tempo.Ballistic
    val stepped = when (block.kind) {
        BlockKind.Sets -> {
            val floor = if (ballistic) BALLISTIC_REST_FLOOR else GRIND_REST_FLOOR
            val maxSets = if (ballistic) BALLISTIC_MAX_SETS else GRIND_MAX_SETS
            ((block.rest ?: rest) - RestClock.STEP_SECONDS).takeIf { it >= floor }?.let { block.copy(rest = it) }
                ?: block.copy(rounds = block.rounds + 1).takeIf { block.rounds < maxSets }
        }

        BlockKind.Emom -> {
            val maxReps = if (ballistic) BALLISTIC_MAX_EMOM_REPS else GRIND_MAX_EMOM_REPS
            block.copy(reps = block.reps + 1).takeIf { block.ranged == null && block.reps < maxReps }
                ?: block.copy(rounds = block.rounds + 1).takeIf { block.rounds < BELL_EMOM_MAX_MINUTES }
        }

        BlockKind.Ladder -> moreLadder(block)

        BlockKind.Density -> null
    }
    return stepped?.copy(origin = block.origin ?: block)
}

/** [block], a ladder, another ladder, up to five; then, climbing from 1, a rung more, up to five. Else null. */
internal fun moreLadder(block: Block): Block? = when {
    block.rounds < MAX_LADDERS -> block.copy(rounds = block.rounds + 1)
    block.climbsFromOne && block.rungs.size < MAX_RUNG -> block.copy(rungs = block.rungs + (block.rungs.size + 1))
    else -> null
}

/** Whether its rungs climb a rep at a time from one: 1-2-3, the Rite of Passage's. */
private val Block.climbsFromOne: Boolean
    get() = rungs.isNotEmpty() && rungs == (1..rungs.size).toList()

/**
 * [block] as a harder lift at the bell it was done at, [at]: as it started, with the lift of [variationOf] and that
 * bell named, its origin kept. Null on a detour already, for a ladder that doesn't climb from one, or with no
 * harder lift to go to.
 */
private fun variation(block: Block, at: Load.Bell, pairs: Set<Double>): Block? {
    // A ladder of set counts (10-15-25-50: volume, not strength) goes on to the bell as it is.
    if (block.onDetour || (block.kind == BlockKind.Ladder && !block.climbsFromOne)) return null
    val to = variationOf(block.exercise, block.double, at.kg, pairs) ?: return null
    return block.startingShape().copy(
        exercise = to.exercise,
        double = to.double,
        load = Load.Bell(at.kg, to.double),
        origin = block.origin ?: block,
    )
}

/**
 * The shape this block goes back to at the next load: its [Block.origin], else itself, at the bottom of its range,
 * at the rest set on Lift, with no origin.
 */
internal fun Block.startingShape(): Block {
    val start = origin ?: this
    return start.copy(reps = start.ranged?.bottom ?: start.reps, rest = null, origin = null)
}

/** The bells [block] can step to: for two bells, only those owned two of, once any are; else any of [bells]. */
private fun bellsFor(block: Block, bells: List<Double>, pairs: Set<Double>): List<Double> =
    if (block.double && pairs.isNotEmpty()) bells.filter { it in pairs } else bells

/** What kind of step [from] to [to] is, as [rungOf] takes them: for a summary to say why. */
fun stepKind(from: Block, to: Block): StepKind = when {
    to.exercise != from.exercise || to.double != from.double -> if (to.onDetour) StepKind.Variation else StepKind.Bell
    to.load != from.load -> if (to.load is Load.Bell) StepKind.Bell else StepKind.Load
    (to.rest ?: Int.MAX_VALUE) < (from.rest ?: Int.MAX_VALUE) -> StepKind.Denser
    to.rounds > from.rounds || to.rungs.size > from.rungs.size -> StepKind.Volume
    to.kind == BlockKind.Emom && to.ranged == null && to.origin != null -> StepKind.Volume
    else -> StepKind.Reps
}
