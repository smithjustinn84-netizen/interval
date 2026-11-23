package dev.justinsmith.lift.domain

import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** The cycles a block is stepped through: far longer than any program runs, so a ceiling is met if it is there. */
private const val STEPS = 60

/** How a block was walked: the program, the block's place and the gear. */
private data class Walk(val program: Program, val index: Int, val gear: String)

/**
 * Every block of every built-in, stepped up again and again as if done in full each time, stays a sane thing to ask
 * of someone: the work of an EMOM fits its minute and its minutes barely grow, a get-up never gains reps, a carry
 * goes heavier rather than longer, a hold stops at two minutes, no set grows past its ceiling and the snatch never
 * becomes two bells. The tests are left out: they never step.
 */
class ProgramSafetyTest {
    private val gears = mapOf(
        "one bell" to Gear(listOf(16.0)),
        "a ladder of bells" to Gear(listOf(8.0, 12.0, 16.0, 20.0, 24.0, 28.0, 32.0, 40.0, 48.0)),
        "a ladder of pairs" to Gear(
            listOf(8.0, 12.0, 16.0, 20.0, 24.0, 28.0, 32.0, 40.0, 48.0),
            pairs = setOf(12.0, 16.0, 20.0, 24.0, 32.0),
        ),
    )

    /** Every planned set of [block] done as planned at its load. */
    private fun doneFor(block: Block): List<SetLog> = block.plannedSets().map {
        val held = it.exercise.measure == Measure.Time
        SetLog.of(
            0,
            it.exercise,
            block.load?.kg ?: 0.0,
            if (held) 0 else it.reps,
            it.side,
            0,
            double = it.double,
            seconds = it.reps.takeIf { held },
        )
    }

    /** [block] with a load to step from, as a session started on it would name one. */
    private fun loaded(block: Block): Block = block.load?.let { block } ?: when (block.exercise.equipment) {
        Equipment.Kettlebell -> block.withLoad(Load.Bell(startingBellKg(block.exercise, emptyList()), block.double))
        Equipment.Barbell -> block.withLoad(Load.Bar(DEFAULT_BAR_KG))
        Equipment.Bodyweight -> block.copy(load = Load.Bodyweight(0.0))
        else -> block
    }

    /** Each block of the built-ins, under each gear, stepped [STEPS] times: [check] sees every block it comes to. */
    private fun walkAll(check: (Walk, start: Block, at: Block) -> String?) {
        val problems = mutableListOf<String>()
        for (program in builtInPrograms) {
            program.days.flatten().forEachIndexed { index, first ->
                for ((name, gear) in gears) {
                    val walk = Walk(program, index, name)
                    var block = loaded(first)
                    repeat(STEPS) { step ->
                        check(walk, first, block)?.let {
                            problems +=
                                "${program.key} block $index, $name, step $step: $it"
                        }
                        block = (rungOf(block, doneFor(block), gear, rest = 90) as? Rung.Step)?.to ?: return@repeat
                    }
                }
            }
        }
        if (problems.isNotEmpty()) fail(problems.distinct().take(40).joinToString("\n"))
    }

    @Test
    fun `an EMOM's work fits its minute`() = walkAll { _, _, at ->
        val work = at.reps * at.exercise.secondsPerRep
        "${at.exercise} ${at.reps} a minute is $work s of work".takeIf {
            at.kind == BlockKind.Emom && work > EMOM_WORK_SECONDS
        }
    }

    @Test
    fun `an EMOM's minutes barely grow and a ranged one's never`() = walkAll { _, start, at ->
        val most = if (start.ranged != null) start.rounds else ceil(start.rounds * EMOM_MINUTES_GROWTH).toInt()
        "${at.exercise} ran to ${at.rounds} minutes from ${start.rounds}".takeIf {
            at.kind == BlockKind.Emom && at.rounds > most
        }
    }

    @Test
    fun `a complex keeps its reps a minute`() = walkAll { _, start, at ->
        "${at.exercise} went to ${at.reps} a minute".takeIf {
            at.kind == BlockKind.Emom && at.exercise.movement.perRep >= COMPLEX_LIFTS && at.reps > start.reps
        }
    }

    @Test
    fun `a get-up never gains reps`() = walkAll { _, start, at ->
        "get-ups went to ${at.reps} a set".takeIf {
            at.exercise.movement.pattern == Pattern.GetUp && at.reps > start.reps
        }
    }

    @Test
    fun `a carry goes heavier not longer and a hold stops at two minutes`() = walkAll { _, start, at ->
        val most = if (at.exercise.equipment == Equipment.Kettlebell) CARRY_MAX_SECONDS else CORE_HOLD_MAX_SECONDS
        "${at.exercise} held for ${at.reps} s".takeIf {
            at.exercise.measure == Measure.Time && at.reps > maxOf(most, start.reps)
        }
    }

    @Test
    fun `no set grows past its ceiling`() = walkAll { _, start, at ->
        val ceiling = when {
            at.exercise.equipment != Equipment.Kettlebell -> PLAIN_MAX_SET_REPS
            at.exercise.movement.tempo == Tempo.Ballistic -> BALLISTIC_MAX_SET_REPS
            else -> GRIND_MAX_SET_REPS
        }
        "${at.exercise} went to ${at.reps} a set".takeIf {
            at.kind == BlockKind.Sets &&
                at.exercise.measure == Measure.Reps &&
                at.reps > maxOf(ceiling, start.reps, at.ranged?.top ?: 0)
        }
    }

    @Test
    fun `the snatch never goes to two bells on its own`() = walkAll { _, start, at ->
        "${at.exercise} went to two bells".takeIf {
            at.exercise in setOf(BuiltInExercise.Snatch, BuiltInExercise.HalfSnatch) && at.double && !start.double
        }
    }

    @Test
    fun `the walk reaches every built-in`() {
        var walked = 0
        walkAll { _, _, _ -> null.also { walked++ } }
        assertTrue(walked > builtInPrograms.size * gears.size)
    }
}
