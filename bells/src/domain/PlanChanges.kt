package dev.justinsmith.bells.domain

/** Block [index] of a program day, as programmed ([from]) and as the session left it ([to]). */
data class BlockChange(val index: Int, val from: Block, val to: Block) {
    /** Another exercise swapped in: kept on this day only, whatever the scope. */
    val swapped: Boolean get() = from.exercise != to.exercise
}

/** How far a change to a program day reaches when it is kept. */
enum class SaveScope {
    /** The day just done. */
    ThisDay,

    /** Every block of the same exercise, on every day. */
    EveryDay,
}

/**
 * What a session changed of day [dayNumber] (from 1) of [program], its day [dayIndex] (from 0) of those that
 * repeat: the [changes] to its blocks. Skipping a block is not a change: it is for the day it was skipped.
 */
data class PlanChanges(
    val program: Program,
    val dayNumber: Int,
    val dayIndex: Int,
    val changes: List<BlockChange>,
    /** The steps up the day earned, each from its block as done: offered for next time, not yet kept. */
    val nudges: List<BlockChange> = emptyList(),
) {
    /**
     * These changes with the [nudges] at [taken] kept too, each over its block's own change; what is kept is
     * then each block as programmed and as it is to be.
     */
    fun withNudges(taken: Set<Int>): PlanChanges {
        val programmed = program.days[dayIndex]
        val picked = nudges.filterIndexed { i, _ -> i in taken }
            .mapNotNull { nudge -> programmed.getOrNull(nudge.index)?.let { BlockChange(nudge.index, it, nudge.to) } }
        val kept = changes.filterNot { c -> picked.any { it.index == c.index } } + picked
        return copy(changes = kept.sortedBy { it.index }, nudges = emptyList())
    }

    /**
     * The program's days with the changes kept, as far as [scope] reaches. Every day: a load changed goes to
     * every block of the exercise with as many bells; rounds, reps or rungs only to those of the same kind that
     * had what this one had; a swap or a change of kind or bells stays on this day.
     */
    fun applied(scope: SaveScope): List<List<Block>> = program.days.mapIndexed { d, blocks ->
        blocks.mapIndexed { i, block ->
            val own = changes.firstOrNull { d == dayIndex && it.index == i }
            when {
                own != null -> own.to
                scope == SaveScope.EveryDay -> changes.filterNot { it.swapped }.fold(block) { b, c -> c.carry(b) }
                else -> block
            }
        }
    }

    /** The program's draft with the changes kept, as far as [scope] reaches, named [name]. */
    fun draft(scope: SaveScope, name: String): ProgramDraft =
        ProgramDraft(name, applied(scope), program.daysPerWeek, program.weeks)

    /** Whether the program goes on after this day: a copy of it is then followed from the next. */
    val goesOn: Boolean get() = dayNumber < program.totalDays
}

/** [block] with what this change changed, where it is of the same exercise and had what this one had. */
private fun BlockChange.carry(block: Block): Block {
    if (block.exercise != from.exercise) return block
    var carried = block
    if (block.double == from.double && (to.kg != from.kg || to.band != from.band)) {
        carried = carried.copy(kg = to.kg, band = to.band)
    }
    if (block.kind == from.kind && from.kind == to.kind) {
        if (to.rounds != from.rounds && block.rounds == from.rounds) carried = carried.copy(rounds = to.rounds)
        if (to.reps != from.reps && block.reps == from.reps) carried = carried.copy(reps = to.reps)
        if (to.rungs != from.rungs && block.rungs == from.rungs) carried = carried.copy(rungs = to.rungs)
    }
    return carried
}

/**
 * What a session changed of [day], and the steps up it earned ([nudges]); null when it changed nothing and
 * earned none.
 */
fun planChanges(day: ProgramDay, nudges: List<BlockChange> = emptyList()): PlanChanges? {
    val changes = day.plan?.takeIf { it.blocks.size == day.blocks.size }?.let { plan ->
        day.blocks.zip(plan.changedBlocks).mapIndexedNotNull { i, (from, to) ->
            BlockChange(i, from, to).takeIf { from != to }
        }
    }.orEmpty()
    return PlanChanges(day.program, day.number, day.dayIndex, changes, nudges)
        .takeIf { changes.isNotEmpty() || nudges.isNotEmpty() }
}

/**
 * A name for a copy of a program, from [base] ("Easy Strength (yours)"): no longer than a program's name can be,
 * and none of [taken], with " 2", " 3"… added until it is free.
 */
fun copyName(base: String, taken: Set<String>): String {
    val names = taken.map(::nameKey).toSet()
    val clamped = base.take(ProgramDraft.MAX_NAME_LENGTH).trim()
    if (nameKey(clamped) !in names) return clamped
    return generateSequence(2) { it + 1 }
        .map { n -> " $n".let { suffix -> base.take(ProgramDraft.MAX_NAME_LENGTH - suffix.length).trim() + suffix } }
        .first { nameKey(it) !in names }
}

private fun nameKey(name: String) = name.trim().lowercase()
