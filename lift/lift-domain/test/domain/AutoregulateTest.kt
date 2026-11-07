package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AutoregulateTest {
    private val bells = listOf(12.0, 16.0, 20.0, 24.0)

    @Test
    fun `a grind goes a bell down and rests longer`() {
        val press = Block(BuiltInExercise.Press, rounds = 3, reps = 5)
        val easier = press.easier(Load.Bell(20.0), bells, emptySet(), bar = null, baseRest = 90)
        assertEquals(Load.Bell(16.0), easier.load)
        assertEquals(3, easier.rounds)
        // 90 × 1.3 = 117, to the nearest 15.
        assertEquals(120, easier.rest)
    }

    @Test
    fun `two bells go down only to a pair and with no lighter bell the load stays`() {
        val fronts = Block(BuiltInExercise.FrontSquat, rounds = 3, reps = 5, double = true)
        val easier = fronts.easier(Load.Bell(24.0, double = true), bells, setOf(16.0, 24.0), bar = null, baseRest = 90)
        assertEquals(Load.Bell(16.0, double = true), easier.load)
        val lightest = Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 10)
        assertNull(lightest.easier(Load.Bell(12.0), bells, emptySet(), bar = null, baseRest = 90).load)
    }

    @Test
    fun `a ballistic EMOM loses a quarter of its minutes and sets lose a round`() {
        val abc =
            Block(BuiltInExercise.ArmorBuildingComplex, rounds = 20, reps = 1, kind = BlockKind.Emom, double = true)
        val easierAbc = abc.easier(Load.Bell(24.0, double = true), bells, emptySet(), bar = null, baseRest = 90)
        assertEquals(15, easierAbc.rounds)
        assertNull(easierAbc.rest)
        // A ballistic lift keeps its bell.
        assertNull(easierAbc.load)
        val swings = Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 15)
        val easierSwings = swings.easier(Load.Bell(24.0), bells, emptySet(), bar = null, baseRest = 90)
        assertEquals(4, easierSwings.rounds)
        assertEquals(60, easierSwings.rest)
        assertEquals(
            2,
            Block(BuiltInExercise.Snatch, rounds = 2, reps = 5).easier(null, bells, emptySet(), null, 90).rounds,
        )
    }

    @Test
    fun `a built-in day is made easier from the loads it would be done at`() {
        val day = program(ProgramId.GrapplerStrength).days.first()
        val plan = SessionPlan.of(day)
        val easier = plan.easier({ Load.of(it.exercise.equipment, 20.0, it.double) }, bells, emptySet(), null, 90)
        // Front squats and rows are grinds: a bell down from the 20 they would be done at.
        val fronts = assertNotNull(easier).blocks.first().block
        assertEquals(BuiltInExercise.FrontSquat, fronts.exercise)
        assertEquals(16.0, fronts.load?.kg)
    }

    @Test
    fun `the grappler's ABC day offers the get-up day on a taxed day and not the other way`() {
        val bjj = program(ProgramId.BjjKettlebell)
        val (abc, getUps) = bjj.days.map { it.plannedGripLoad }
        // 15 minutes of one ABC with a pair: 6 lifts × grip 2 × thrown 1.5 × two bells, each minute.
        assertEquals(540.0, abc)
        // Get-ups 8, bottoms-up carries 36, windmills 20, dead bugs nothing.
        assertEquals(64.0, getUps)
        assertEquals(2, bjj.lighterDayThan(1))
        assertNull(bjj.lighterDayThan(2))
        assertNull(program(ProgramId.SimpleAndSinister).lighterDayThan(1))
    }
}
