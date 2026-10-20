package dev.justinsmith.bells.domain

/** How a block's sets run: one after another, every minute on the minute, or in ladders of rising reps. */
enum class BlockKind { Sets, Emom, Ladder }

/**
 * One part of a program day. [Sets]: [rounds] sets of [reps]. [Emom]: [rounds] minutes, [reps] at the top
 * of each. [Ladder]: [rounds] ladders, each climbing [rungs]. A one-sided lift does each set on both sides.
 */
data class Block(
    val exercise: Exercise,
    val rounds: Int,
    val reps: Int = 0,
    val kind: BlockKind = BlockKind.Sets,
    val rungs: List<Int> = emptyList(),
) {
    /** The sets it comes to, in order: one-sided lifts left then right, except an EMOM, which alternates by minute. */
    fun plannedSets(): List<PlannedSet> = when (kind) {
        BlockKind.Sets -> (1..rounds).flatMap { sides(reps) }

        BlockKind.Emom -> (0 until rounds).map { minute ->
            PlannedSet(exercise, reps, if (exercise.perSide) Side.entries[minute % 2] else null)
        }

        BlockKind.Ladder -> (1..rounds).flatMap { rungs.flatMap { rung -> sides(rung) } }
    }

    /** The reps it comes to in all. */
    val totalReps: Int get() = plannedSets().sumOf { it.reps }

    private fun sides(reps: Int) =
        if (exercise.perSide) {
            Side.entries.map {
                PlannedSet(exercise, reps, it)
            }
        } else {
            listOf(PlannedSet(exercise, reps, null))
        }
}

/** One set a program asks for. */
data class PlannedSet(val exercise: Exercise, val reps: Int, val side: Side?)

/** A built-in program: [days] repeat in turn, [daysPerWeek] of them a week, for [weeks] weeks. */
data class Program(val id: ProgramId, val days: List<List<Block>>, val daysPerWeek: Int, val weeks: Int) {
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

/** The built-in programs, by a stable id that is stored. Screens name them from the app's strings. */
enum class ProgramId { DailySwingsGetUps, PressLadders, CleanPressEmom, GobletSquatRow }

val builtInPrograms: List<Program> = listOf(
    Program(
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
    Program(
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
    Program(
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
    Program(
        ProgramId.GobletSquatRow,
        days = listOf(
            listOf(Block(Exercise.GobletSquat, rounds = 3, reps = 10), Block(Exercise.Row, rounds = 3, reps = 10)),
        ),
        daysPerWeek = 2,
        weeks = 4,
    ),
)

fun program(id: ProgramId): Program = builtInPrograms.first { it.id == id }

/**
 * Of [planned], the first set not yet done by [logged]: the n-th planned set of a lift (and side) is done by
 * the n-th logged one, whatever its bell or reps, so going off plan never blocks what comes next.
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

/** The bell to suggest for [exercise]: the last one used for it, else [fallback]. */
fun suggestedBell(exercise: Exercise, history: List<SetLog>, fallback: Double): Double =
    history.lastOrNull { it.exercise == exercise }?.bellKg ?: fallback
