package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MovementTest {
    @Test
    fun `the quick lifts are the thrown ones`() {
        // Every lift paced at two seconds a rep is thrown by the hips, so the pace and the tempo agree.
        val quick = BuiltInExercise.entries.filter { it.secondsPerRep == 2 }
        assertTrue(quick.isNotEmpty())
        quick.forEach { assertEquals(Tempo.Ballistic, it.movement.tempo, "$it") }
    }

    @Test
    fun `a timed lift is held and a swing is a hinge never a squat`() {
        BuiltInExercise.entries.filter { it.measure == Measure.Time }
            .forEach { assertEquals(Tempo.Hold, it.movement.tempo, "$it") }
        assertEquals(Pattern.Hinge, BuiltInExercise.TwoHandSwing.movement.pattern)
        assertEquals(Pattern.Squat, BuiltInExercise.GobletSquat.movement.pattern)
        assertEquals(Pattern.Lunge, BuiltInExercise.WalkingLunge.movement.pattern)
        assertEquals(Pattern.Pull, BuiltInExercise.RenegadeRow.movement.pattern)
        assertEquals(Pattern.Core, BuiltInExercise.KettlebellDeadBug.movement.pattern)
    }

    @Test
    fun `a complex counts every lift in a rep`() {
        assertEquals(6, BuiltInExercise.ArmorBuildingComplex.movement.perRep)
        assertEquals(3, BuiltInExercise.IronCardio.movement.perRep)
        assertEquals(1, BuiltInExercise.Snatch.movement.perRep)
    }

    @Test
    fun `the mat and bodyweight spare the grip and a bell one-handed tests it most`() {
        BuiltInExercise.entries.filter {
            it.equipment == Equipment.Mat
        }.forEach { assertEquals(0, it.movement.grip, "$it") }
        assertEquals(Movement.MAX_GRIP, BuiltInExercise.Snatch.movement.grip)
        assertEquals(Movement.MAX_GRIP, BuiltInExercise.BottomsUpCarry.movement.grip)
    }

    @Test
    fun `a lift the user made is held when timed and a grind otherwise`() {
        val carry =
            CustomExercise("a", "Sandbag carry", Equipment.Kettlebell, Measure.Time, perSide = false, Focus.Core)
        val curl = CustomExercise("b", "Curl", Equipment.Band, Measure.Reps, perSide = false, Focus.UpperBody)
        assertEquals(Movement(Pattern.Other, Tempo.Hold, grip = 1), carry.movement)
        assertEquals(Movement(Pattern.Other, Tempo.Grind, grip = 0), curl.movement)
    }
}
