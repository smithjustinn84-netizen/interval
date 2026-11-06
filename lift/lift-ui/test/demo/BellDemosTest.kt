package dev.justinsmith.lift.ui.demo

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What each bell lift's figure must show, beyond what every demo keeps to. */
class BellDemosTest {
    @Test
    fun `the deadlift sets the bell on the floor and stands up with it`() {
        val floor = DeadliftDemo.figureAt(DeadliftDemo.still)
        assertTrue(abs(floor.bell.y + Rig.BELL - Rig.FLOOR) < 3f, "The bell stops at ${floor.bell}")
        val top = DeadliftDemo.figureAt(0.52f)
        assertTrue(top.hip.y < floor.hip.y - 40f, "The hips never rise")
    }

    @Test
    fun `the snatch turns the bell over the hand once a rep and locks it out overhead`() {
        val turn = SnatchDemo.poseAt(1f).bell - SnatchDemo.poseAt(0f).bell
        assertEquals(360f, turn, 0.01f)
        val top = SnatchDemo.figureAt(0.5f)
        assertTrue(top.hand.y < top.head.y, "The hand stops at ${top.hand}, under the head")
        assertTrue(top.bell.y > top.hand.y, "The bell stands over the fist rather than resting on the forearm")
    }

    @Test
    fun `the half snatch pulls the bell down to the rack before the next swing`() {
        val rack = HalfSnatchDemo.figureAt(0.62f)
        assertTrue(rack.hand.y < rack.shoulder.y + 10f && rack.hand.y > rack.head.y, "The fist is at ${rack.hand}")
        assertTrue(rack.bell.y > rack.hand.y, "The bell sits over the fist in the rack")
    }

    @Test
    fun `the high pull brings the bell to the chest with the elbow back`() {
        val top = HighPullDemo.figureAt(HighPullDemo.still)
        assertTrue(top.elbow.x < top.shoulder.x, "The elbow is in front of the shoulder")
        assertTrue(top.bell.y < top.hip.y - 40f, "The bell hangs at ${top.bell}, below the chest")
    }

    @Test
    fun `a one hand swing leaves the far hand free`() {
        val float = OneHandSwingDemo.figureAt(OneHandSwingDemo.still)
        assertTrue(float.farHand.y > float.hand.y + 40f, "The free hand follows the bell up")
    }
}
