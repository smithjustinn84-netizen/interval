package dev.justinsmith.lift.ui.demo

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** What each bodyweight lift's figure must show, beyond what every demo keeps to. */
class BodyweightDemosTest {
    @Test
    fun `a push up keeps its hands and toes put while the chest lowers`() {
        listOf(PushUpDemo, InclinePushUpDemo).forEach { demo ->
            val figures = frames(demo)
            val toes = figures.map { it.toe }
            assertTrue(toes.all { (it - toes.first()).getDistance() < 8f }, "The toes slide")
            figures.forEach { assertTrue((it.hand - demo.anchor).getDistance() < 0.01f, "The hands slide") }
            val top = demo.figureAt(0f)
            val bottom = demo.figureAt(demo.still)
            assertTrue(bottom.shoulder.y > top.shoulder.y + 40f, "The chest only drops to ${bottom.shoulder}")
            assertTrue(bottom.elbow.x < bottom.hand.x, "The elbows flare forward")
        }
    }

    @Test
    fun `a pull up hangs from the bar and brings the chin over it`() {
        listOf(PullUpDemo, ChinUpDemo).forEach { demo ->
            frames(demo).forEach {
                assertTrue(it.joints.all { joint -> joint.y < Rig.FLOOR - 10f }, "The feet touch down")
            }
            val top = demo.figureAt(demo.still)
            assertTrue(top.head.y < demo.anchor.y, "The chin stays under the bar at ${top.head}")
            assertTrue(demo.figureAt(0f).shoulder.y > demo.anchor.y + 90f, "The hang isn't a dead hang")
        }
    }

    @Test
    fun `the inverted row pivots about the heels up to the bar`() {
        val heels = frames(InvertedRowDemo).map { it.heel }
        assertTrue(heels.all { abs(it.y - Rig.FLOOR) < 8f && abs(it.x - heels.first().x) < 8f }, "The heels move")
        val top = InvertedRowDemo.figureAt(InvertedRowDemo.still)
        assertTrue((top.shoulder - InvertedRowDemo.anchor).getDistance() < 50f, "The chest stops short of the bar")
    }

    @Test
    fun `the squats and lunges sit low and stand tall`() {
        listOf(AirSquatDemo, SplitSquatDemo, ReverseLungeDemo, BulgarianSplitSquatDemo).forEach { demo ->
            val low = demo.figureAt(demo.still)
            val tall = demo.figureAt(0f)
            assertTrue(low.hip.y > tall.hip.y + 40f, "It only drops to ${low.hip}")
        }
        val split = SplitSquatDemo.figureAt(0f)
        val bottom = SplitSquatDemo.figureAt(SplitSquatDemo.still)
        assertTrue((split.farToe - bottom.farToe).getDistance() < 1f, "The back foot moves in a split squat")
    }

    @Test
    fun `the step up stands on the box and the calf raise rises onto the toes`() {
        val top = StepUpDemo.figureAt(StepUpDemo.still)
        assertTrue(abs(top.ankle.y - (Rig.FLOOR - 80f)) < 0.01f, "The foot isn't on the box")
        assertTrue(top.hip.y < StepUpDemo.figureAt(0f).hip.y - 50f, "It never stands up on the box")
        val up = CalfRaiseDemo.figureAt(CalfRaiseDemo.still)
        assertTrue(up.heel.y < Rig.FLOOR - 10f && abs(up.toe.y - Rig.FLOOR) < 0.01f, "The heels stay down")
    }

    @Test
    fun `the floor holds stay down and the bridges lift the hips`() {
        listOf(PlankDemo, SidePlankDemo, HollowHoldDemo, DeadBugDemo).forEach { demo ->
            frames(demo).forEach { assertTrue(it.hip.y > Rig.FLOOR - 80f, "It stands up at ${it.hip}") }
        }
        listOf(GluteBridgeDemo, SingleLegGluteBridgeDemo).forEach { demo ->
            val up = demo.figureAt(demo.still)
            assertTrue(up.hip.y < demo.figureAt(0f).hip.y - 30f, "The hips don't lift")
        }
        val held = SingleLegGluteBridgeDemo.figureAt(SingleLegGluteBridgeDemo.still)
        assertTrue(held.farAnkle.y < Rig.FLOOR - 40f, "The free foot rests on the floor")
    }

    @Test
    fun `the burpee goes from standing to the floor and back and the climber drives each knee in`() {
        val burpee = frames(BurpeeDemo)
        assertTrue(burpee.any { it.shoulder.y > Rig.FLOOR - 70f }, "The burpee never reaches the floor")
        assertTrue(burpee.any { it.hand.y < it.head.y }, "The burpee never reaches overhead")
        val climber = frames(MountainClimberDemo)
        assertTrue(climber.any { it.knee.x > it.hip.x + 30f }, "The near knee never drives in")
        assertTrue(climber.any { it.farKnee.x > it.hip.x + 30f }, "The far knee never drives in")
    }

    private fun frames(demo: ExerciseDemo) = (0..200).map { demo.figureAt(it / 200f) }
}
