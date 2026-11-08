package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafetyTest {
    private val bells = listOf(12.0, 16.0, 20.0, 24.0)

    private fun set(exercise: Exercise, kg: Double, double: Boolean = false) =
        SetLog.of(0, exercise, kg, 10, null, 0, double = double)

    @Test
    fun `a ballistic lift is at a new bell above every one it was done at`() {
        val history = listOf(set(BuiltInExercise.OneHandSwing, 16.0), set(BuiltInExercise.OneHandSwing, 20.0))
        assertTrue(BuiltInExercise.OneHandSwing.firstAtBell(Load.Bell(24.0), bells, history))
        assertFalse(BuiltInExercise.OneHandSwing.firstAtBell(Load.Bell(20.0), bells, history))
        // Back down to a bell under the heaviest done is no new bell.
        assertFalse(BuiltInExercise.OneHandSwing.firstAtBell(Load.Bell(16.0), bells, history))
    }

    @Test
    fun `each lift has its own bells and a pair is new apart from one`() {
        val history = listOf(set(BuiltInExercise.TwoHandSwing, 24.0), set(BuiltInExercise.Clean, 16.0, double = true))
        assertTrue(BuiltInExercise.OneHandSwing.firstAtBell(Load.Bell(24.0), bells, history))
        assertTrue(BuiltInExercise.Clean.firstAtBell(Load.Bell(16.0), bells, history))
        assertFalse(BuiltInExercise.Clean.firstAtBell(Load.Bell(16.0, double = true), bells, history))
    }

    @Test
    fun `a lift never done is new at any bell with a lighter one to groove`() {
        assertTrue(BuiltInExercise.Snatch.firstAtBell(Load.Bell(16.0), bells, emptyList()))
        // With nothing lighter owned there is nothing to groove with.
        assertFalse(BuiltInExercise.Snatch.firstAtBell(Load.Bell(12.0), bells, emptyList()))
        assertFalse(BuiltInExercise.Snatch.firstAtBell(Load.Bell(16.0), listOf(16.0), emptyList()))
    }

    @Test
    fun `only a ballistic lift at a bell grooves first`() {
        assertFalse(BuiltInExercise.GobletSquat.firstAtBell(Load.Bell(24.0), bells, emptyList()))
        assertFalse(BuiltInExercise.BarbellPowerSnatch.firstAtBell(Load.Bar(40.0), bells, emptyList()))
    }
}
