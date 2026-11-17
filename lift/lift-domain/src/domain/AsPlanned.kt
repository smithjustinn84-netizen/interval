package dev.justinsmith.lift.domain

// A program's day trained without the phone and logged after, as planned: every set it asks for, at the load it
// would have been suggested, timed as the day would run. Nothing about it is marked: it is a session like another.

/** A set logged after the fact: [reps] at [load] ([seconds] for a hold), logged at [loggedAtEpochMillis]. */
data class SetAsPlanned(
    val exercise: Exercise,
    val load: Load,
    val reps: Int,
    val side: Side?,
    val seconds: Int?,
    val loggedAtEpochMillis: Long,
)

/** A session logged after the fact, from [startedAtEpochMillis] to [finishedAtEpochMillis], with its [sets]. */
data class SessionAsPlanned(
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val sets: List<SetAsPlanned>,
) {
    /**
     * This session started no earlier than [earliest] (when the program was taken up, which its days count from) and
     * finished no later than [latest] (now): as it is when it starts after [earliest]; else moved on to start then,
     * when it still finishes by [latest]; else squeezed between the two, its sets in their order and spaced to scale,
     * so a day logged straight after the program is taken up counts as one of it.
     */
    fun within(earliest: Long, latest: Long): SessionAsPlanned {
        if (startedAtEpochMillis >= earliest) return this
        val length = finishedAtEpochMillis - startedAtEpochMillis
        val span = if (earliest + length <= latest) length else maxOf(latest - earliest, 0L)
        fun place(at: Long): Long = if (length ==
            0L
        ) {
            earliest
        } else {
            earliest + (at - startedAtEpochMillis) * span / length
        }
        return SessionAsPlanned(
            earliest,
            earliest + span,
            sets.map { it.copy(loggedAtEpochMillis = place(it.loggedAtEpochMillis)) },
        )
    }
}

/**
 * This day done as planned, finished at [finishedAt], each set at the load [loadOf] gives it, resting [restSeconds]
 * between sets (or a block's own rest). A density block comes to as many rounds as it did the last time in
 * [before] (the sessions finished before it, any order), else one, spread evenly over its cap. An EMOM's sets fall a
 * minute apart. Each set is logged as its work ends, its lift's pace after the one before it, so no two share a
 * time. Null for a day with nothing planned.
 */
fun ProgramDay.doneAsPlanned(
    finishedAt: Long,
    before: List<LiftSession>,
    restSeconds: Int,
    loadOf: (PlannedSet) -> Load,
): SessionAsPlanned? {
    val blocks = today.blocks.map { it.block to it.plannedSets() }.filter { (_, sets) -> sets.isNotEmpty() }
    if (blocks.isEmpty()) return null
    val timed = mutableListOf<Pair<PlannedSet, Long>>()
    var at = 0L
    blocks.forEachIndexed { index, (block, planned) ->
        if (index > 0) at += restAfter(blocks[index - 1].first, restSeconds) * MILLIS
        at = when (block.kind) {
            BlockKind.Emom -> emomSets(planned, at, timed)
            BlockKind.Density -> densitySets(block, densityRoundsBefore(block, before), at, timed)
            BlockKind.Sets, BlockKind.Ladder -> sets(planned, at, restSeconds, timed)
        }
    }
    val startedAt = finishedAt - at
    val sets = timed.map { (set, offset) ->
        SetAsPlanned(
            set.exercise,
            loadOf(set),
            set.reps,
            set.side,
            set.reps.takeIf { set.held },
            startedAt + offset,
        )
    }
    return SessionAsPlanned(startedAt, finishedAt, sets)
}

/** The rounds the last session of this day's program that did a density block like [block] came to; one with none. */
private fun ProgramDay.densityRoundsBefore(block: Block, before: List<LiftSession>): Int =
    densityRounds(block, program, before).lastOrNull() ?: 1

/** One after another, a rest after each but between a left side and its right. Returns when the block ends. */
private fun sets(
    planned: List<PlannedSet>,
    from: Long,
    restSeconds: Int,
    into: MutableList<Pair<PlannedSet, Long>>,
): Long {
    var at = from
    planned.forEachIndexed { index, set ->
        if (index > 0 && set.side != Side.Right) at += (set.rest ?: restSeconds) * MILLIS
        at += workSeconds(set) * MILLIS
        into += set to at
    }
    return at
}

/** A set at the top of each minute, after the count-in. Returns when its last minute ends. */
private fun emomSets(planned: List<PlannedSet>, from: Long, into: MutableList<Pair<PlannedSet, Long>>): Long {
    val start = from + EmomClock.COUNT_IN_MILLIS
    planned.forEachIndexed { minute, set ->
        into += set to start + minute * EmomClock.MINUTE_MILLIS + workSeconds(set) * MILLIS
    }
    return start + planned.size * EmomClock.MINUTE_MILLIS
}

/**
 * [rounds] of the block's first round over its cap, after the count-in, the last as the cap ends; a one-sided lift
 * alternates, as the clock has it. Returns when the cap ends.
 */
private fun densitySets(block: Block, rounds: Int, from: Long, into: MutableList<Pair<PlannedSet, Long>>): Long {
    val first = block.plannedSets().single()
    val start = from + EmomClock.COUNT_IN_MILLIS
    val cap = block.rounds * EmomClock.MINUTE_MILLIS
    val each = maxOf(cap / rounds, workSeconds(first) * MILLIS)
    repeat(rounds) { round ->
        val side = first.side?.let { Side.entries[(it.ordinal + round) % Side.entries.size] }
        into += first.copy(side = side) to start + (round + 1) * each
    }
    return maxOf(start + cap, start + rounds * each)
}

/** The rest after [block], before the next: none after one on the clock, which ends as its time does. */
private fun restAfter(block: Block, restSeconds: Int): Int =
    if (block.kind == BlockKind.Emom || block.kind == BlockKind.Density) 0 else block.rest ?: restSeconds

/** How long a set's work takes: a hold its seconds and its count-in, any other its reps at its lift's pace. */
private fun workSeconds(set: PlannedSet): Long = if (set.held) {
    set.reps + HoldClock.COUNT_IN_MILLIS / MILLIS
} else {
    (set.reps * set.exercise.secondsPerRep).toLong()
}

private const val MILLIS = 1_000L
