package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AsPlannedTest {
    private val press = Block(BuiltInExercise.Press, rounds = 2, reps = 5, load = Load.Bell(16.0))
    private val carry = Block(BuiltInExercise.SuitcaseCarry, rounds = 1, reps = 30)
    private val emom = Block(BuiltInExercise.TwoHandSwing, rounds = 4, reps = 10, kind = BlockKind.Emom)
    private val density = Block(BuiltInExercise.OneHandSwing, rounds = 10, reps = 10, kind = BlockKind.Density)
    private val program = Program(ProgramKey.Custom("p"), listOf(listOf(press, carry, emom, density)), 3, 4, "Mine")
    private val day = program.day(1)
    private val finished = 100 * MINUTE

    private fun SessionAsPlanned.logged(): List<SetLog> =
        sets.mapIndexed { i, set ->
            SetLog(i.toLong(), set.exercise, set.load, set.reps, set.side, set.loggedAtEpochMillis, set.seconds)
        }

    private fun asPlanned(before: List<LiftSession> = emptyList()) =
        assertNotNull(day.doneAsPlanned(finished, before, REST) { it.load ?: Load.Bell(20.0, it.double) })

    @Test
    fun `every set of the day is done at its load and the day runs as long as its estimate`() {
        val done = asPlanned()
        assertEquals(finished, done.finishedAtEpochMillis)
        assertEquals(day.estimatedSeconds(REST) * 1_000L, done.finishedAtEpochMillis - done.startedAtEpochMillis)
        val presses = done.sets.filter { it.exercise == BuiltInExercise.Press }
        assertEquals(listOf(Side.Left, Side.Right, Side.Left, Side.Right), presses.map { it.side })
        assertTrue(presses.all { it.load == Load.Bell(16.0) && it.reps == 5 && it.seconds == null })
        assertEquals(Load.Bell(20.0), done.sets.first { it.exercise == BuiltInExercise.TwoHandSwing }.load)
        // Every planned set is matched by one logged, the day done in full.
        assertTrue(blocksDone(day, done.logged()).all { block -> block.done.all { it != null } })
    }

    @Test
    fun `a hold keeps its seconds`() {
        val carries = asPlanned().sets.filter { it.exercise == BuiltInExercise.SuitcaseCarry }
        assertEquals(listOf(30, 30), carries.map { it.seconds })
    }

    @Test
    fun `sets are logged one after another and never together`() {
        val times = asPlanned().sets.map { it.loggedAtEpochMillis }
        assertEquals(times.sorted(), times)
        assertEquals(times.size, times.toSet().size)
        assertTrue(times.first() > asPlanned().startedAtEpochMillis)
    }

    @Test
    fun `an EMOM's sets fall a minute apart`() {
        val swings = asPlanned().sets.filter {
            it.exercise == BuiltInExercise.TwoHandSwing
        }.map { it.loggedAtEpochMillis }
        assertEquals(listOf(MINUTE, MINUTE, MINUTE), swings.zipWithNext { a, b -> b - a })
    }

    @Test
    fun `a density block new to the lifter is one round`() {
        val rounds = asPlanned().sets.filter { it.exercise == BuiltInExercise.OneHandSwing }
        assertEquals(1, rounds.size)
        assertEquals(1, blocksDone(day, asPlanned().logged()).last().rounds)
    }

    @Test
    fun `a density block comes to the rounds it did last time spread over its cap and alternating sides`() {
        val earlier = LiftSession(
            "a",
            0,
            MINUTE * 30,
            "Mine",
            sets = asPlanned().logged().filter { it.exercise != BuiltInExercise.OneHandSwing } +
                (1..6).map { round ->
                    SetLog.of(
                        100L + round,
                        BuiltInExercise.OneHandSwing,
                        20.0,
                        10,
                        Side.entries[(round - 1) % 2],
                        MINUTE * (20 + round),
                    )
                },
            program = program.key,
            day = 1,
        )
        assertEquals(listOf(6), densityRounds(density, program, listOf(earlier)))
        val done = asPlanned(before = listOf(earlier))
        val rounds = done.sets.filter { it.exercise == BuiltInExercise.OneHandSwing }
        assertEquals(6, rounds.size)
        assertEquals(
            listOf(Side.Left, Side.Right, Side.Left, Side.Right, Side.Left, Side.Right),
            rounds.map {
                it.side
            },
        )
        assertEquals(done.finishedAtEpochMillis, rounds.last().loggedAtEpochMillis)
        assertEquals(6, blocksDone(day, done.logged()).last().rounds)
        // Its pace reads as it would have run, not as sets entered together.
        assertNotNull(density(done.logged().filter { it.exercise == BuiltInExercise.OneHandSwing }))
    }

    @Test
    fun `a session kept within when its program was taken up and now is moved on or squeezed in order`() {
        val done = asPlanned()
        val length = done.finishedAtEpochMillis - done.startedAtEpochMillis
        assertEquals(done, done.within(done.startedAtEpochMillis, Long.MAX_VALUE))

        val later = done.startedAtEpochMillis + MINUTE
        val moved = done.within(later, Long.MAX_VALUE)
        assertEquals(later to later + length, moved.startedAtEpochMillis to moved.finishedAtEpochMillis)
        assertEquals(done.sets.map { it.loggedAtEpochMillis + MINUTE }, moved.sets.map { it.loggedAtEpochMillis })

        val squeezed = done.within(later, later + length / 2)
        assertEquals(later + length / 2, squeezed.finishedAtEpochMillis)
        val times = squeezed.sets.map { it.loggedAtEpochMillis }
        assertEquals(times.sorted(), times)
        assertTrue(times.all { it in later..later + length / 2 })
    }

    @Test
    fun `a day with every block skipped has nothing to log`() {
        val skipped = day.copy(
            plan = SessionPlan.of(day.blocks).let { plan ->
                plan.blocks.indices.fold(plan) { p, i -> p.skip(i, 0) }
            },
        )
        assertNull(skipped.doneAsPlanned(finished, emptyList(), REST) { Load.Mat })
    }

    private companion object {
        const val MINUTE = 60_000L
        const val REST = 90
    }
}
