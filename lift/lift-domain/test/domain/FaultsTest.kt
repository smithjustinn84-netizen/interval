package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaultsTest {
    @Test
    fun `the thrown kettlebell lifts are prone to the swing's faults and the snatch to a late hand first`() {
        val swing = listOf(Fault.SquattedHinge, Fault.DeathGrip, Fault.BentWrist)
        assertEquals(swing, BuiltInExercise.TwoHandSwing.faults)
        assertEquals(swing, BuiltInExercise.Clean.faults)
        assertEquals(listOf(Fault.LateHand) + swing, BuiltInExercise.Snatch.faults)
        assertEquals(listOf(Fault.SquattedHinge), BuiltInExercise.Deadlift.faults)
    }

    @Test
    fun `the lifts with faults are the kettlebell hinges under load`() {
        val prone = BuiltInExercise.entries.filter { it.faults.isNotEmpty() }.toSet()
        assertEquals(
            setOf(
                BuiltInExercise.TwoHandSwing, BuiltInExercise.OneHandSwing, BuiltInExercise.Clean,
                BuiltInExercise.Snatch, BuiltInExercise.HalfSnatch, BuiltInExercise.HighPull,
                BuiltInExercise.LongCycle, BuiltInExercise.IronCardio, BuiltInExercise.ArmorBuildingComplex,
                BuiltInExercise.CleanAndPress, BuiltInExercise.Deadlift,
            ),
            prone,
        )
    }

    @Test
    fun `a squat a press and the bar's lifts have none`() {
        listOf(BuiltInExercise.GobletSquat, BuiltInExercise.Press, BuiltInExercise.BarbellDeadlift)
            .forEach { assertTrue(it.faults.isEmpty(), "$it") }
    }
}
