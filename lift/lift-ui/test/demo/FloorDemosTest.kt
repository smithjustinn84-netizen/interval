package dev.justinsmith.lift.ui.demo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What each floor drill's figure must show, beyond what every demo keeps to. */
class FloorDemosTest {
    @Test
    fun `the spinal rock rocks back to the shoulders with the head off the mat and sits up again`() {
        val sitting = SpinalRockDemo.figureAt(0f)
        val back = SpinalRockDemo.figureAt(SpinalRockDemo.still)
        assertTrue(back.shoulder.y > sitting.shoulder.y + 60f, "The shoulders stay up at ${back.shoulder}")
        assertTrue(back.head.y + Rig.HEAD < Rig.FLOOR - 5f, "The head hits the mat at ${back.head}")
        assertTrue(sitting.head.y < sitting.hip.y - 100f, "It never sits up")
    }

    @Test
    fun `the shoulder roll goes over upside down and comes all the way up to standing`() {
        val over = frames(ShoulderRollDemo).any { it.hip.y < it.head.y - 40f }
        assertTrue(over, "The roll never turns the hips over the head")
        val stood = ShoulderRollDemo.figureAt(0.82f)
        assertTrue(stood.head.y < stood.hip.y - 100f && stood.hip.y < stood.knee.y - 60f, "It never stands up")
    }

    @Test
    fun `the sit-up and the V-up come up off the mat from lying`() {
        listOf(SitUpDemo, CrossVUpDemo).forEach { demo ->
            val lying = demo.figureAt(0f)
            assertTrue(lying.shoulder.y > lying.hip.y - 20f, "It starts sitting at ${lying.shoulder}")
            assertTrue(frames(demo).any { it.shoulder.y < lying.shoulder.y - 70f }, "The chest never comes up")
        }
        // Each side of the V lifts its own leg: the near one, then the far one.
        val near = CrossVUpDemo.figureAt(0.28f)
        val far = CrossVUpDemo.figureAt(0.78f)
        assertTrue(near.ankle.y < near.farAnkle.y - 80f, "The near leg stays down")
        assertTrue(far.farAnkle.y < far.ankle.y - 80f, "The far leg stays down")
    }

    @Test
    fun `the hip escape straightens the legs away and the bridge lifts the hips`() {
        val bent = HipEscapeDemo.figureAt(0f)
        val escaped = HipEscapeDemo.figureAt(HipEscapeDemo.still)
        // Seen from above with the near foot planted: the hips go away from it, and out to the side.
        assertEquals(bent.ankle, escaped.ankle, "The planted foot slides")
        val away = (escaped.hips - escaped.ankle).getDistance() - (bent.hips - bent.ankle).getDistance()
        assertTrue(away > 25f, "The hips only push $away away")
        assertTrue(escaped.hips.y < bent.hips.y - 30f, "The hips never go out to the side")
        val lying = BridgeAndRollDemo.figureAt(0f)
        val bridged = BridgeAndRollDemo.figureAt(BridgeAndRollDemo.still)
        assertTrue(bridged.hip.y < lying.hip.y - 30f, "The hips stay down")
        val turned = BridgeAndRollDemo.figureAt(0.5f)
        assertTrue(turned.hand.y < turned.shoulder.y - 50f, "The arm never reaches over")
    }

    @Test
    fun `the technical stand-up rises from sitting to a stance on a planted foot`() {
        val sitting = TechnicalStandUpDemo.figureAt(0f)
        val stance = TechnicalStandUpDemo.figureAt(TechnicalStandUpDemo.still)
        assertTrue(sitting.hip.y > Rig.FLOOR - Rig.TRUNK, "It starts off the mat")
        assertTrue(stance.head.y < stance.hip.y - 100f && stance.hip.y < stance.knee.y - 50f, "It never stands")
        frames(TechnicalStandUpDemo).forEach { assertTrue(it.ankle == sitting.ankle, "The near foot moves") }
    }

    @Test
    fun `the sit-out threads the far leg through in front on a hand that stays put`() {
        val kneeling = SitOutDemo.figureAt(0f)
        val through = SitOutDemo.figureAt(SitOutDemo.still)
        assertTrue(through.farAnkle.x > kneeling.hand.x, "The far leg never comes through")
        assertTrue(through.hip.y > kneeling.hip.y + 10f, "The hips never drop")
        frames(SitOutDemo).forEach {
            assertTrue((it.hand - kneeling.hand).getDistance() < 0.5f, "The hand slides to ${it.hand}")
        }
    }

    @Test
    fun `the butterfly and the frog hold their place and breathe`() {
        listOf(ButterflyDemo, FrogStretchDemo).forEach { demo ->
            val held = demo.figureAt(0f)
            frames(demo).forEach { assertTrue((it.hip - held.hip).getDistance() < 12f, "The hips wander to ${it.hip}") }
        }
        // The frog is seen from above: its knees wide either side of the hips, its hands ahead of the shoulders.
        val frog = FrogStretchDemo.figureAt(0f)
        assertTrue((frog.knee - frog.farKnee).getDistance() > 120f, "The frog's knees are only ${frog.knee} apart")
        assertTrue(frog.hand.x < frog.shoulder.x - 30f, "The frog's hands are back at ${frog.hand}")
        val butterfly = ButterflyDemo.figureAt(0f)
        assertTrue(butterfly.knee.x - butterfly.farKnee.x > 120f, "The butterfly's knees are in at ${butterfly.knee}")
        assertTrue((butterfly.ankle - butterfly.farAnkle).getDistance() < 40f, "The feet are apart")
    }

    private fun frames(demo: ExerciseDemo) = (0..40).map { demo.figureAt(it / 40f) }
}
