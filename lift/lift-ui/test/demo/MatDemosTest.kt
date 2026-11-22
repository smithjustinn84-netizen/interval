package dev.justinsmith.lift.ui.demo

import dev.justinsmith.interval.ui.ExerciseRep
import kotlin.test.Test
import kotlin.test.assertTrue

/** What each mat Pilates lift's rep must show, beyond what every rep keeps to. */
class MatDemosTest {
    @Test
    fun `a mat lift keeps the hips on the mat`() {
        listOf(
            HundredDemo,
            RollUpDemo,
            RollingLikeABallDemo,
            SingleLegStretchDemo,
            DoubleLegStretchDemo,
            ScissorsDemo,
            CrissCrossDemo,
            SpineStretchForwardDemo,
            SawDemo,
            SwanDemo,
            SingleLegKickDemo,
            SideKickSeriesDemo,
            TeaserDemo,
            SwimmingDemo,
            SealDemo,
        ).forEach { rep ->
            frames(rep).forEach { assertTrue(it.hip.y < 0.16f, "Hips leave mat at ${it.hip}") }
        }
    }

    @Test
    fun `the roll up peels from lying long to folded over the legs`() {
        val lying = RollUpDemo.jointsAt(0f)
        assertTrue(lying.headCenter.y < 0.16f, "Starts with head up at ${lying.headCenter}")
        val folded = RollUpDemo.jointsAt(RollUpDemo.still)
        assertTrue(folded.shoulder.x > folded.hip.x + 0.10f, "Never folds forward over legs")
    }

    @Test
    fun `the teaser balances in a V`() {
        val vee = TeaserDemo.jointsAt(TeaserDemo.still)
        assertTrue(vee.shoulder.y > vee.hip.y + 0.20f, "Shoulders stay low at ${vee.shoulder}")
        assertTrue(vee.ankle.y > vee.hip.y + 0.20f, "Legs stay low at ${vee.ankle}")
    }

    @Test
    fun `rolling like a ball and the seal keep the feet off the mat`() {
        listOf(RollingLikeABallDemo, SealDemo).forEach { rep ->
            frames(rep).forEach { j ->
                listOf(j.toe, j.heel, j.farToe, j.farHeel).forEach { foot ->
                    assertTrue(foot.y > 0.01f, "Foot touches down at $foot")
                }
            }
        }
    }

    @Test
    fun `the swan lifts the chest on hands that stay put`() {
        val hands = frames(SwanDemo).map { it.kettlebellAnchor }
        assertTrue(hands.all { (it - hands.first()).getDistance() < 0.05f }, "Hands slide")
        val down = SwanDemo.jointsAt(0f)
        val up = SwanDemo.jointsAt(SwanDemo.still)
        assertTrue(up.shoulder.y > down.shoulder.y + 0.10f, "Chest only lifts to ${up.shoulder}")
    }

    @Test
    fun `the shoulder bridge lifts a leg to the ceiling with the hips held up`() {
        val up = ShoulderBridgeDemo.jointsAt(ShoulderBridgeDemo.still)
        assertTrue(up.farAnkle.y > up.hip.y + 0.28f, "Leg only rises to ${up.farAnkle}")
        assertTrue(up.hip.y > 0.12f, "Hips sag to ${up.hip}")
    }

    private fun frames(rep: ExerciseRep) = (0..200).map { rep.jointsAt(it / 200f) }
}
