package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExerciseDemoCanvasTest {
    private val reps = mapOf("swing" to ExerciseRep.Swing, "clean and press" to ExerciseRep.CleanAndPress)

    private fun moments(rep: ExerciseRep): List<BodyJoints> = (0..200).map { rep.poseAt(it / 200f).joints() }

    private fun assertLength(expected: Float, from: Offset, to: Offset, what: String) =
        assertEquals(expected, (to - from).getDistance(), absoluteTolerance = 0.0001f, what)

    @Test
    fun `standing tall stacks every joint over the ankle at its height`() {
        val joints = BodyPose().joints()
        listOf(joints.knee, joints.hip, joints.shoulder, joints.elbow, joints.wrist, joints.headCenter).forEach {
            assertEquals(0f, it.x, absoluteTolerance = 0.0001f)
        }
        assertEquals(0.525f, joints.hip.y, absoluteTolerance = 0.0001f)
        assertEquals(1f, joints.headCenter.y + BodyProportions.HEAD, absoluteTolerance = 0.0001f)
        assertTrue(joints.wrist.y < joints.hip.y, "the hand hangs below the hip")
    }

    @Test
    fun `no limb stretches anywhere in a rep`() {
        reps.forEach { (name, rep) ->
            moments(rep).forEach { joints ->
                assertLength(BodyProportions.SHIN, joints.ankle, joints.knee, "$name shin")
                assertLength(BodyProportions.THIGH, joints.knee, joints.hip, "$name thigh")
                assertLength(BodyProportions.TORSO, joints.hip, joints.shoulder, "$name torso")
                assertLength(BodyProportions.UPPER_ARM, joints.shoulder, joints.elbow, "$name upper arm")
                assertLength(BodyProportions.FOREARM, joints.elbow, joints.wrist, "$name forearm")
                assertLength(BodyProportions.HAND, joints.wrist, joints.kettlebellAnchor, "$name hand")
                assertLength(
                    BodyProportions.BELL_HANDLE + BodyProportions.BELL_RADIUS,
                    joints.kettlebellAnchor,
                    joints.kettlebellCenter,
                    "$name bell",
                )
            }
        }
    }

    @Test
    fun `the ankle never leaves its place and nothing goes through the floor`() {
        reps.values.flatMap(::moments).forEach { joints ->
            assertEquals(Offset(0f, BodyProportions.ANKLE), joints.ankle)
            assertTrue(joints.kettlebellCenter.y - BodyProportions.BELL_RADIUS > 0f)
            assertTrue(joints.knee.y > joints.ankle.y && joints.hip.y > joints.knee.y)
        }
    }

    @Test
    fun `the bottom of the hinge sends the hips back and the bell behind them`() {
        val joints = KettlebellPoses.Hinge.joints()
        assertTrue(joints.hip.x < -0.05f, "hips back")
        assertTrue(joints.hip.y < BodyPose().joints().hip.y, "hips below where they stand")
        assertTrue(joints.shoulder.x > 0.1f, "shoulders ahead of the feet")
        assertTrue(joints.kettlebellCenter.x < joints.hip.x, "the bell behind the hips")
        assertEquals(0f, joints.knee.x, absoluteTolerance = 0.03f, "shins upright")
    }

    @Test
    fun `the lockout stacks the grip over the shoulder the hip and the ankle`() {
        val joints = KettlebellPoses.Lockout.joints()
        listOf(joints.hip, joints.shoulder, joints.elbow, joints.wrist, joints.kettlebellAnchor).forEach {
            assertEquals(0f, it.x, absoluteTolerance = 0.0001f)
        }
        assertTrue(joints.kettlebellAnchor.y > joints.headCenter.y + BodyProportions.HEAD, "the grip over the head")
        assertTrue(joints.kettlebellCenter.y < joints.kettlebellAnchor.y, "the bell lies below the hand")
    }

    @Test
    fun `the rack holds the fist by the shoulder with the bell under it`() {
        val joints = KettlebellPoses.Rack.joints()
        assertTrue((joints.wrist - joints.shoulder).getDistance() < 0.12f)
        assertTrue(joints.kettlebellCenter.y < joints.kettlebellAnchor.y)
    }

    @Test
    fun `a rep is at each stance at its share and comes round to the first`() {
        reps.values.forEach { rep ->
            rep.keys.filterNot { it.passing }.forEach { assertEquals(it.pose, rep.poseAt(it.at)) }
            assertEquals(rep.keys.first().pose, rep.poseAt(1f))
            assertEquals(rep.poseAt(0.25f), rep.poseAt(1.25f))
            assertEquals(rep.poseAt(0.75f), rep.poseAt(-0.25f))
        }
    }

    @Test
    fun `the hips are most of the way through before the arm has left the body`() {
        val early = ExerciseRep.Swing.poseAt(0.12f)
        val hips = 1 - early.hipAngleDeg / KettlebellPoses.Hinge.hipAngleDeg
        val arm = (early.shoulderAngleDeg - KettlebellPoses.Hinge.shoulderAngleDeg) /
            (KettlebellPoses.Floating.shoulderAngleDeg - KettlebellPoses.Hinge.shoulderAngleDeg)
        assertTrue(hips > 0.5f && arm < 0.2f, "hips $hips of the way and the arm $arm")
    }

    @Test
    fun `the arm falls back to the body before the hips fold`() {
        val float = ExerciseRep.Swing.keys.first { it.pose == KettlebellPoses.Floating }
        val falling = ExerciseRep.Swing.poseAt(float.at + 0.25f)
        assertTrue(falling.shoulderAngleDeg < float.pose.shoulderAngleDeg / 2, "the arm is most of the way down")
        assertTrue(falling.hipAngleDeg < KettlebellPoses.Hinge.hipAngleDeg / 4, "the hips have hardly folded")
    }

    @Test
    fun `a clean keeps the bell close where a curl would swing it out`() {
        val rack = ExerciseRep.CleanAndPress.keys.first { it.pose == KettlebellPoses.Rack }.at
        val furthest = (0..100).maxOf { ExerciseRep.CleanAndPress.poseAt(rack * it / 100).joints().kettlebellCenter.x }
        assertTrue(furthest < 0.16f, "the bell gets $furthest ahead of the ankle")
    }

    @Test
    fun `no joint turns past what the stances of its rep say`() {
        reps.forEach { (name, rep) ->
            val keyed = rep.keys.map { it.pose.angles() }
            (0..400).forEach { step ->
                rep.poseAt(step / 400f).angles().forEachIndexed { joint, angle ->
                    val least = keyed.minOf { it[joint] }
                    val most = keyed.maxOf { it[joint] }
                    assertTrue(angle in least - 0.01f..most + 0.01f, "$name joint $joint is at $angle at $step")
                }
            }
        }
    }

    @Test
    fun `the bell comes back down the path it went up`() {
        val rep = ExerciseRep.Swing
        val float = rep.still
        val up = (0..400).map { rep.poseAt(float * it / 400f).joints().kettlebellCenter }
        (1 until 40).forEach { step ->
            val down = rep.poseAt(float + (1f - float) * step / 40f).joints().kettlebellCenter
            assertTrue(up.minOf { (it - down).getDistance() } < 0.01f, "off the path at $step")
        }
    }

    @Test
    fun `a pose blends by its angles`() {
        val blended = BodyPose().blend(KettlebellPoses.Hinge, share = 0.5f)
        assertEquals(41f, blended.hipAngleDeg)
        assertEquals(30f, blended.torsoAngleDeg)
        assertEquals(5f, blended.shoulderAngleDeg)
        assertLength(BodyProportions.THIGH, blended.joints().knee, blended.joints().hip, "thigh")
    }

    @Test
    fun `a rep needs its stances in order from a rest at the start`() {
        val key = PoseKey(at = 0f, pose = BodyPose())
        assertFailsWith<IllegalArgumentException> { ExerciseRep(listOf(key), seconds = 1f) }
        assertFailsWith<IllegalArgumentException> {
            ExerciseRep(listOf(key.copy(at = 0.5f), key.copy(at = 0.8f)), seconds = 1f)
        }
        assertFailsWith<IllegalArgumentException> {
            ExerciseRep(listOf(key, key.copy(at = 0.6f), key.copy(at = 0.4f)), seconds = 1f)
        }
        assertFailsWith<IllegalArgumentException> {
            ExerciseRep(listOf(key.copy(passing = true), key.copy(at = 0.5f)), seconds = 1f)
        }
    }

    @Test
    fun `degrees and radians turn into each other`() {
        assertEquals(kotlin.math.PI.toFloat(), Angles.radians(Angles.HALF_TURN), absoluteTolerance = 0.0001f)
        assertEquals(Angles.QUARTER_TURN, Angles.degrees(kotlin.math.PI.toFloat() / 2), absoluteTolerance = 0.001f)
    }
}
