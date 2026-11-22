package dev.justinsmith.lift.ui.demo

import dev.justinsmith.interval.ui.ExerciseRep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What each floor drill's rep must show, beyond what every rep keeps to. */
class FloorDemosTest {
    @Test
    fun `the spinal rock rocks back to the shoulders with the head off the mat and sits up again`() {
        val sitting = SpinalRockDemo.jointsAt(0f)
        val back = SpinalRockDemo.jointsAt(SpinalRockDemo.still)
        assertTrue(back.shoulder.y < sitting.shoulder.y - 0.16f, "Shoulders stay up at ${back.shoulder}")
        assertTrue(back.headCenter.y > 0.08f, "Head hits mat at ${back.headCenter}")
        assertTrue(sitting.headCenter.y > sitting.hip.y + 0.25f, "Never sits up")
    }

    @Test
    fun `the shoulder roll goes over upside down and comes all the way up to standing`() {
        val over = frames(ShoulderRollDemo).any { it.hip.y > it.headCenter.y + 0.12f }
        assertTrue(over, "Roll never turns hips over head")
        val stood = ShoulderRollDemo.jointsAt(0.82f)
        assertTrue(stood.headCenter.y > stood.hip.y + 0.28f && stood.hip.y > stood.knee.y + 0.18f, "Never stands up")
    }

    @Test
    fun `the sit-up and the V-up come up off the mat from lying`() {
        listOf(SitUpDemo, CrossVUpDemo).forEach { rep ->
            val lying = rep.jointsAt(0f)
            assertTrue(lying.shoulder.y < lying.hip.y + 0.08f, "Starts sitting at ${lying.shoulder}")
            assertTrue(frames(rep).any { it.shoulder.y > lying.shoulder.y + 0.18f }, "Chest never comes up")
        }
        val near = CrossVUpDemo.jointsAt(0.28f)
        val far = CrossVUpDemo.jointsAt(0.78f)
        assertTrue(near.ankle.y > near.farAnkle.y + 0.20f, "Near leg stays down")
        assertTrue(far.farAnkle.y > far.ankle.y + 0.20f, "Far leg stays down")
    }

    @Test
    fun `the hip escape straightens the legs away and the bridge lifts the hips`() {
        val bent = HipEscapeDemo.jointsAt(0f)
        val escaped = HipEscapeDemo.jointsAt(HipEscapeDemo.still)
        assertEquals(bent.ankle, escaped.ankle, "Planted foot slides")
        val away = (escaped.hips - escaped.ankle).getDistance() - (bent.hips - bent.ankle).getDistance()
        assertTrue(away > 0.08f, "Hips only push $away away")
        assertTrue(escaped.hips.y > bent.hips.y + 0.08f, "Hips never go out to the side")
        val lying = BridgeAndRollDemo.jointsAt(0f)
        val bridged = BridgeAndRollDemo.jointsAt(BridgeAndRollDemo.still)
        assertTrue(bridged.hip.y > lying.hip.y + 0.08f, "Hips stay down")
        val turned = BridgeAndRollDemo.jointsAt(0.5f)
        assertTrue(turned.kettlebellAnchor.y > turned.shoulder.y + 0.14f, "Arm never reaches over")
    }

    @Test
    fun `the technical stand-up rises from sitting to a stance on a planted foot`() {
        val sitting = TechnicalStandUpDemo.jointsAt(0f)
        val stance = TechnicalStandUpDemo.jointsAt(TechnicalStandUpDemo.still)
        assertTrue(sitting.hip.y < 0.16f, "Starts off mat at ${sitting.hip}")
        assertTrue(stance.headCenter.y > stance.hip.y + 0.28f && stance.hip.y > stance.knee.y + 0.15f, "Never stands")
        frames(TechnicalStandUpDemo).forEach { assertEquals(sitting.ankle, it.ankle, "Near foot moves") }
    }

    @Test
    fun `the sit-out threads the far leg through in front on a hand that stays put`() {
        val kneeling = SitOutDemo.jointsAt(0f)
        val through = SitOutDemo.jointsAt(SitOutDemo.still)
        assertTrue(through.farAnkle.x > kneeling.kettlebellAnchor.x, "Far leg never comes through")
        assertTrue(through.hip.y < kneeling.hip.y - 0.03f, "Hips never drop")
        frames(SitOutDemo).forEach {
            assertTrue((it.kettlebellAnchor - kneeling.kettlebellAnchor).getDistance() < 0.005f, "Hand slides")
        }
    }

    @Test
    fun `the butterfly and the frog hold their place and breathe`() {
        listOf(ButterflyDemo, FrogStretchDemo).forEach { rep ->
            val held = rep.jointsAt(0f)
            frames(rep).forEach { assertTrue((it.hip - held.hip).getDistance() < 0.04f, "Hips wander") }
        }
        val frog = FrogStretchDemo.jointsAt(0f)
        assertTrue((frog.knee - frog.farKnee).getDistance() > 0.35f, "Frog knees stay narrow")
        assertTrue(frog.kettlebellAnchor.x < frog.shoulder.x - 0.08f, "Frog hands stay back")
        val butterfly = ButterflyDemo.jointsAt(0f)
        assertTrue(butterfly.knee.x - butterfly.farKnee.x > 0.35f, "Butterfly knees stay narrow")
        assertTrue((butterfly.ankle - butterfly.farAnkle).getDistance() < 0.15f, "Feet stay apart")
    }

    private fun frames(rep: ExerciseRep) = (0..40).map { rep.jointsAt(it / 40f) }
}
