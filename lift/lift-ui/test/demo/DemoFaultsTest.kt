package dev.justinsmith.lift.ui.demo

import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Fault
import dev.justinsmith.lift.domain.faults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoFaultsTest {
    @Test
    fun `a squatted swing hikes with the knees forward and the chest up`() {
        val swing = assertNotNull(BuiltInExercise.TwoHandSwing.rep)
        val wrong = assertNotNull(swing.doneWrong(Fault.SquattedHinge))
        val right = swing.jointsAt(0f)
        val squatted = wrong.jointsAt(0f)
        assertTrue(squatted.knee.x > right.knee.x + 0.08f, "The knees stay back at ${squatted.knee}")
        assertTrue(squatted.hip.y < right.hip.y - 0.03f, "The hips stay up at ${squatted.hip}")
        assertTrue(squatted.shoulder.y > right.shoulder.y, "The chest stays down at ${squatted.shoulder}")
        // Standing tall at the float, it is done as it should be.
        assertEquals(swing.jointsAt(swing.still).hip, wrong.jointsAt(wrong.still).hip)
    }

    @Test
    fun `a fault a rep can't show returns null and one shown keeps the rep's phases and timing`() {
        val swing = assertNotNull(BuiltInExercise.TwoHandSwing.rep)
        listOf(Fault.DeathGrip, Fault.BentWrist, Fault.LateHand).forEach { assertNull(swing.doneWrong(it)) }
        val wrong = assertNotNull(swing.doneWrong(Fault.SquattedHinge))
        assertEquals(swing.phases, wrong.phases)
        assertEquals(swing.setUp, wrong.setUp)
        assertEquals(swing.seconds, wrong.seconds)
    }

    @Test
    fun `every lift shown done wrong keeps its joints and bell off the floor`() {
        BuiltInExercise.entries.forEach { exercise ->
            val rep = exercise.rep ?: return@forEach
            exercise.faults.mapNotNull { rep.doneWrong(it) }.forEach { wrong ->
                (0..100).forEach { step ->
                    val at = step / 100f
                    val j = wrong.jointsAt(at)
                    assertTrue(
                        j.kettlebellCenter.y > 0.04f,
                        "$exercise at $at: the bell is in the floor at ${j.kettlebellCenter}",
                    )
                }
            }
        }
    }

    @Test
    fun `a step goes on to the next part and round from the last one to the first`() {
        val swing = assertNotNull(BuiltInExercise.TwoHandSwing.rep)
        val starts = swing.phases.map { it.start }
        assertEquals(starts[1], swing.stepAfter(0f))
        assertEquals(starts[2], swing.stepAfter(starts[1]))
        assertEquals(starts[0], swing.stepAfter(0.99f))
        val airSquat = assertNotNull(BuiltInExercise.AirSquat.rep)
        assertTrue(airSquat.stepAfter(-0.5f) >= 0f)
    }
}
