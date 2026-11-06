package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StarterProgramTest {
    private val rest = RestClock.DEFAULT_SECONDS

    private fun pick(minutes: Int, prefer: ProgramFamily? = null, rest: Int = this.rest): ProgramId? =
        (starterProgram(minutes, rest, prefer)?.key as? ProgramKey.BuiltIn)?.id

    /** A new built-in lands on one side or the other here: say which before it can be picked for someone. */
    @Test
    fun `the built-ins one bell is enough for`() {
        val oneBell = builtInPrograms.filter { it.oneBell }.map { (it.key as ProgramKey.BuiltIn).id }.toSet()
        val not = setOf(
            // Two bells.
            ProgramId.ArmorBuildingFormula,
            ProgramId.FifteenMinuteKettlebell,
            ProgramId.GrapplerStrength,
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
    fun `a grappler gets a grappler's program once one fits`() {
        assertEquals(ProgramId.RoundConditioning, pick(30, ProgramFamily.Grappling))
        assertEquals(ProgramId.KettlebellFoundations, pick(20, ProgramFamily.Grappling))
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
