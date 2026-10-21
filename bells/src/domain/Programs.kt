package dev.justinsmith.bells.domain

/** How a block's sets run: one after another, every minute on the minute, or in ladders of rising reps. */
enum class BlockKind { Sets, Emom, Ladder }

/**
 * One part of a program day. [Sets]: [rounds] sets of [reps]. [Emom]: [rounds] minutes, [reps] at the top
 * of each. [Ladder]: [rounds] ladders, each climbing [rungs]. A one-sided lift does each set on both sides,
 * unless it is done with two bells ([double]).
 */
data class Block(
    val exercise: Exercise,
    val rounds: Int,
    val reps: Int = 0,
    val kind: BlockKind = BlockKind.Sets,
    val rungs: List<Int> = emptyList(),
    val double: Boolean = false,
) {
    /** The sets it comes to, in order: one-sided lifts left then right, except an EMOM, which alternates by minute. */
    fun plannedSets(): List<PlannedSet> = when (kind) {
        BlockKind.Sets -> (1..rounds).flatMap { sides(reps) }

        BlockKind.Emom -> (0 until rounds).map { minute ->
            PlannedSet(exercise, reps, if (exercise.sided(double)) Side.entries[minute % 2] else null, double)
        }

        BlockKind.Ladder -> (1..rounds).flatMap { rungs.flatMap { rung -> sides(rung) } }
    }

    /** The reps it comes to in all. */
    val totalReps: Int get() = plannedSets().sumOf { it.reps }

    private fun sides(reps: Int) =
        if (exercise.sided(double)) {
            Side.entries.map {
                PlannedSet(exercise, reps, it, double)
            }
        } else {
            listOf(PlannedSet(exercise, reps, null, double))
        }
}

/** One set a program asks for, with two bells when [double]. */
data class PlannedSet(val exercise: Exercise, val reps: Int, val side: Side?, val double: Boolean = false)

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

    /** Where someone stands after [done] sessions of it: the day to do next, or null once it is over. */
    fun dayAfter(done: Int): ProgramDay? = if (done >= totalDays) {
        null
    } else {
        ProgramDay(this, number = done + 1, week = done / daysPerWeek + 1, blocks = days[done % days.size])
    }
}

/** Day [number] (from 1) of [program], in [week] (from 1), and what it asks for. */
data class ProgramDay(val program: Program, val number: Int, val week: Int, val blocks: List<Block>) {
    fun plannedSets(): List<PlannedSet> = blocks.flatMap { it.plannedSets() }
}

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

/**
 * The built-in programs, by a stable id that is stored. Screens name them from the app's strings. [danJohn]
 * ones follow Dan John's kettlebell programming.
 */
enum class ProgramId(val danJohn: Boolean = false) {
    DailySwingsGetUps,
    PressLadders,
    CleanPressEmom,
    GobletSquatRow,
    EasyStrength(danJohn = true),
    ArmorBuildingFormula(danJohn = true),
    TenThousandSwings(danJohn = true),
    SwingSquatCountdown(danJohn = true),
}

private fun builtIn(id: ProgramId, days: List<List<Block>>, daysPerWeek: Int, weeks: Int) =
    Program(ProgramKey.BuiltIn(id), days, daysPerWeek, weeks)

/** 500 swings in five ladders of 10, 15, 25 and 50: a day of 10,000 Swings. */
private val fiveHundredSwings =
    Block(Exercise.TwoHandSwing, rounds = 5, kind = BlockKind.Ladder, rungs = listOf(10, 15, 25, 50))

/** Twenty minutes of the Armor Building Complex, one every minute. */
private val armorBuildingComplex =
    Block(Exercise.ArmorBuildingComplex, rounds = 20, reps = 1, kind = BlockKind.Emom, double = true)

val builtInPrograms: List<Program> = listOf(
    builtIn(
        ProgramId.DailySwingsGetUps,
        days = listOf(
            listOf(
                Block(Exercise.TwoHandSwing, rounds = 10, reps = 10, kind = BlockKind.Emom),
                Block(Exercise.GetUp, rounds = 5, reps = 1),
            ),
        ),
        daysPerWeek = 5,
        weeks = 6,
    ),
    builtIn(
        ProgramId.PressLadders,
        days = listOf(
            listOf(
                Block(Exercise.Press, rounds = 3, kind = BlockKind.Ladder, rungs = listOf(1, 2, 3)),
                Block(Exercise.TwoHandSwing, rounds = 5, reps = 10),
            ),
            listOf(
                Block(Exercise.Press, rounds = 4, kind = BlockKind.Ladder, rungs = listOf(1, 2, 3)),
                Block(Exercise.Snatch, rounds = 5, reps = 5),
            ),
            listOf(
                Block(Exercise.Press, rounds = 5, kind = BlockKind.Ladder, rungs = listOf(1, 2, 3)),
                Block(Exercise.TwoHandSwing, rounds = 5, reps = 15),
            ),
        ),
        daysPerWeek = 3,
        weeks = 8,
    ),
    builtIn(
        ProgramId.CleanPressEmom,
        days = listOf(
            listOf(
                Block(Exercise.Clean, rounds = 10, reps = 3, kind = BlockKind.Emom),
                Block(Exercise.Press, rounds = 10, reps = 3, kind = BlockKind.Emom),
            ),
        ),
        daysPerWeek = 3,
        weeks = 4,
    ),
    builtIn(
        ProgramId.GobletSquatRow,
        days = listOf(
            listOf(Block(Exercise.GobletSquat, rounds = 3, reps = 10), Block(Exercise.Row, rounds = 3, reps = 10)),
        ),
        daysPerWeek = 2,
        weeks = 4,
    ),
    // The 40-day workout: the same few lifts, light and easy, five days a week.
    builtIn(
        ProgramId.EasyStrength,
        days = listOf(
            listOf(
                Block(Exercise.Press, rounds = 2, reps = 5),
                Block(Exercise.Row, rounds = 2, reps = 5),
                Block(Exercise.GobletSquat, rounds = 2, reps = 5),
                Block(Exercise.TwoHandSwing, rounds = 3, reps = 15),
                Block(Exercise.GetUp, rounds = 1, reps = 1),
            ),
        ),
        daysPerWeek = 5,
        weeks = 8,
    ),
    // The complex on two days of three; press ladders of 2-3-5 and swings between them.
    builtIn(
        ProgramId.ArmorBuildingFormula,
        days = listOf(
            listOf(armorBuildingComplex),
            listOf(
                Block(Exercise.Press, rounds = 5, kind = BlockKind.Ladder, rungs = listOf(2, 3, 5)),
                Block(Exercise.TwoHandSwing, rounds = 10, reps = 10),
            ),
            listOf(armorBuildingComplex),
        ),
        daysPerWeek = 3,
        weeks = 6,
    ),
    // 500 swings a session for 20 sessions, with a strength move in small ladders.
    builtIn(
        ProgramId.TenThousandSwings,
        days = listOf(
            listOf(
                fiveHundredSwings,
                Block(Exercise.Press, rounds = 5, kind = BlockKind.Ladder, rungs = listOf(1, 2, 3)),
            ),
            listOf(
                fiveHundredSwings,
                Block(Exercise.GobletSquat, rounds = 5, kind = BlockKind.Ladder, rungs = listOf(1, 2, 3)),
            ),
        ),
        daysPerWeek = 5,
        weeks = 4,
    ),
    // After the Humane Burpee: goblet squats counting down from 10, with 15 swings for each step.
    builtIn(
        ProgramId.SwingSquatCountdown,
        days = listOf(
            listOf(
                Block(Exercise.GobletSquat, rounds = 1, kind = BlockKind.Ladder, rungs = (10 downTo 1).toList()),
                Block(Exercise.TwoHandSwing, rounds = 10, reps = 15),
            ),
        ),
        daysPerWeek = 3,
        weeks = 4,
    ),
)

fun program(id: ProgramId): Program = builtInPrograms.first { it.key == ProgramKey.BuiltIn(id) }

/**
 * Of [planned], the first set not yet done by [logged]: the n-th planned set of a lift (and side) is done by
 * the n-th logged one, whatever its bell or reps, so going off plan never blocks what comes next. A set
 * with two bells has no side, so for a one-sided lift a planned double set is done only by a double one; for
 * a lift with no sides, one bell or two does it.
 */
fun nextPlanned(planned: List<PlannedSet>, logged: List<SetLog>): Int? {
    val used = mutableMapOf<Pair<Exercise, Side?>, Int>()
    logged.forEach { set ->
        val key = set.exercise to set.side
        used[key] = (used[key] ?: 0) + 1
    }
    planned.forEachIndexed { i, set ->
        val key = set.exercise to set.side
        val left = used[key] ?: 0
        if (left == 0) return i
        used[key] = left - 1
    }
    return null
}

/**
 * The bell to suggest for [exercise], with two bells or one ([double]): the last one used for it so, else
 * the last used for it at all, else [fallback].
 */
fun suggestedBell(exercise: Exercise, double: Boolean, history: List<SetLog>, fallback: Double): Double =
    history.lastOrNull { it.exercise == exercise && it.double == double }?.bellKg
        ?: history.lastOrNull { it.exercise == exercise }?.bellKg
        ?: fallback
