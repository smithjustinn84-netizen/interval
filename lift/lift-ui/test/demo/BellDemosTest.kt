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

    @Test
    fun `the clean lands the bell on the forearm in the rack`() {
        val rack = CleanDemo.figureAt(CleanDemo.still)
        assertTrue(rests(rack), "The bell hangs past the fist in the rack")
        assertTrue(abs(rack.hand.y - rack.shoulder.y) < 15f, "The fist is at ${rack.hand}, not the collarbone")
        assertTrue(!rests(CleanDemo.figureAt(0f)), "The hiked bell rests on the forearm")
    }

    @Test
    fun `a press locks the bell out overhead from the rack`() {
        listOf(PressDemo, PushPressDemo, JerkDemo, BottomsUpPressDemo).forEach { demo ->
            val top = demo.figureAt(demo.still)
            assertTrue(top.hand.y < top.head.y - Rig.HEAD, "The fist stops at ${top.hand}, under the head")
            val rack = demo.figureAt(0f)
            assertTrue(rack.hand.y > rack.head.y, "The press starts with the fist at ${rack.hand}, over the head")
        }
    }

    @Test
    fun `the bottoms up press keeps the bell over the fist`() {
        (0..20).map { BottomsUpPressDemo.figureAt(it / 20f) }.forEach { figure ->
            assertTrue(figure.bell.y < figure.hand.y - 20f, "The bell tips off the fist at ${figure.bell}")
        }
    }

    @Test
    fun `the jerk drops under the bell before standing tall with it`() {
        val under = JerkDemo.figureAt(0.44f)
        val tall = JerkDemo.figureAt(0.65f)
        assertTrue(under.hip.y > tall.hip.y + 10f, "The jerk never drops under")
        assertTrue(under.hand.y < under.head.y, "The arm isn't locked out under the bell")
    }

    @Test
    fun `the squats sit the hips below the knees`() {
        listOf(FrontSquatDemo to 0.54f, ThrusterDemo to 0.33f).forEach { (demo, bottom) ->
            val figure = demo.figureAt(bottom)
            assertTrue(figure.hip.y > figure.knee.y, "The hips stop at ${figure.hip}, above the knees")
        }
        val top = ThrusterDemo.figureAt(ThrusterDemo.still)
        assertTrue(top.hand.y < top.head.y, "The thruster's press stops at ${top.hand}")
    }

    /** Whether the bell lies back along the forearm, as in the rack, rather than past the fist. */
    private fun rests(figure: Figure): Boolean {
        val forearm = figure.hand - figure.elbow
        val reach = figure.bell - figure.hand
        return forearm.x * reach.x + forearm.y * reach.y < 0f
    }
}
