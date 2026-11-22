package dev.justinsmith.lift.ui.demo

import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.celebration
import dev.justinsmith.lift.domain.BuiltInExercise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What each stretch's rep must show, beyond what every rep keeps to. */
class StretchDemosTest {
    private val heldStill = listOf(
        HipFlexorLungeDemo,
        DeepSquatDemo,
        ThreadTheNeedleDemo,
        ForearmStretchDemo,
        ChildsPoseDemo,
        CobraDemo,
        HalfSplitDemo,
        ForwardFoldDemo,
        PigeonDemo,
        LyingTwistDemo,
    )

    @Test
    fun `a held stretch is shown in its position and breathes in place`() {
        heldStill.forEach { rep ->
            assertEquals(0f, rep.still, "A hold's still is its position")
            val held = rep.jointsAt(0f)
            val breath = rep.jointsAt(0.5f)
            assertTrue((held.hip - breath.hip).getDistance() < 0.05f, "Hips wander to ${breath.hip}")
        }
    }

    @Test
    fun `the all-fours stretches keep the hands put and rock or bow`() {
        listOf(PalmsDownFingersBackDemo, BacksOfTheHandsDemo, CatCowDemo).forEach { rep ->
            val start = rep.jointsAt(0f)
            frames(rep).forEach { j ->
                assertTrue((j.kettlebellAnchor - start.kettlebellAnchor).getDistance() < 0.005f, "Hands slide")
                assertTrue((j.fingers - start.fingers).getDistance() < 0.005f, "Fingers lift")
                assertTrue((j.knee - start.knee).getDistance() < 0.04f, "Knees slide")
            }
        }
        val neutral = PalmsDownFingersBackDemo.jointsAt(0f)
        val rocked = PalmsDownFingersBackDemo.jointsAt(PalmsDownFingersBackDemo.still)
        assertTrue(rocked.hip.x < neutral.hip.x - 0.08f, "Hips never rock back")
        val leaned = BacksOfTheHandsDemo.jointsAt(BacksOfTheHandsDemo.still)
        assertTrue(leaned.hip.x > neutral.hip.x + 0.03f, "Hips never lean in")
    }

    @Test
    fun `the cat tucks the head under and rounds the back where the cow lifts it`() {
        val cow = CatCowDemo.jointsAt(0f)
        val cat = CatCowDemo.jointsAt(CatCowDemo.still)
        assertTrue(cat.headCenter.y < cow.headCenter.y - 0.05f, "Head only drops to ${cat.headCenter}")
        assertTrue(cow.spineBow < -5f, "Cow spine doesn't arch: ${cow.spineBow}")
        assertTrue(cat.spineBow > 10f, "Cat spine doesn't round: ${cat.spineBow}")
    }

    @Test
    fun `child's pose rests low on the mat and cobra lifts the head`() {
        val figure = ChildsPoseDemo.jointsAt(0f)
        assertTrue(figure.headCenter.y < 0.14f, "Head floats at ${figure.headCenter}")
        val hands = frames(CobraDemo).map { it.kettlebellAnchor }
        assertTrue(hands.all { (it - hands.first()).getDistance() < 0.005f }, "Cobra hands slide")
        assertTrue(CobraDemo.jointsAt(0f).headCenter.y > 0.25f, "Cobra chest stays down")
    }

    @Test
    fun `the downward dog lifts the hips highest on hands that stay down`() {
        frames(DownwardDogDemo).forEach { j ->
            assertTrue(j.hip.y > j.shoulder.y + 0.12f && j.hip.y > j.knee.y + 0.12f, "Hips sag")
            assertTrue(j.kettlebellAnchor.y < 0.06f, "Hands lift to ${j.kettlebellAnchor}")
        }
    }

    @Test
    fun `the deep squat sits the hips below the knees and the fold hangs the head low`() {
        val squat = DeepSquatDemo.jointsAt(0f)
        assertTrue(squat.hip.y < squat.knee.y, "Squat stops above knees")
        val fold = ForwardFoldDemo.jointsAt(0f)
        assertTrue(fold.headCenter.y < fold.hip.y - 0.25f, "Fold doesn't hang low")
    }

    @Test
    fun `the hip switch swings the knees over to the other side`() {
        val right = HipSwitchDemo.jointsAt(0f)
        val left = HipSwitchDemo.jointsAt(0.5f)
        assertTrue(right.knee.x > right.hip.x && left.knee.x < left.farHip.x, "Near knee never changes side")
        assertTrue(right.farKnee.x > right.hip.x && left.farKnee.x < left.farHip.x, "Far knee never changes side")
        val over = HipSwitchDemo.jointsAt(0.3f)
        assertTrue(over.knee.y > over.hip.y + 0.08f && over.farKnee.y > over.hip.y + 0.08f, "Knees never lift over")
    }

    @Test
    fun `seen from above thread the needle reaches under the chest and the twist drops a knee across`() {
        val threaded = ThreadTheNeedleDemo.jointsAt(0f)
        assertTrue(threaded.armsBehind, "Arm goes over back")
        assertTrue(threaded.kettlebellAnchor.y < threaded.farShoulder.y - 0.06f, "Hand stops under chest")
        val twisted = LyingTwistDemo.jointsAt(0f)
        assertTrue(twisted.knee.y < twisted.farHip.y - 0.08f, "Knee stays on own side")
    }

    @Test
    fun `the world's greatest stretch reaches the near arm up from the floor`() {
        val down = WorldsGreatestStretchDemo.jointsAt(0f)
        assertTrue(down.kettlebellAnchor.y < 0.08f, "Hands start off floor at ${down.kettlebellAnchor}")
        val reach = WorldsGreatestStretchDemo.jointsAt(WorldsGreatestStretchDemo.still)
        assertTrue(reach.kettlebellAnchor.y > reach.headCenter.y + 0.06f, "Arm only reaches ${reach.kettlebellAnchor}")
    }

    @Test
    fun `the circles go round and the wrist pulls draw back`() {
        assertTrue(
            frames(ShoulderCirclesDemo).any {
                it.kettlebellAnchor.y > it.headCenter.y + 0.06f
            },
            "Never overhead",
        )
        val circling = frames(WristCirclesDemo)
        assertTrue(circling.all { (it.kettlebellAnchor - circling.first().kettlebellAnchor).getDistance() < 0.005f })
        val fingerY = circling.map { it.fingers.y }
        assertTrue(fingerY.maxOf { it } - fingerY.minOf { it } > 0.04f, "Fists hardly tip")
        val meeting = FingerPullsDemo.jointsAt(0f)
        val pulled = FingerPullsDemo.jointsAt(FingerPullsDemo.still)
        assertTrue(meeting.fingers.y > meeting.wrist.y, "Fingers don't point up")
        assertTrue(pulled.fingers.x < meeting.fingers.x - 0.015f, "Fingers never draw back")
        val stretched = ForearmStretchDemo.jointsAt(0f)
        assertTrue(stretched.fingers.y < stretched.wrist.y - 0.04f, "Fingers don't point down")
    }

    @Test
    fun `the wrist drills are cropped close and give no celebration`() {
        listOf(
            BuiltInExercise.WristCircles,
            BuiltInExercise.FingerPulls,
            BuiltInExercise.ForearmStretch,
            BuiltInExercise.PalmsDownFingersBack,
            BuiltInExercise.BacksOfTheHands,
        ).forEach { exercise ->
            val rep = assertNotNull(exercise.rep)
            assertNotNull(rep.crop, "$exercise is not seen close")
            assertNull(rep.celebration)
        }
    }

    private fun frames(rep: ExerciseRep) = (0..200).map { rep.jointsAt(it / 200f) }
}
