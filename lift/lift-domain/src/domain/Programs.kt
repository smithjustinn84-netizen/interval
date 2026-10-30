package dev.justinsmith.lift.domain

/** How a block's sets run: one after another, every minute on the minute, or in ladders of rising reps. */
enum class BlockKind { Sets, Emom, Ladder }

/**
 * One part of a program day. [Sets]: [rounds] sets of [reps]. [Emom]: [rounds] minutes, [reps] at the top
 * of each. [Ladder]: [rounds] ladders, each climbing [rungs]. A one-sided lift does each set on both sides,
 * unless it is done with two bells ([double]). For a hold ([Measure.Time]), [reps] counts the seconds held,
 * and a hold is only ever sets. Its load, when the plan names one: [kg], the bell or the weight added to
 * bodyweight (0 for none), or [band], a [Band]'s id; null leaves it to what was used last time. Sets or an EMOM
 * of a counted lift can build within a [range]: [reps] is then where it stands, and the load goes up only once
 * every set reaches the top (see [nextStep]).
 */
data class Block(
    val exercise: Exercise,
    val rounds: Int,
    val reps: Int = 0,
    val kind: BlockKind = BlockKind.Sets,
    val rungs: List<Int> = emptyList(),
    val double: Boolean = false,
    val kg: Double? = null,
    val band: String? = null,
    val range: RepRange? = null,
) {
    /** The [range] it builds within: none for a ladder or a hold, which never take one. */
    val ranged: RepRange? get() = range?.takeIf { kind != BlockKind.Ladder && exercise.measure == Measure.Reps }

    /** The sets it comes to, in order: one-sided lifts left then right, except an EMOM, which alternates by minute. */
    fun plannedSets(): List<PlannedSet> = when (kind) {
        BlockKind.Sets -> (1..rounds).flatMap { sides(reps) }

        BlockKind.Emom -> (0 until rounds).map { minute ->
            val side = if (exercise.sided(double)) Side.entries[minute % 2] else null
            PlannedSet(exercise, reps, side, double, kg, band)
        }

        BlockKind.Ladder -> (1..rounds).flatMap { rungs.flatMap { rung -> sides(rung) } }
    }

    /** The reps it comes to in all. */
    val totalReps: Int get() = plannedSets().sumOf { it.reps }

    private fun sides(reps: Int) =
        if (exercise.sided(double)) {
            Side.entries.map {
                PlannedSet(exercise, reps, it, double, kg, band)
            }
        } else {
            listOf(PlannedSet(exercise, reps, null, double, kg, band))
        }
}

/**
 * Reps to build within, from [bottom] to [top]: a set done at [top] every time earns the next load, back at
 * [bottom]. Double progression, as kettlebells need it, their jumps too big to take at the same reps.
 */
data class RepRange(val bottom: Int, val top: Int) {
    init {
        require(bottom in 1 until top) { "A range climbs from one rep or more" }
    }
}

/**
 * One set a program asks for, with two bells when [double]. A hold's [reps] are the seconds it lasts. Its load
 * is its block's: [kg] or [band], null for none named.
 */
data class PlannedSet(
    val exercise: Exercise,
    val reps: Int,
    val side: Side?,
    val double: Boolean = false,
    val kg: Double? = null,
    val band: String? = null,
) {
    /** Whether it is a hold, timed rather than counted. */
    val held: Boolean get() = exercise.measure == Measure.Time
}

/**
 * A program: [days] repeat in turn, [daysPerWeek] of them a week, for [weeks] weeks. A built-in is named from
 * the app's strings; one the user made carries its [name].
 */
data class Program(
    val key: ProgramKey,
    val days: List<List<Block>>,
    val daysPerWeek: Int,
    val weeks: Int,
    val name: String? = null,
) {
    init {
        require(days.isNotEmpty() && days.none { it.isEmpty() }) { "A program needs days, each with a block" }
    }

    val totalDays: Int get() = daysPerWeek * weeks

    /** Where someone stands after [done] days of it: the day to do next, or null once it is over. */
    fun dayAfter(done: Int): ProgramDay? = if (done >= totalDays) null else day(done + 1)

    /**
     * Day [number] (from 1) as programmed, its days taken in turn: laid out past the last of its weeks too,
     * for a day done of a program since made shorter.
     */
    fun day(number: Int): ProgramDay =
        ProgramDay(this, number, week = (number - 1) / daysPerWeek + 1, blocks = days[(number - 1) % days.size])
}

/**
 * Day [number] (from 1) of [program], in [week] (from 1), and what it asks for: its [blocks] as programmed, or
 * as a session's [plan] changed them.
 */
data class ProgramDay(
    val program: Program,
    val number: Int,
    val week: Int,
    val blocks: List<Block>,
    val plan: SessionPlan? = null,
) {
    /** Which of the program's days it is, from 0. */
    val dayIndex: Int get() = (number - 1) % program.days.size

    /** Today's plan: as the session changed it, or as programmed. */
    val today: SessionPlan get() = plan ?: SessionPlan.of(blocks)

    /** Each block's sets, in order. */
    fun blockSets(): List<List<PlannedSet>> = today.blocks.map { it.plannedSets() }

    fun plannedSets(): List<PlannedSet> = blockSets().flatten()
}

/**
 * The program followed, from [startedAtEpochMillis]: its days are the sessions of it since. [daysBefore] of
 * it were counted done before then with no session of its own: a copy followed from where its original was.
 */
data class Followed(val program: Program, val startedAtEpochMillis: Long, val daysBefore: Int = 0)

/** A program's id as it is stored, with its sessions and as the one followed. */
sealed interface ProgramKey {
    val stored: String

    /** A built-in, stored by its [ProgramId]'s name. */
    data class BuiltIn(val id: ProgramId) : ProgramKey {
        override val stored: String get() = id.name
    }

    /** One the user made, by its [uid]. */
    data class Custom(val uid: String) : ProgramKey {
        override val stored: String get() = CUSTOM_PREFIX + uid
    }

    companion object {
        /** The key [stored] names; null for a built-in this build doesn't know. */
        fun parse(stored: String): ProgramKey? = if (stored.startsWith(CUSTOM_PREFIX)) {
            stored.removePrefix(CUSTOM_PREFIX).takeIf { it.isNotEmpty() }?.let(::Custom)
        } else {
            ProgramId.entries.firstOrNull { it.name == stored }?.let(::BuiltIn)
        }
    }
}

private const val CUSTOM_PREFIX = "custom:"
