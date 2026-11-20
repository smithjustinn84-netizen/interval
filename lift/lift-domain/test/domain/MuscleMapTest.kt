package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MuscleMapTest {
    private var nextId = 1L

    private fun set(exercise: Exercise, side: Side? = null) = SetLog.of(nextId++, exercise, 16.0, 10, side, nextId)

    private fun session(vararg sets: SetLog) = LiftSession("s$nextId", 0, 60_000, "", sets.toList())

    @Test
    fun `a built-in exercise maps primary to full worked and also to full assisted`() {
        val map = MuscleMap.of(BuiltInExercise.TwoHandSwing)
        assertFalse(map.isEmpty)
        assertEquals(listOf(Muscle.Glutes, Muscle.Hamstrings), map.primary)
        assertEquals(listOf(Muscle.LowerBack, Muscle.Abs, Muscle.Forearms), map.assistedOnly)
        assertEquals(1f, map.primaryIntensity(Muscle.Glutes))
        assertEquals(0f, map.assistedIntensity(Muscle.Glutes))
        assertEquals(0f, map.primaryIntensity(Muscle.Abs))
        assertEquals(1f, map.assistedIntensity(Muscle.Abs))
        assertEquals(0f, map.primaryIntensity(Muscle.Chest))
    }

    @Test
    fun `a custom exercise maps its focus groups as assisted and unknown is empty`() {
        val core = CustomExercise("c", "Ab wheel", Equipment.Mat, Measure.Reps, false, Focus.Core)
        val map = MuscleMap.of(core)
        assertTrue(map.primary.isEmpty())
        assertEquals(MuscleGroup.Core.muscles, map.assistedOnly.toSet())
        assertEquals(1f, map.assistedIntensity(Muscle.Abs))

        val gone = UnknownExercise("custom:gone", Equipment.Kettlebell, Measure.Reps)
        assertTrue(MuscleMap.of(gone).isEmpty)
    }

    @Test
    fun `session sets weight unilateral half and normalize intensity by highest muscle`() {
        // 2 sets GobletSquat (Quads=2, Glutes=2) + 1 L + 1 R Press (Shoulders=1, Triceps=1)
        val s = session(
            set(BuiltInExercise.GobletSquat),
            set(BuiltInExercise.GobletSquat),
            set(BuiltInExercise.Press, Side.Left),
            set(BuiltInExercise.Press, Side.Right),
        )
        val map = MuscleMap.of(s)
        assertEquals(listOf(Muscle.Quads, Muscle.Glutes, Muscle.Shoulders, Muscle.Triceps), map.primary)
        assertEquals(1f, map.primaryIntensity(Muscle.Quads))
        assertEquals(0.5f, map.primaryIntensity(Muscle.Shoulders))
        assertEquals(0f, map.primaryIntensity(Muscle.UpperBack))
        assertTrue(Muscle.UpperBack in map.assistedOnly)
        assertEquals(1f, map.assistedIntensity(Muscle.UpperBack))
        assertEquals(2f / 3f, map.assistedIntensity(Muscle.Abs))
    }

    @Test
    fun `balance and program weekBalance populate per-muscle map`() {
        val balance = MuscleBalance.of(
            listOf(session(set(BuiltInExercise.BarbellRow)), session(set(BuiltInExercise.BarbellRow))),
        )!!
        assertEquals(listOf(Muscle.Lats, Muscle.UpperBack), balance.muscles.primary)
        assertEquals(0f, balance.muscles.primaryIntensity(Muscle.LowerBack))
        assertTrue(Muscle.LowerBack in balance.muscles.assistedOnly)

        val program = Program(
            ProgramKey.Custom("p"),
            listOf(listOf(Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 10))),
            daysPerWeek = 3,
            weeks = 4,
        )
        val week = program.weekBalance()
        assertEquals(15.0, week.muscles.muscles[Muscle.Glutes]?.worked)
        assertEquals(15.0, week.muscles.muscles[Muscle.LowerBack]?.assisted)
    }
}
