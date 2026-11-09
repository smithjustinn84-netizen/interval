package dev.justinsmith.lift.ui.demo

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What each stretch's figure must show, beyond what every demo keeps to. */
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
        heldStill.forEach { demo ->
            assertEquals(0f, demo.still, "A hold's still is its position")
            val held = demo.figureAt(0f)
            val breath = demo.figureAt(0.5f)
            assertTrue((held.hip - breath.hip).getDistance() < 12f, "The hips wander to ${breath.hip}")
        }
    }

    @Test
    fun `the all-fours stretches keep the hands and the knees where they are`() {
        listOf(PalmsDownFingersBackDemo, BacksOfTheHandsDemo, CatCowDemo).forEach { demo ->
            val start = demo.figureAt(0f)
            frames(demo).forEach { figure ->
                assertTrue((figure.hand - start.hand).getDistance() < 0.5f, "The hands slide to ${figure.hand}")
                assertTrue((figure.knee - start.knee).getDistance() < 5f, "The knees slide to ${figure.knee}")
                assertTrue(figure.knee.y > Rig.FLOOR - 12f, "The knees lift off the mat")
            }
        }
        val neutral = PalmsDownFingersBackDemo.figureAt(0f)
        val rocked = PalmsDownFingersBackDemo.figureAt(PalmsDownFingersBackDemo.still)
        assertTrue(rocked.hip.x < neutral.hip.x - 30f, "The hips never rock back")
        val leaned = BacksOfTheHandsDemo.figureAt(BacksOfTheHandsDemo.still)
        assertTrue(leaned.hip.x > neutral.hip.x + 10f, "The hips never lean in")
    }

    @Test
    fun `the cat tucks the head under where the cow lifts it`() {
        val cow = CatCowDemo.figureAt(0f)
        val cat = CatCowDemo.figureAt(CatCowDemo.still)
        assertTrue(cat.head.y > cow.head.y + 20f, "The head only drops to ${cat.head} from ${cow.head}")
    }

    @Test
    fun `thread the needle and child's pose rest the head on the mat and the cobra lifts it`() {
        listOf(ThreadTheNeedleDemo, ChildsPoseDemo).forEach { demo ->
            val figure = demo.figureAt(0f)
            assertTrue(figure.head.y + Rig.HEAD > Rig.FLOOR - 3f, "The head floats at ${figure.head}")
            assertTrue(figure.knee.y + Rig.LEG / 2 > Rig.FLOOR - 3f, "The knees float at ${figure.knee}")
        }
        val hands = frames(CobraDemo).map { it.hand }
        assertTrue(hands.all { (it - hands.first()).getDistance() < 1f }, "The cobra's hands slide")
        assertTrue(CobraDemo.figureAt(0f).head.y < Rig.FLOOR - 80f, "The cobra's chest stays down")
    }

    @Test
    fun `the downward dog lifts the hips highest on hands and feet that stay down`() {
        frames(DownwardDogDemo).forEach { figure ->
            assertTrue(figure.hip.y < figure.shoulder.y - 50f && figure.hip.y < figure.knee.y - 50f, "The hips sag")
            assertTrue(abs(figure.hand.y + Rig.HAND - Rig.FLOOR) < 0.5f, "The hands lift to ${figure.hand}")
        }
        DownwardDogDemo.keyframes.map { DownwardDogDemo.figureAt(it.at) }.forEach { figure ->
            assertTrue(figure.toe.y > Rig.FLOOR - 1f || figure.farToe.y > Rig.FLOOR - 1f, "Both feet leave the floor")
        }
    }

    @Test
    fun `the kneeling stretches put the back knee on the floor and the front foot down`() {
        listOf(HipFlexorLungeDemo, HalfSplitDemo).forEach { demo ->
            frames(demo).forEach { figure ->
                assertTrue(
                    abs(figure.farKnee.y + Rig.LEG / 2 - Rig.FLOOR) < 1f,
                    "The back knee is at ${figure.farKnee}",
                )
                assertTrue(figure.heel.y > Rig.FLOOR - 1f, "The front heel lifts to ${figure.heel}")
            }
        }
        val split = HalfSplitDemo.figureAt(0f)
        assertTrue(split.toe.y < split.ankle.y - 20f, "The half split's toes point down")
    }

    @Test
    fun `the deep squat sits the hips below the knees and the fold hangs the head below them`() {
        val squat = DeepSquatDemo.figureAt(0f)
        assertTrue(squat.hip.y > squat.knee.y, "The squat stops at ${squat.hip}, above the knees")
        val fold = ForwardFoldDemo.figureAt(0f)
        assertTrue(fold.head.y > fold.hip.y + 100f, "The fold only hangs to ${fold.head}")
    }

    @Test
    fun `the hip switch swings the knees over to the other side`() {
        val right = HipSwitchDemo.figureAt(0f)
        val left = HipSwitchDemo.figureAt(0.5f)
        assertTrue(right.knee.x > right.hip.x && left.knee.x < left.hip.x, "The near knee never changes side")
        assertTrue(right.farKnee.x < right.hip.x && left.farKnee.x > left.hip.x, "The far knee never changes side")
    }

    @Test
    fun `the world's greatest stretch reaches the near arm to the ceiling from the floor`() {
        val demo = WorldsGreatestStretchDemo
        val down = demo.figureAt(0f)
        assertTrue(abs(down.hand.y + Rig.HAND - Rig.FLOOR) < 1f, "The hands start at ${down.hand}, off the floor")
        val reach = demo.figureAt(demo.still)
        assertTrue(reach.hand.y < reach.head.y - Rig.HEAD, "The arm only reaches ${reach.hand}")
        assertTrue(abs(reach.farHand.y + Rig.HAND - Rig.FLOOR) < 1f, "The far hand leaves the floor")
    }

    @Test
    fun `the circles go round and the pulls draw back`() {
        val circle = ShoulderCirclesDemo
        assertEquals(FULL_TURN, circle.keyframes[2].pose.upperArm - circle.keyframes[0].pose.upperArm)
        assertTrue(frames(circle).any { it.hand.y < it.head.y - Rig.HEAD }, "The arm never goes overhead")
        val fists = frames(WristCirclesDemo).map { it.hand }
        assertTrue(fists.maxOf { it.y } - fists.minOf { it.y } in 15f..30f, "The fists hardly move")
        val meeting = FingerPullsDemo.figureAt(0f)
        val pulled = FingerPullsDemo.figureAt(FingerPullsDemo.still)
        assertTrue((meeting.farHand - meeting.hand).getDistance() < 20f, "The hands never meet")
        assertTrue(pulled.farHand.x < meeting.farHand.x - 5f, "The fingers are never drawn back")
    }

    private fun frames(demo: ExerciseDemo) = (0..200).map { demo.figureAt(it / 200f) }
}
