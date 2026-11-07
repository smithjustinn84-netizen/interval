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

    @Test
    fun `the complexes press and squat between their cleans`() {
        listOf(CleanAndPressDemo, LongCycleDemo, IronCardioDemo, ArmorBuildingComplexDemo).forEach { demo ->
            val figures = (0..200).map { demo.figureAt(it / 200f) }
            assertTrue(figures.any { it.hand.y < it.head.y - Rig.HEAD }, "A complex never locks out overhead")
            assertTrue(figures.any { rests(it) }, "A complex never racks the bell")
        }
        val squats = (0..400).map { IronCardioDemo.figureAt(it / 400f).hip.y > Rig.FLOOR - Rig.SHIN - 10f }
        assertTrue(squats.any { it }, "Iron Cardio never squats")
    }

    @Test
    fun `the armor building complex squats three times with a bell in each hand`() {
        val demo = ArmorBuildingComplexDemo
        assertEquals(Hold.Pair, demo.hold)
        val deep = (0..800).map { demo.figureAt(it / 800f).hip.y > demo.figureAt(it / 800f).knee.y }
        assertEquals(3, deep.zipWithNext().count { (was, now) -> !was && now }, "The squats in a rep")
        val top = demo.figureAt(demo.still)
        assertTrue((top.farBell - top.bell).getDistance() < 2 * Rig.FAR_ARM_OFFSET, "The far bell lags the near one")
    }

    @Test
    fun `the lunge lowers the back knee toward the floor with the front foot planted`() {
        val bottom = RackedLungeDemo.figureAt(RackedLungeDemo.still)
        assertTrue(Rig.FLOOR - bottom.farKnee.y < 20f, "The back knee stops at ${bottom.farKnee}")
        assertTrue(bottom.farAnkle.x < bottom.hip.x - 40f, "The back foot never steps back")
        assertEquals(Rig.FLOOR, bottom.ankle.y)
    }

    @Test
    fun `the row braces the free hand on the bench and pulls the elbow back`() {
        val bench = RowDemo.props.single() as Bench
        (0..20).map { RowDemo.figureAt(it / 20f) }.forEach { figure ->
            assertTrue(abs(figure.farHand.y - (Rig.FLOOR - bench.height)) < 6f, "The free hand leaves the bench")
            assertTrue(figure.farHand.x in bench.from..bench.to, "The free hand misses the bench")
        }
        val top = RowDemo.figureAt(RowDemo.still)
        assertTrue(top.elbow.x < top.shoulder.x - 30f, "The elbow stops at ${top.elbow}")
    }

    @Test
    fun `the windmill hinges under a bell held straight up`() {
        val down = WindmillDemo.figureAt(WindmillDemo.still)
        assertTrue(down.hand.y < down.shoulder.y - 90f, "The bell arm leans at ${down.hand}")
        assertTrue(abs(down.hand.x - down.shoulder.x) < 10f, "The bell arm leans at ${down.hand}")
        assertTrue(down.farHand.y > down.hip.y, "The free hand never reaches down")
    }

    @Test
    fun `the halo circles the bell in front of the face and behind the head`() {
        val places = (0..40).map { HaloDemo.figureAt(it / 40f) }
        assertTrue(places.any { it.bell.x > it.head.x + Rig.HEAD }, "The bell never passes in front of the face")
        assertTrue(places.any { it.bell.x < it.head.x }, "The bell never goes behind the head")
        places.forEach { assertTrue(it.bell.y < it.shoulder.y, "The bell drops to ${it.bell}, under the shoulders") }
    }

    @Test
    fun `a carry walks on the spot with each foot in front in turn`() {
        listOf(FarmersWalkDemo, SuitcaseCarryDemo, RackCarryDemo, BottomsUpCarryDemo).forEach { demo ->
            val figures = (0..40).map { demo.figureAt(it / 40f) }
            figures.forEach { assertEquals(Rig.ANKLE_X, it.hip.x, 0.01f) }
            assertTrue(figures.any { it.ankle.x > it.farAnkle.x + 30f }, "The near foot never leads")
            assertTrue(figures.any { it.farAnkle.x > it.ankle.x + 30f }, "The far foot never leads")
        }
        val farmer = FarmersWalkDemo.figureAt(0f)
        assertTrue(farmer.farBell.y > farmer.hip.y - 30f, "The farmer's far bell isn't at the side")
        val bottomsUp = BottomsUpCarryDemo.figureAt(0.3f)
        assertTrue(bottomsUp.bell.y < bottomsUp.hand.y - 20f, "The bottoms-up bell tips off the fist")
    }

    @Test
    fun `the get up rises from the floor to standing under a bell held straight up`() {
        val lying = GetUpDemo.figureAt(0f)
        assertTrue(Rig.FLOOR - lying.head.y < 40f, "The get-up starts with the head at ${lying.head}")
        val tall = GetUpDemo.figureAt(0.5f)
        assertTrue(tall.head.y < Rig.FLOOR - Rig.SHIN - Rig.THIGH - Rig.TORSO, "The get-up never stands")
        (0..100).map { GetUpDemo.figureAt(it / 100f) }.forEach { figure ->
            assertTrue(abs(figure.hand.x - figure.shoulder.x) < 6f, "The bell arm leans at ${figure.hand}")
            assertTrue(figure.hand.y < figure.shoulder.y - 90f, "The bell arm bends at ${figure.hand}")
        }
    }

    /** Whether the bell lies back along the forearm, as in the rack, rather than past the fist. */
    private fun rests(figure: Figure): Boolean {
        val forearm = figure.hand - figure.elbow
        val reach = figure.bell - figure.hand
        return forearm.x * reach.x + forearm.y * reach.y < 0f
    }
}
