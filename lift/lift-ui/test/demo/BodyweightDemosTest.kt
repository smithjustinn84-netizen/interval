package dev.justinsmith.lift.ui.demo

import dev.justinsmith.interval.ui.ExerciseRep
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** What each bodyweight lift's rep must show, beyond what every rep keeps to. */
class BodyweightDemosTest {
    @Test
    fun `a push up keeps its hands and toes put while the chest lowers`() {
        listOf(PushUpDemo, InclinePushUpDemo).forEach { rep ->
            val frames = frames(rep)
            val toes = frames.map { it.toe }
            assertTrue(toes.all { (it - toes.first()).getDistance() < 0.08f }, "The toes slide")
            frames.forEach { assertTrue((it.kettlebellAnchor - rep.anchor).getDistance() < 0.01f, "The hands slide") }
            val top = rep.jointsAt(0f)
            val bottom = rep.jointsAt(rep.still)
            assertTrue(bottom.shoulder.y < top.shoulder.y - 0.12f, "Chest only drops to ${bottom.shoulder}")
            assertTrue(bottom.elbow.x < bottom.kettlebellAnchor.x, "The elbows flare forward")
        }
    }

    @Test
    fun `a pull up hangs from the bar and brings the chin over it`() {
        listOf(PullUpDemo, ChinUpDemo).forEach { rep ->
            frames(rep).forEach { j ->
                assertTrue(j.ankle.y > 0.02f && j.toe.y > 0.02f, "The feet touch down at ${j.ankle}")
            }
            val top = rep.jointsAt(rep.still)
            assertTrue(top.headCenter.y > rep.anchor.y - 0.04f, "The chin stays under the bar at ${top.headCenter}")
            assertTrue(rep.jointsAt(0f).shoulder.y < rep.anchor.y - 0.25f, "The hang isn't a dead hang")
        }
    }

    @Test
    fun `the inverted row pivots about the heels up to the bar`() {
        val heels = frames(InvertedRowDemo).map { it.heel }
        assertTrue(heels.all { (it - heels.first()).getDistance() < 0.08f }, "The heels move")
        val top = InvertedRowDemo.jointsAt(InvertedRowDemo.still)
        assertTrue((top.shoulder - InvertedRowDemo.anchor).getDistance() < 0.22f, "Chest stops short of bar")
    }

    @Test
    fun `the squats and lunges sit low and stand tall`() {
        listOf(AirSquatDemo, SplitSquatDemo, ReverseLungeDemo, BulgarianSplitSquatDemo).forEach { rep ->
            val low = rep.jointsAt(rep.still)
            val tall = rep.jointsAt(0f)
            assertTrue(low.hip.y < tall.hip.y - 0.12f, "It only drops to ${low.hip}")
        }
    }

    @Test
    fun `the step up stands on the box and the calf raise rises onto the toes`() {
        val top = StepUpDemo.jointsAt(StepUpDemo.still)
        assertTrue(abs(top.ankle.y - (StepUpDemo.anchor.y + 0.04f)) < 0.01f, "Foot isn't on the box")
        assertTrue(top.hip.y > StepUpDemo.jointsAt(0f).hip.y + 0.15f, "It never stands up on the box")
        val up = CalfRaiseDemo.jointsAt(CalfRaiseDemo.still)
        assertTrue(up.heel.y > 0.05f && abs(up.toe.y - 0.02f) < 0.01f, "The heels stay down")
    }

    @Test
    fun `the floor holds stay down and the bridges lift the hips`() {
        listOf(PlankDemo, SidePlankDemo, HollowHoldDemo, DeadBugDemo).forEach { rep ->
            frames(rep).forEach { assertTrue(it.hip.y < 0.30f, "It stands up at ${it.hip}") }
        }
        listOf(GluteBridgeDemo, SingleLegGluteBridgeDemo).forEach { rep ->
            val up = rep.jointsAt(rep.still)
            assertTrue(up.hip.y > rep.jointsAt(0f).hip.y + 0.08f, "The hips don't lift")
        }
        val held = SingleLegGluteBridgeDemo.jointsAt(SingleLegGluteBridgeDemo.still)
        assertTrue(held.farAnkle.y > 0.14f, "The free foot rests on the floor")
    }

    @Test
    fun `the burpee goes from standing to the floor and back and the climber drives each knee in`() {
        val burpee = frames(BurpeeDemo)
        assertTrue(burpee.any { it.shoulder.y < 0.25f }, "The burpee never reaches the floor")
        assertTrue(burpee.any { it.kettlebellAnchor.y > it.headCenter.y }, "The burpee never reaches overhead")
        val climber = frames(MountainClimberDemo)
        assertTrue(climber.any { it.knee.x > it.hip.x + 0.08f }, "The near knee never drives in")
        assertTrue(climber.any { it.farKnee.x > it.hip.x + 0.08f }, "The far knee never drives in")
    }

    private fun frames(rep: ExerciseRep) = (0..200).map { rep.jointsAt(it / 200f) }
}
