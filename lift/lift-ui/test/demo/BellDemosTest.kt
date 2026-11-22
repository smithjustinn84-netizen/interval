package dev.justinsmith.lift.ui.demo

import dev.justinsmith.interval.ui.BodyJoints
import dev.justinsmith.interval.ui.KettlebellReps
import dev.justinsmith.interval.ui.RepBench
import dev.justinsmith.interval.ui.RepHold
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What each kettlebell lift's rep must show, beyond what every rep keeps to. */
class BellDemosTest {
    @Test
    fun `the deadlift sets the bell near the floor and stands up with it`() {
        val floor = DeadliftRep.jointsAt(DeadliftRep.still)
        assertTrue(floor.kettlebellCenter.y in 0.06f..0.22f, "The bell stops at ${floor.kettlebellCenter}")
        val top = DeadliftRep.jointsAt(0.55f)
        assertTrue(top.hip.y > floor.hip.y + 0.12f, "The hips never rise")
    }

    @Test
    fun `the snatch locks out overhead with the bell resting on the forearm`() {
        val top = KettlebellReps.Snatch.jointsAt(KettlebellReps.Snatch.still)
        assertTrue(
            top.kettlebellAnchor.y > top.headCenter.y + 0.06f,
            "Hand stops under head at ${top.kettlebellAnchor}",
        )
        assertTrue(top.kettlebellCenter.y < top.kettlebellAnchor.y, "Bell stands over fist instead of resting")
    }

    @Test
    fun `the half snatch pulls the bell down to the rack before the next swing`() {
        val rack = HalfSnatchRep.jointsAt(0.70f)
        assertTrue(rack.kettlebellAnchor.y < rack.headCenter.y, "Fist is at ${rack.kettlebellAnchor}")
        assertTrue(rack.kettlebellCenter.y < rack.kettlebellAnchor.y, "Bell sits over fist in the rack")
    }

    @Test
    fun `the high pull brings the bell to the chest with the elbow back`() {
        val top = HighPullRep.jointsAt(HighPullRep.still)
        assertTrue(top.elbow.x < top.shoulder.x - 0.08f, "The elbow is in front of the shoulder")
        assertTrue(top.kettlebellCenter.y > top.hip.y + 0.12f, "The bell hangs low at ${top.kettlebellCenter}")
    }

    @Test
    fun `a one hand swing leaves the far hand free`() {
        val float = KettlebellReps.OneHandSwing.jointsAt(KettlebellReps.OneHandSwing.still)
        assertTrue(float.farBellAnchor.y < float.kettlebellAnchor.y - 0.15f, "The free hand follows the bell up")
    }

    @Test
    fun `the clean lands the bell on the forearm in the rack`() {
        val rack = KettlebellReps.Clean.jointsAt(KettlebellReps.Clean.still)
        assertTrue(rests(rack), "The bell hangs past the fist in the rack")
        assertTrue(!rests(KettlebellReps.Clean.jointsAt(0f)), "The hiked bell rests on the forearm")
    }

    @Test
    fun `a press locks the bell out overhead from the rack`() {
        listOf(KettlebellReps.Press, PushPressRep, JerkRep, BottomsUpPressRep).forEach { rep ->
            val top = rep.jointsAt(rep.still)
            assertTrue(
                top.kettlebellAnchor.y > top.headCenter.y + 0.06f,
                "Fist stops under head at ${top.kettlebellAnchor}",
            )
            val rack = rep.jointsAt(0f)
            assertTrue(rack.kettlebellAnchor.y < rack.headCenter.y, "Press starts with fist over head")
        }
    }

    @Test
    fun `the bottoms up press keeps the bell over the fist`() {
        (0..20).map { BottomsUpPressRep.jointsAt(it / 20f) }.forEach { j ->
            assertTrue(
                j.kettlebellCenter.y > j.kettlebellAnchor.y + 0.05f,
                "Bell tips off fist at ${j.kettlebellCenter}",
            )
        }
    }

    @Test
    fun `the jerk drops under the bell before standing tall with it`() {
        val under = JerkRep.jointsAt(0.45f)
        val tall = JerkRep.jointsAt(0.68f)
        assertTrue(under.hip.y < tall.hip.y - 0.03f, "The jerk never drops under")
        assertTrue(under.kettlebellAnchor.y > under.headCenter.y, "The arm isn't locked out under the bell")
    }

    @Test
    fun `the squats sit the hips below the knees`() {
        listOf(FrontSquatRep to FrontSquatRep.still, ThrusterRep to 0.36f).forEach { (rep, bottom) ->
            val j = rep.jointsAt(bottom)
            assertTrue(j.hip.y < j.knee.y, "The hips stop at ${j.hip}, above the knees ${j.knee}")
        }
        val top = ThrusterRep.jointsAt(ThrusterRep.still)
        assertTrue(top.kettlebellAnchor.y > top.headCenter.y, "The thruster's press stops at ${top.kettlebellAnchor}")
    }

    @Test
    fun `the complexes press and squat between their cleans`() {
        listOf(KettlebellReps.CleanAndPress, LongCycleRep, IronCardioRep, ArmorBuildingComplexRep).forEach { rep ->
            val frames = (0..200).map { rep.jointsAt(it / 200f) }
            assertTrue(
                frames.any {
                    it.kettlebellAnchor.y > it.headCenter.y + 0.06f
                },
                "A complex never locks out overhead",
            )
            assertTrue(frames.any { rests(it) }, "A complex never racks the bell")
        }
        assertTrue((0..400).any { IronCardioRep.jointsAt(it / 400f).hip.y < IronCardioRep.jointsAt(it / 400f).knee.y })
    }

    @Test
    fun `the armor building complex squats three times with a bell in each hand`() {
        val rep = ArmorBuildingComplexRep
        assertEquals(RepHold.Pair, rep.hold)
        val deep = (0..800).map { rep.jointsAt(it / 800f).let { j -> j.hip.y < j.knee.y } }
        assertEquals(3, deep.zipWithNext().count { (was, now) -> !was && now }, "The squats in a rep")
    }

    @Test
    fun `the lunge lowers the back knee toward the floor with the front foot planted`() {
        val bottom = RackedLungeDemo.jointsAt(RackedLungeDemo.still)
        assertTrue(bottom.farKnee.y < 0.14f, "The back knee stops high at ${bottom.farKnee}")
        assertTrue(bottom.farAnkle.x < bottom.hip.x - 0.12f, "The back foot never steps back")
        assertEquals(0.04f, bottom.ankle.y, 0.002f)
    }

    @Test
    fun `the renegade row rows one bell off the floor while the other holds the plank`() {
        val frames = (0..20).map { RenegadeRowDemo.jointsAt(it / 20f) }
        frames.forEach { j ->
            assertTrue(abs(frames.first().farBellCenter.y - j.farBellCenter.y) < 0.01f, "The far bell leaves the floor")
            assertTrue(j.farBellCenter.y > 0f, "The far bell sinks through the floor at ${j.farBellCenter}")
        }
        val rowed = RenegadeRowDemo.jointsAt(RenegadeRowDemo.still)
        assertTrue(rowed.kettlebellCenter.y > frames.first().kettlebellCenter.y + 0.06f, "Near bell stays down")
    }

    @Test
    fun `the kettlebell dead bug holds the bell still over the chest as the legs reach`() {
        val held = KettlebellDeadBugDemo.jointsAt(0f).kettlebellCenter
        (0..20).map { KettlebellDeadBugDemo.jointsAt(it / 20f) }.forEach { j ->
            assertTrue((j.kettlebellCenter - held).getDistance() < 0.01f, "The bell moves to ${j.kettlebellCenter}")
        }
    }

    @Test
    fun `the row braces the free hand on the bench and pulls the elbow back`() {
        val bench = RowDemo.props.single() as RepBench
        (0..20).map { RowDemo.jointsAt(it / 20f) }.forEach { j ->
            assertTrue(
                j.farBellAnchor.x in bench.from..bench.to,
                "The free hand misses the bench at ${j.farBellAnchor}",
            )
        }
        val top = RowDemo.jointsAt(RowDemo.still)
        assertTrue(top.elbow.x < top.shoulder.x - 0.08f, "The elbow stops at ${top.elbow}")
    }

    @Test
    fun `the windmill bends to the side under a bell held straight up`() {
        val down = WindmillDemo.jointsAt(WindmillDemo.still)
        assertTrue(down.front, "The windmill is seen from the front")
        assertTrue(down.kettlebellAnchor.y > down.shoulder.y + 0.25f, "The bell arm leans at ${down.kettlebellAnchor}")
        assertTrue(abs(down.kettlebellAnchor.x - down.shoulder.x) < 0.04f, "The bell arm leans")
        assertTrue(down.neck.x < down.hips.x - 0.16f, "The trunk stays over the hips at ${down.neck}")
        assertTrue(down.farBellAnchor.y < down.hip.y, "The free hand never reaches down")
        assertEquals(WindmillDemo.jointsAt(0f).farAnkle, down.farAnkle, "The far foot moved")
    }

    @Test
    fun `the halo circles the bell past each ear and behind the head`() {
        val places = (0..40).map { HaloDemo.jointsAt(it / 40f) }
        assertTrue(places.any { it.kettlebellCenter.x > it.headCenter.x + 0.07f }, "Bell never passes near ear")
        assertTrue(places.any { it.kettlebellCenter.x < it.headCenter.x - 0.07f }, "Bell never passes far ear")
        assertFalse(places.first().armsBehind, "Bell starts behind the head")
        assertTrue(places[20].armsBehind, "Bell never goes behind the head")
    }

    @Test
    fun `a carry walks on the spot with each foot in front in turn`() {
        listOf(FarmersWalkDemo, SuitcaseCarryDemo, RackCarryDemo, BottomsUpCarryDemo).forEach { rep ->
            val frames = (0..40).map { rep.jointsAt(it / 40f) }
            frames.forEach { assertEquals(0f, it.hip.x, 0.002f) }
            assertTrue(frames.any { it.ankle.x > it.farAnkle.x + 0.08f }, "The near foot never leads")
            assertTrue(frames.any { it.farAnkle.x > it.ankle.x + 0.08f }, "The far foot never leads")
        }
        val bottomsUp = BottomsUpCarryDemo.jointsAt(0.3f)
        assertTrue(bottomsUp.kettlebellCenter.y > bottomsUp.kettlebellAnchor.y + 0.05f, "Bottoms-up bell tips off")
    }

    @Test
    fun `the get up rises from the floor to standing under a bell held straight up`() {
        val lying = KettlebellReps.GetUp.jointsAt(0f)
        assertTrue(lying.headCenter.y < 0.15f, "The get-up starts with the head at ${lying.headCenter}")
        val tall = KettlebellReps.GetUp.jointsAt(0.5f)
        assertTrue(tall.headCenter.y > 0.85f, "The get-up never stands")
    }

    private fun rests(j: BodyJoints): Boolean {
        val forearm = j.kettlebellAnchor - j.elbow
        val reach = j.kettlebellCenter - j.kettlebellAnchor
        return forearm.x * reach.x + forearm.y * reach.y < 0f
    }
}
