package dev.justinsmith.lift.domain

// Supersets: two blocks side by side done in turn, a set of the first (A1), then a set of the second (A2), round by
// round. Each stays a block of its own, so everything that reads a block (its progression, an easier day, a swap,
// what was done of it) reads it as before; only the order its sets are done in, and the rest between them, change.

/** How a superset rests: once after each round of the pair, going straight from A1 to A2, or after each set. */
enum class SupersetRest { Shared, Alternating }

/**
 * A superset: block [first] and the one after it, resting by [rest]; the [letter]-th of the day's (from 0, A's 0),
 * so its blocks are A1 and A2, then B1 and B2.
 */
data class Superset(val first: Int, val rest: SupersetRest, val letter: Int) {
    val second: Int get() = first + 1

    fun has(block: Int): Boolean = block == first || block == second
}

/**
 * Where a block stands in its superset: the [letter]-th pair's ([Superset.letter]), its [second] block (A2) or its
 * first (A1), done in turn with [partner], resting by [rest].
 */
data class SupersetPlace(val letter: Int, val second: Boolean, val partner: Exercise, val rest: SupersetRest) {
    /** Its mark, "A1" or "A2". */
    val mark: String get() = "${'A' + letter}${if (second) 2 else 1}"
}

/** Whether a block can be one of a superset: sets of a counted lift, never a hold, an EMOM, a ladder or a cap. */
val Block.pairable: Boolean get() = kind == BlockKind.Sets && exercise.measure == Measure.Reps

/**
 * The day's supersets, in order. Blocks pair, first to last, when both are marked ([Block.superset]) to rest the
 * same way, both [pairable], of different lifts (swapped ones too) and neither skipped. A mark with no such
 * partner beside it pairs nothing: the block runs on its own.
 */
fun SessionPlan.supersets(): List<Superset> {
    val pairs = mutableListOf<Superset>()
    var i = 0
    while (i < blocks.size - 1) {
        val rest = blocks[i].block.superset
        if (rest != null && pairs(blocks[i], blocks[i + 1])) {
            pairs += Superset(i, rest, pairs.size)
            i += 2
        } else {
            i++
        }
    }
    return pairs
}

/** The supersets of these blocks, as programmed: see [SessionPlan.supersets]. */
fun List<Block>.supersets(): List<Superset> = SessionPlan.of(this).supersets()

/** Where block [index] stands in a superset; null when it is in none. */
fun SessionPlan.supersetPlace(index: Int): SupersetPlace? = supersets().placeOf(index, this)

/** Where block [index] of these stands in a superset; null when it is in none. */
fun List<Block>.supersetPlace(index: Int): SupersetPlace? = SessionPlan.of(this).supersetPlace(index)

private fun List<Superset>.placeOf(index: Int, plan: SessionPlan): SupersetPlace? {
    val pair = firstOrNull { it.has(index) } ?: return null
    val second = index == pair.second
    val partner = plan.blocks[if (second) pair.first else pair.second].block.exercise
    return SupersetPlace(pair.letter, second, partner, pair.rest)
}

private fun pairs(a: PlanBlock, b: PlanBlock): Boolean {
    if (a.block.superset != b.block.superset || a.skipped || b.skipped) return false
    if (!a.block.pairable || !b.block.pairable) return false
    // The n-th set of a lift is done by its n-th logged: a lift in both halves would be done out of turn.
    return a.lifts().intersect(b.lifts()).isEmpty()
}

private fun PlanBlock.lifts(): Set<String> = setOfNotNull(block.exercise.key, before?.exercise?.key)

/**
 * One of the day's planned sets in the order it is done: the [index]-th of them laid block after block (as
 * [ProgramDay.plannedSets] has them, which is how sets logged are matched), in block [block]. [straightOn] when it
 * goes straight on to the next with no rest: a superset's A1 before its A2, the pair resting once after.
 */
data class OrderedSet(val index: Int, val set: PlannedSet, val block: Int, val straightOn: Boolean)

/**
 * Today's sets in the order they are done: each block's in turn, but a superset's round by round, a round of its
 * first block (both sides of a one-sided lift) then a round of its second, the longer one's rounds left over after.
 */
fun SessionPlan.inOrder(): List<OrderedSet> {
    val sets = blocks.map { it.plannedSets() }
    val starts = sets.runningFold(0) { at, block -> at + block.size }
    val pairs = supersets().associateBy { it.first }
    val order = mutableListOf<OrderedSet>()
    fun place(block: Int, at: Int) = OrderedSet(starts[block] + at, sets[block][at], block, straightOn = false)
    var i = 0
    while (i < blocks.size) {
        val pair = pairs[i]
        if (pair == null) {
            sets[i].indices.forEach { order += place(i, it) }
            i++
            continue
        }
        val first = roundsIn(sets[i])
        val second = roundsIn(sets[i + 1])
        for (round in 0 until maxOf(first.size, second.size)) {
            val a = first.getOrNull(round).orEmpty().map { place(i, it) }
            val b = second.getOrNull(round).orEmpty().map { place(i + 1, it) }
            // Sharing the rest, the round's last set of A1 goes straight on to A2's.
            order += if (pair.rest == SupersetRest.Shared && b.isNotEmpty()) {
                a.dropLast(1) + a.takeLast(1).map { it.copy(straightOn = true) }
            } else {
                a
            }
            order += b
        }
        i += 2
    }
    return order
}

/** These sets of a block in rounds, by place: a round of a one-sided lift is its left then its right. */
private fun roundsIn(sets: List<PlannedSet>): List<List<Int>> {
    val rounds = mutableListOf<MutableList<Int>>()
    sets.forEachIndexed { at, set ->
        if (set.side != Side.Right || rounds.isEmpty()) rounds += mutableListOf(at) else rounds.last() += at
    }
    return rounds
}

/**
 * This day's block [index] paired with the one after it, resting by [rest], or with [rest] null out of its
 * superset: either block's own pair is undone first, so a block is only ever in one.
 */
fun List<Block>.pairedWithNext(index: Int, rest: SupersetRest?): List<Block> {
    if (index !in indices) return this
    val unpaired = unpair(index).let { if (index + 1 in indices) it.unpair(index + 1) else it }
    if (rest == null || index + 1 !in indices) return unpaired.keptPaired()
    val marked = unpaired.mapIndexed { i, block ->
        if (i == index ||
            i == index + 1
        ) {
            block.copy(superset = rest)
        } else {
            block
        }
    }
    return marked.keptPaired()
}

/** These blocks with block [index]'s superset undone, its partner's mark too. */
fun List<Block>.unpair(index: Int): List<Block> {
    val pair = supersets().firstOrNull { it.has(index) }
    return mapIndexed { i, block ->
        if (i == index || pair?.has(i) == true) block.copy(superset = null) else block
    }
}

/** These blocks with every mark that pairs nothing taken off: what an editor keeps after any change. */
fun List<Block>.keptPaired(): List<Block> {
    val pairs = supersets()
    return mapIndexed { i, block ->
        if (block.superset != null && pairs.none { it.has(i) }) block.copy(superset = null) else block
    }
}

/** Today's plan with block [index] and the one after it a superset resting by [rest]: see [pairedWithNext]. */
fun SessionPlan.pairedWithNext(index: Int, rest: SupersetRest?): SessionPlan {
    val paired = changedBlocks.pairedWithNext(index, rest)
    return copy(blocks = blocks.mapIndexed { i, entry -> entry.copy(block = paired[i]) })
}

/**
 * What block [first] can be paired with: [partner], the block after it, resting by [rest] (null while they aren't
 * paired). A block already paired is offered its pair, its A2 by the A1 before it.
 */
data class SupersetOffer(val first: Int, val partner: Exercise, val rest: SupersetRest?)

/**
 * What block [index] of today's plan can be a superset with: its pair when it is in one, else the block after it
 * when the two could pair ([pairable], different lifts, neither skipped); null for neither.
 */
fun SessionPlan.supersetOffer(index: Int): SupersetOffer? {
    val pair = supersets().firstOrNull { it.has(index) }
    if (pair != null) {
        val partner = blocks[if (index == pair.first) pair.second else pair.first].block.exercise
        return SupersetOffer(pair.first, partner, pair.rest)
    }
    val next = blocks.getOrNull(index + 1)
    val marked = listOfNotNull(blocks.getOrNull(index), next)
        .map { it.copy(block = it.block.copy(superset = SupersetRest.Shared)) }
    val pairs = next != null && SessionPlan(marked).supersets().isNotEmpty()
    return if (pairs) SupersetOffer(index, next.block.exercise, null) else null
}

/** What block [index] of these can be a superset with: see [SessionPlan.supersetOffer]. */
fun List<Block>.supersetOffer(index: Int): SupersetOffer? = SessionPlan.of(this).supersetOffer(index)
