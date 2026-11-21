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
        val swing = assertNotNull(BuiltInExercise.TwoHandSwing.demo).framed()
        val wrong = assertNotNull(swing.doneWrong(Fault.SquattedHinge))
        val right = swing.figureAt(0f)
        val squatted = wrong.figureAt(0f)
        assertTrue(squatted.knee.x > right.knee.x + 15f, "The knees stay back at ${squatted.knee}")
        assertTrue(squatted.hip.y > right.hip.y + 5f, "The hips stay up at ${squatted.hip}")
        assertTrue(squatted.shoulder.y < right.shoulder.y, "The chest stays down at ${squatted.shoulder}")
        // Standing tall at the float, it is done as it should be.
        val top = 0.46f
        assertEquals(swing.figureAt(top).hip, wrong.figureAt(top).hip)
    }

    @Test
    fun `a fault a figure can't show has no figure and one shown keeps the demo's rep`() {
        val swing = assertNotNull(BuiltInExercise.TwoHandSwing.demo).framed()
        listOf(Fault.DeathGrip, Fault.BentWrist, Fault.LateHand).forEach { assertNull(swing.doneWrong(it)) }
        val wrong = assertNotNull(swing.doneWrong(Fault.SquattedHinge))
        assertEquals(swing.phases, wrong.phases)
        assertEquals(swing.setUp, wrong.setUp)
        assertEquals(swing.view, wrong.view)
        assertEquals(swing.secondsPerRep, wrong.secondsPerRep)
    }

    @Test
    fun `every lift shown done wrong stays in its view and its weight off the floor`() {
        BuiltInExercise.entries.forEach { exercise ->
            val demo = exercise.demo?.framed() ?: return@forEach
            exercise.faults.mapNotNull { demo.doneWrong(it) }.forEach { wrong ->
                (-10..100).forEach { step ->
                    val at = step / 100f
                    val figure = wrong.figureAt(at)
                    val drawn = if (wrong.hold == Hold.Pair) figure.joints else figure.joints - figure.farBell
                    drawn.forEach { assertTrue(wrong.view.contains(it), "$exercise at $at: $it is out of view") }
                    drawn.forEach { assertTrue(it.y <= Rig.FLOOR + 0.5f, "$exercise at $at: $it is through the floor") }
                    assertTrue(
                        figure.bell.y + Rig.BELL <= Rig.FLOOR + 0.5f,
                        "$exercise at $at: the bell is in the floor",
                    )
                }
            }
        }
    }

    @Test
    fun `a step goes on to the next part and round from the last one to the first`() {
        val swing = assertNotNull(BuiltInExercise.TwoHandSwing.demo)
        val starts = swing.stretches.map { it.first }
        assertEquals(listOf(0f, 0.26f, 0.66f), starts)
        assertEquals(0.26f, swing.stepAfter(0f))
        assertEquals(0.66f, swing.stepAfter(0.3f))
        // Just short of a part's start is at it already, so a step goes on past it.
        assertEquals(0f, swing.stepAfter(0.659f))
        assertEquals(0f, swing.stepAfter(0.99f))
        // From the set-up, into the rep.
        assertEquals(0f, assertNotNull(BuiltInExercise.GobletSquat.demo).stepAfter(-0.5f))
    }
}
