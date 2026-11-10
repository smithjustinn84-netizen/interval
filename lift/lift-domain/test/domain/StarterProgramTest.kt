package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StarterProgramTest {
    private val rest = RestClock.DEFAULT_SECONDS

    private val oneBell = program(ProgramId.BjjOneBell)

    private fun pick(
        minutes: Int,
        prefer: ProgramFamily? = null,
        pair: Boolean = false,
        programs: List<Program> = programCatalogue(),
    ) = (starterProgram(minutes, rest, prefer, pair, programs)?.key as? ProgramKey.BuiltIn)?.id

    /** A new built-in lands on one side or the other here: say which before it can be picked for someone. */
    @Test
    fun `the built-ins one bell is enough for`() {
        val oneBell = builtInPrograms.filter { it.oneBell }.map { (it.key as ProgramKey.BuiltIn).id }.toSet()
        val not = setOf(
            // Two bells.
            ProgramId.ArmorBuildingFormula,
            ProgramId.FifteenMinuteKettlebell,
            ProgramId.GrapplerStrength,
            ProgramId.BjjKettlebell,
            // A bar to hang from, or a bench.
            ProgramId.TenThousandSwings,
            ProgramId.BellsAndBodyweight,
            ProgramId.BodyweightBasics,
            ProgramId.BodyweightEmom,
            // Bands, and the barbell.
            ProgramId.BandsAndBells,
            ProgramId.OlympicFoundations,
            ProgramId.BarbellFiveByFive,
        )
        assertEquals(ProgramId.entries.toSet() - not, oneBell)
    }

    @Test
    fun `a lift that is only ever done with two bells rules its program out`() {
        val farmers = Program(ProgramKey.Custom("a"), listOf(listOf(Block(BuiltInExercise.FarmersWalk, 3, 30))), 2, 4)
        val swings = Program(ProgramKey.Custom("b"), listOf(listOf(Block(BuiltInExercise.TwoHandSwing, 5, 10))), 2, 4)
        assertFalse(farmers.oneBell)
        assertTrue(swings.oneBell)
    }

    @Test
    fun `the session's length picks a beginner's program that fills it`() {
        assertEquals(ProgramId.FirstHundredSwings, pick(15))
        assertEquals(ProgramId.KettlebellFoundations, pick(20))
        assertEquals(ProgramId.BeginnerFullBody, pick(30))
    }

    @Test
    fun `with no beginner's program that short any that fits is taken`() {
        assertEquals(ProgramId.GobletSquatRow, pick(10))
    }

    @Test
    fun `a grappler with one bell is started on the program built around the mat once it fits`() {
        assertEquals(20..20, oneBell.dayMinutes(RestClock.DEFAULT_SECONDS))
        assertTrue(oneBell.oneBell)
        assertEquals(ProgramId.BjjOneBell, pick(20, ProgramFamily.Grappling))
        assertEquals(ProgramId.BjjOneBell, pick(30, ProgramFamily.Grappling))
        // Not for someone who never said BJJ, and not when its days are too long.
        assertEquals(ProgramId.KettlebellFoundations, pick(20))
        assertEquals(pick(15), pick(15, ProgramFamily.Grappling))
    }

    @Test
    fun `nobody is started on a program no longer offered`() {
        val picked = (5..60 step 5).flatMap { minutes ->
            ProgramFamily.entries.flatMap { family ->
                listOf(pick(minutes, family), pick(minutes, family, pair = true))
            }
        }
        assertTrue(picked.none { it in retiredPrograms })
    }

    @Test
    fun `a grappler with a pair is started on the program built around the mat once it fits`() {
        val bjj = builtInPrograms.single { it.key == ProgramKey.BuiltIn(ProgramId.BjjKettlebell) }
        assertEquals(15..20, bjj.dayMinutes(RestClock.DEFAULT_SECONDS))
        assertEquals(ProgramId.BjjKettlebell, pick(20, ProgramFamily.Grappling, pair = true))
        assertEquals(ProgramId.BjjKettlebell, pick(30, ProgramFamily.Grappling, pair = true))
        // Too short for its longer day: the grappler's one-bell picks as before.
        assertEquals(pick(15, ProgramFamily.Grappling), pick(15, ProgramFamily.Grappling, pair = true))
    }

    @Test
    fun `a pair opens the programs done with two and the others stay`() {
        val abf = builtInPrograms.single { it.key == ProgramKey.BuiltIn(ProgramId.ArmorBuildingFormula) }
        val bars = builtInPrograms.single { it.key == ProgramKey.BuiltIn(ProgramId.BodyweightBasics) }
        assertFalse(abf.oneBell)
        assertTrue(abf.pairOfBells)
        assertFalse(bars.pairOfBells)
        assertTrue(builtInPrograms.filter { it.oneBell }.all { it.pairOfBells })
    }

    @Test
    fun `with nothing that short the shortest is taken`() {
        assertEquals(ProgramId.GobletSquatRow, pick(5))
    }

    @Test
    fun `the days are timed at the rest the user takes`() {
        val swings = Program(ProgramKey.Custom("b"), listOf(listOf(Block(BuiltInExercise.GobletSquat, 5, 10))), 2, 4)
        // Five sets of ten with four rests: under five minutes at 30 seconds, about ten at two minutes.
        assertEquals(swings, starterProgram(5, restSeconds = 30, programs = listOf(swings)))
        assertEquals(5..5, swings.dayMinutes(30))
        assertEquals(10..10, swings.dayMinutes(120))
    }

    @Test
    fun `with nothing for one bell there is no program`() {
        val doubles = builtInPrograms.filter { !it.oneBell }
        assertNull(starterProgram(20, rest, programs = doubles))
    }
}
