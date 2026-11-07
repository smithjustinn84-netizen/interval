package dev.justinsmith.lift.ui.demo

import kotlin.test.Test
import kotlin.test.assertTrue

/** What each mat Pilates lift's figure must show, beyond what every demo keeps to. */
class MatDemosTest {
    @Test
    fun `a mat lift keeps the hips on the mat`() {
        listOf(
            HundredDemo,
            RollUpDemo,
            SingleLegCirclesDemo,
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
        ).forEach { demo ->
            frames(demo).forEach { assertTrue(it.hip.y > Rig.FLOOR - 40f, "The hips leave the mat at ${it.hip}") }
        }
    }

    @Test
    fun `the roll up peels from lying long to folded over the legs`() {
        val lying = RollUpDemo.figureAt(0f)
        assertTrue(lying.head.y > Rig.FLOOR - 40f, "It starts with the head up at ${lying.head}")
        val folded = RollUpDemo.figureAt(RollUpDemo.still)
        assertTrue(folded.shoulder.x > folded.hip.x + 40f, "It never folds forward over the legs")
    }

    @Test
    fun `the teaser balances in a V`() {
        val vee = TeaserDemo.figureAt(TeaserDemo.still)
        assertTrue(vee.shoulder.y < vee.hip.y - 80f, "The shoulders stay low at ${vee.shoulder}")
        assertTrue(vee.ankle.y < vee.hip.y - 80f, "The legs stay low at ${vee.ankle}")
    }

    @Test
    fun `rolling like a ball and the seal keep the feet off the mat`() {
        listOf(RollingLikeABallDemo, SealDemo).forEach { demo ->
            frames(demo).forEach { figure ->
                listOf(figure.toe, figure.heel, figure.farToe, figure.farHeel).forEach { foot ->
                    assertTrue(foot.y < Rig.FLOOR - 5f, "A foot touches down at $foot")
                }
            }
        }
    }

    @Test
    fun `the swan lifts the chest on hands that stay put`() {
        val hands = frames(SwanDemo).map { it.hand }
        assertTrue(hands.all { (it - hands.first()).getDistance() < 8f }, "The hands slide")
        val down = SwanDemo.figureAt(0f)
        val up = SwanDemo.figureAt(SwanDemo.still)
        assertTrue(up.shoulder.y < down.shoulder.y - 40f, "The chest only lifts to ${up.shoulder}")
    }

    @Test
    fun `the shoulder bridge lifts a leg to the ceiling with the hips held up`() {
        val up = ShoulderBridgeDemo.figureAt(ShoulderBridgeDemo.still)
        assertTrue(up.farAnkle.y < up.hip.y - 100f, "The leg only rises to ${up.farAnkle}")
        assertTrue(up.hip.y < Rig.FLOOR - 40f, "The hips sag to ${up.hip}")
    }

    private fun frames(demo: ExerciseDemo) = (0..200).map { demo.figureAt(it / 200f) }
}
