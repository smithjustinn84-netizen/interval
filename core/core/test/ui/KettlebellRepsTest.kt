package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.Offset
import dev.justinsmith.interval.ui.KettlebellPoses.halfKneeling
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KettlebellRepsTest {
    private val reps = mapOf(
        "swing" to KettlebellReps.Swing,
        "clean" to KettlebellReps.Clean,
        "snatch" to KettlebellReps.Snatch,
        "press" to KettlebellReps.Press,
        "half-kneeling press" to KettlebellReps.HalfKneelingPress,
        "goblet squat" to KettlebellReps.GobletSquat,
        "get-up" to KettlebellReps.GetUp,
        "clean and press" to KettlebellReps.CleanAndPress,
    )

    private fun moments(rep: ExerciseRep, from: Float = 0f, until: Float = 1f): List<BodyJoints> =
        (0..400).map { rep.poseAt(from + (until - from) * it / 400f).joints() }

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
    fun `the far side stands behind the near one until a stance says otherwise`() {
        val pose = KettlebellPoses.Hinge
        val joints = pose.joints()
        assertFalse(pose.showsFarSide)
        listOf(
            joints.farKnee to joints.knee,
            joints.farAnkle to joints.ankle,
            joints.farElbow to joints.elbow,
            joints.farWrist to joints.wrist,
            joints.farHeel to Offset(-BodyProportions.HEEL, BodyProportions.SOLE),
            joints.farToe to Offset(BodyProportions.TOE, BodyProportions.SOLE),
        ).forEach { (far, near) -> assertTrue((far - near).getDistance() < 0.0001f, "$far is not at $near") }
        assertTrue(pose.halfKneeling().showsFarSide)
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
                assertLength(BodyProportions.THIGH, joints.hip, joints.farKnee, "$name far thigh")
                assertLength(BodyProportions.SHIN, joints.farKnee, joints.farAnkle, "$name far shin")
                assertLength(BodyProportions.UPPER_ARM, joints.shoulder, joints.farElbow, "$name far upper arm")
                assertLength(BodyProportions.FOREARM, joints.farElbow, joints.farWrist, "$name far forearm")
            }
        }
    }

    @Test
    fun `the planted ankle never leaves its place and nothing goes through the floor`() {
        reps.forEach { (name, rep) ->
            moments(rep).forEachIndexed { step, joints ->
                assertEquals(Offset(0f, BodyProportions.ANKLE), joints.ankle)
                assertTrue(joints.kettlebellCenter.y - BodyProportions.BELL_RADIUS > 0f, "$name bell at $step")
                val lowest = listOf(
                    joints.knee,
                    joints.hip,
                    joints.shoulder,
                    joints.headCenter,
                    joints.elbow,
                    joints.wrist,
                    joints.farKnee,
                    joints.farAnkle,
                    joints.farToe,
                    joints.farElbow,
                    joints.farWrist,
                ).minOf { it.y }
                assertTrue(lowest > -0.005f, "$name is $lowest under the floor at $step")
            }
        }
    }

    @Test
    fun `every rep comes round to where it started without a seam`() {
        reps.forEach { (name, rep) ->
            assertEquals(rep.keyframes.first().pose, rep.keyframes.last().pose, name)
            assertEquals(rep.keyframes.first().pose, rep.poseAt(1f), name)
            rep.keyframes.forEachIndexed { index, keyframe ->
                if (!keyframe.passing && index < rep.keyframes.lastIndex) {
                    assertEquals(keyframe.pose, rep.poseAt(rep.timeOf(index)), "$name at keyframe $index")
                }
            }
            assertEquals(rep.poseAt(0.25f), rep.poseAt(1.25f), name)
            assertEquals(rep.poseAt(0.75f), rep.poseAt(-0.25f), name)
            val nearEnd = rep.poseAt(0.9995f).angles()
            rep.poseAt(0f).angles().forEachIndexed { joint, angle ->
                assertEquals(angle, nearEnd[joint], absoluteTolerance = 1.5f, "$name joint $joint jumps at the seam")
            }
        }
    }

    @Test
    fun `a keyframe's weight is its share of the rep's time`() {
        val rep = KettlebellReps.Swing
        assertEquals(0f, rep.timeOf(0))
        assertEquals(0.17f, rep.timeOf(1), absoluteTolerance = 0.0001f)
        assertEquals(0.42f, rep.timeOf(2), absoluteTolerance = 0.0001f)
        assertEquals(1f, rep.timeOf(rep.keyframes.lastIndex))
        assertEquals(rep.timeOf(2), rep.still)
        assertTrue(rep.timeOf(2) - rep.timeOf(0) < rep.timeOf(4) - rep.timeOf(2), "up is quicker than down")
    }

    @Test
    fun `no joint turns past what the stances of its rep say`() {
        reps.forEach { (name, rep) ->
            val keyed = rep.keyframes.map { it.pose.angles() }
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
    fun `the hike pass sends the hips back under a trunk at half a right angle with the shins upright`() {
        val joints = KettlebellPoses.Hinge.joints()
        assertTrue(joints.hip.x < -0.05f, "hips back")
        assertTrue(joints.hip.y < BodyPose().joints().hip.y, "hips below where they stand")
        assertTrue(joints.shoulder.x > 0.1f, "shoulders ahead of the feet")
        assertEquals(45f, KettlebellPoses.Hinge.torsoAngleDeg, absoluteTolerance = 3f)
        assertEquals(0f, joints.knee.x, absoluteTolerance = 0.03f, "shins upright")
        assertTrue(joints.kettlebellCenter.x < joints.knee.x - 0.15f, "the bell behind the knees")
        assertTrue(joints.kettlebellCenter.x < joints.hip.x, "the bell behind the hips")
    }

    @Test
    fun `a swing's hips go back where a squat's go down`() {
        fun travel(rep: ExerciseRep) = moments(rep).map { it.hip }.let { hips ->
            Offset(hips.maxOf { it.x } - hips.minOf { it.x }, hips.maxOf { it.y } - hips.minOf { it.y })
        }
        val swing = travel(KettlebellReps.Swing)
        val squat = travel(KettlebellReps.GobletSquat)
        assertTrue(swing.x > swing.y * 3, "a hinge: the hips go $swing")
        assertTrue(squat.y > squat.x * 2, "a squat: the hips go $squat")
        val knees = moments(KettlebellReps.Swing).maxOf { it.knee.x }
        assertTrue(knees < 0.03f, "a swing's knees stay over the ankles, not $knees ahead")
        assertTrue(moments(KettlebellReps.GobletSquat).maxOf { it.knee.x } > 0.1f, "a squat's knees go forward")
    }

    @Test
    fun `the float is a standing plank with the bell at chest height`() {
        val pose = KettlebellPoses.Floating
        val joints = pose.joints()
        assertEquals(0f, pose.hipAngleDeg)
        assertEquals(0f, pose.torsoAngleDeg)
        assertTrue(joints.kettlebellCenter.y in joints.shoulder.y - 0.12f..joints.shoulder.y + 0.05f)
    }

    @Test
    fun `the hips are most of the way through before the arm has left the body`() {
        val early = KettlebellReps.Swing.poseAt(0.12f)
        val hips = 1 - early.hipAngleDeg / KettlebellPoses.Hinge.hipAngleDeg
        val arm = (early.shoulderAngleDeg - KettlebellPoses.Hinge.shoulderAngleDeg) /
            (KettlebellPoses.Floating.shoulderAngleDeg - KettlebellPoses.Hinge.shoulderAngleDeg)
        assertTrue(hips > 0.5f && arm < 0.25f, "hips $hips of the way and the arm $arm")
    }

    @Test
    fun `the arm falls back to the body before the hips fold`() {
        val float = KettlebellReps.Swing.still
        val falling = KettlebellReps.Swing.poseAt(float + 0.25f)
        assertTrue(falling.shoulderAngleDeg < KettlebellPoses.Floating.shoulderAngleDeg / 2, "the arm is down")
        assertTrue(falling.hipAngleDeg < KettlebellPoses.Hinge.hipAngleDeg / 4, "the hips have hardly folded")
    }

    @Test
    fun `the bell comes back down the path it went up`() {
        val rep = KettlebellReps.Swing
        val float = rep.still
        val up = moments(rep, until = float).map { it.kettlebellCenter }
        (1 until 40).forEach { step ->
            val down = rep.poseAt(float + (1f - float) * step / 40f).joints().kettlebellCenter
            assertTrue(up.minOf { (it - down).getDistance() } < 0.01f, "off the path at $step")
        }
    }

    @Test
    fun `a clean keeps the bell close with the elbow by the ribs`() {
        val rep = KettlebellReps.Clean
        val toRack = moments(rep, until = rep.still)
        val furthest = toRack.maxOf { it.kettlebellCenter.x }
        assertTrue(furthest < 0.18f, "the bell gets $furthest ahead of the ankle")
        val pull = KettlebellPoses.Pull.joints()
        assertTrue(abs(pull.elbow.x - pull.hip.x) < 0.08f, "the elbow is by the ribs in the pull")
    }

    @Test
    fun `the rack holds the fist by the shoulder on an upright forearm with the bell under it`() {
        val joints = KettlebellPoses.Rack.joints()
        assertTrue((joints.wrist - joints.shoulder).getDistance() < 0.12f)
        assertEquals(joints.elbow.x, joints.wrist.x, absoluteTolerance = 0.03f, "the forearm is upright")
        assertTrue(joints.kettlebellCenter.y < joints.kettlebellAnchor.y)
        assertTrue(joints.kettlebellCenter.y > joints.elbow.y, "the bell sits in the crook of the arm")
    }

    @Test
    fun `the lockout stacks the grip over the shoulder the hip and the ankle`() {
        val joints = KettlebellPoses.Lockout.joints()
        listOf(joints.hip, joints.shoulder, joints.elbow, joints.wrist, joints.kettlebellAnchor).forEach {
            assertEquals(0f, it.x, absoluteTolerance = 0.0001f)
        }
        assertTrue(joints.kettlebellAnchor.y > joints.headCenter.y + BodyProportions.HEAD, "the grip over the head")
        assertTrue(joints.kettlebellCenter.y < joints.kettlebellAnchor.y, "the bell lies below the hand")
        assertEquals(KettlebellPoses.Lockout, KettlebellReps.Snatch.poseAt(KettlebellReps.Snatch.still))
    }

    @Test
    fun `a snatch pulls the bell high and close before it punches through`() {
        val rep = KettlebellReps.Snatch
        val up = moments(rep, until = rep.still)
        val furthest = up.maxOf { it.kettlebellCenter.x }
        assertTrue(furthest < 0.22f, "the bell gets $furthest ahead of the ankle")
        val pull = KettlebellPoses.HighPull.joints()
        assertTrue(pull.elbow.x < pull.shoulder.x - 0.1f, "the elbow is back")
        assertTrue(pull.kettlebellCenter.y > pull.shoulder.y - 0.12f, "the bell is at the chest")
        assertTrue((pull.wrist - pull.shoulder).getDistance() < 0.08f, "the fist is by the shoulder")
    }

    @Test
    fun `a press goes up from the rack past the face and back to it`() {
        val rep = KettlebellReps.Press
        assertEquals(KettlebellPoses.Rack, rep.poseAt(0f))
        assertEquals(KettlebellPoses.Lockout, rep.poseAt(rep.still))
        val bell = moments(rep).map { it.kettlebellCenter }
        assertTrue(bell.maxOf { it.x } - bell.minOf { it.x } < 0.2f, "the bell stays over the feet")
        assertTrue(moments(rep).all { it.hip == BodyPose().joints().hip }, "the legs and trunk never move")
    }

    @Test
    fun `half kneeling puts the far knee down under the hip and lays its foot along the floor`() {
        moments(KettlebellReps.HalfKneelingPress).forEach { joints ->
            assertEquals(0f, joints.knee.x, absoluteTolerance = 0.001f, "the front shin is upright")
            assertEquals(joints.knee.y, joints.hip.y, absoluteTolerance = 0.001f, "the front thigh is level")
            assertEquals(joints.hip.x, joints.farKnee.x, absoluteTolerance = 0.001f)
            assertEquals(0.035f, joints.farKnee.y, absoluteTolerance = 0.005f, "the far knee is on the floor")
            assertEquals(joints.farKnee.y, joints.farAnkle.y, absoluteTolerance = 0.005f)
            assertTrue(joints.farToe.x < joints.farAnkle.x - 0.08f, "the far foot lies back")
            assertEquals(BodyProportions.SOLE, joints.farToe.y, absoluteTolerance = 0.005f)
            assertEquals(joints.hip.x, joints.shoulder.x, absoluteTolerance = 0.001f, "the trunk is upright")
        }
    }

    @Test
    fun `the bottom of a goblet squat is deep and upright with the elbows inside the knees`() {
        val pose = KettlebellPoses.GobletBottom
        val joints = pose.joints()
        assertTrue(joints.hip.y < joints.knee.y, "the hips are below the knees")
        assertTrue(pose.torsoAngleDeg < 30f, "the trunk is nearly upright")
        assertTrue(joints.elbow.x < joints.knee.x, "the elbows are inside the knees")
        assertTrue((joints.elbow - joints.knee).getDistance() < 0.13f, "the elbows are down at the knees")
        assertEquals(0f, KettlebellReps.GobletSquat.poseAt(0f).hipAngleDeg, "it stands with the hips right through")
        val heels = joints.hip.x + (joints.kettlebellCenter.x - joints.hip.x) / 2
        assertTrue(heels in -BodyProportions.HEEL..BodyProportions.TOE, "its weight is over the foot")
    }

    @Test
    fun `a get-up keeps the bell's arm plumb from the press to standing and back`() {
        val rep = KettlebellReps.GetUp
        moments(rep, from = rep.timeOf(1), until = rep.timeOf(rep.keyframes.lastIndex - 1)).forEach { joints ->
            assertEquals(joints.shoulder.x, joints.kettlebellAnchor.x, absoluteTolerance = 0.012f)
            assertTrue(joints.kettlebellAnchor.y > joints.shoulder.y + 0.3f, "the bell is over the shoulder")
        }
    }

    @Test
    fun `a get-up starts on the floor and keeps its seat there until the bridge`() {
        val rep = KettlebellReps.GetUp
        val floor = GetUpPoses.Floor.joints()
        assertEquals(floor.hip.y, floor.shoulder.y, absoluteTolerance = 0.001f, "it lies flat")
        assertTrue(floor.hip.y < 0.07f && floor.headCenter.x < floor.hip.x, "on its back, the head behind")
        assertTrue(floor.farAnkle.y < 0.05f && floor.farWrist.y < 0.05f, "the far leg and arm lie on the floor")
        val sit = rep.keyframes.indexOfFirst { it.pose == GetUpPoses.TallSit }
        moments(rep, until = rep.timeOf(sit)).forEach { joints ->
            assertTrue((joints.hip - floor.hip).getDistance() < 0.002f, "the seat has moved to ${joints.hip}")
        }
        assertTrue(GetUpPoses.Bridge.joints().hip.y > 0.25f, "the bridge lifts the hips")
    }

    @Test
    fun `a get-up's hand stays where it is planted from the elbow to the sweep`() {
        val rep = KettlebellReps.GetUp
        val elbow = rep.keyframes.indexOfFirst { it.pose == GetUpPoses.Elbow }
        val swept = rep.keyframes.indexOfFirst { it.pose == GetUpPoses.Swept }
        val planted = GetUpPoses.Elbow.joints().farWrist
        assertEquals(GetUpPoses.Elbow.joints().farElbow.y, planted.y, absoluteTolerance = 0.003f, "the forearm is flat")
        moments(rep, from = rep.timeOf(elbow), until = rep.timeOf(swept)).forEachIndexed { step, joints ->
            assertTrue((joints.farWrist - planted).getDistance() < 0.035f, "the hand is at ${joints.farWrist} at $step")
        }
        val planting = with(GetUpPoses) { listOf(TallSit, Rising, Risen, Bridge, Drawn, Tucked, Swept) }
        planting.forEach {
            assertTrue((it.joints().farWrist - planted).getDistance() < 0.004f, "the hand is off its place in $it")
        }
    }

    @Test
    fun `a get-up sweeps the far knee down under the hip then stands with every joint stacked`() {
        val swept = GetUpPoses.Swept.joints()
        assertEquals(0.035f, swept.farKnee.y, absoluteTolerance = 0.005f)
        assertEquals(swept.hip.x, swept.farKnee.x, absoluteTolerance = 0.005f)
        val kneeling = GetUpPoses.Kneeling.joints()
        assertEquals(swept.farKnee.x, kneeling.farKnee.x, absoluteTolerance = 0.005f, "the knee stays where it landed")
        assertEquals(kneeling.hip.x, kneeling.shoulder.x, absoluteTolerance = 0.001f)
        val standing = GetUpPoses.Standing.joints()
        listOf(standing.hip, standing.shoulder, standing.kettlebellAnchor, standing.farAnkle).forEach {
            assertEquals(0f, it.x, absoluteTolerance = 0.0001f)
        }
    }

    @Test
    fun `a get-up names each step up and each step back down in order`() {
        val rep = KettlebellReps.GetUp
        assertEquals(
            listOf(
                RepPhase.FloorPress,
                RepPhase.RollToElbow,
                RepPhase.TallSit,
                RepPhase.HighBridge,
                RepPhase.SweepToKneeling,
                RepPhase.StandUp,
                RepPhase.StepBack,
                RepPhase.HandDown,
                RepPhase.SweepThrough,
                RepPhase.DownToElbow,
                RepPhase.RollDown,
                RepPhase.LowerBell,
            ),
            rep.phases.map { it.phase },
        )
        assertEquals(0f, rep.phases.first().from)
        assertEquals(1f, rep.phases.last().until)
        rep.phases.zipWithNext { a, b -> assertEquals(a.until, b.from) }
        val bridge = rep.keyframes.indexOfFirst { it.pose == GetUpPoses.Bridge }
        assertEquals(RepPhase.HighBridge, rep.phaseAt(rep.timeOf(bridge) - 0.01f))
        assertEquals(RepPhase.SweepToKneeling, rep.phaseAt(rep.timeOf(bridge) + 0.01f))
        assertEquals(RepPhase.HighBridge, rep.phaseAt(rep.timeOf(bridge)), "a stance held says how it was reached")
        assertEquals(RepPhase.HighBridge, rep.phaseAt(rep.still))
        assertEquals(RepPhase.FloorPress, rep.phaseAt(0f))
        assertEquals(RepPhase.FloorPress, rep.phaseAt(1f))
        assertEquals(RepPhase.LowerBell, rep.phaseAt(-0.01f))
        assertEquals(RepPhase.Float, KettlebellReps.Swing.phaseAt(KettlebellReps.Swing.still))
        assertEquals(RepPhase.Lockout, KettlebellReps.Snatch.phaseAt(KettlebellReps.Snatch.still))
    }

    @Test
    fun `every lift names the parts its rep goes through`() {
        reps.forEach { (name, rep) ->
            assertTrue(rep.phases.size >= 4, "$name names ${rep.phases.map { it.phase }}")
            (0..100).forEach { assertTrue(rep.phaseAt(it / 100f) != null, "$name has no name at $it") }
        }
        assertEquals(
            listOf(RepPhase.HipSnap, RepPhase.Float, RepPhase.Backswing, RepPhase.HikePass),
            KettlebellReps.Swing.phases.map { it.phase },
        )
    }

    @Test
    fun `a keyframe with no name is part of the next that has one`() {
        val a = BodyPose()
        val b = BodyPose(hipAngleDeg = 40f)
        val rep = ExerciseRep(
            listOf(
                ExerciseKeyframe(a),
                ExerciseKeyframe(b, weight = 1f, passing = true),
                ExerciseKeyframe(a, weight = 1f, phase = RepPhase.Drive),
                ExerciseKeyframe(b, weight = 1f),
                ExerciseKeyframe(a, weight = 1f),
            ),
            seconds = 1f,
        )
        assertEquals(listOf(PhaseSpan(RepPhase.Drive, from = 0f, until = 0.5f)), rep.phases)
        assertEquals(RepPhase.Drive, rep.phaseAt(0.2f))
        assertEquals(null, rep.phaseAt(0.7f))
    }

    @Test
    fun `a pose blends by its angles`() {
        val blended = BodyPose().blend(KettlebellPoses.Hinge, share = 0.5f)
        assertEquals(35f, blended.hipAngleDeg)
        assertEquals(23f, blended.torsoAngleDeg)
        assertEquals(1f, blended.shoulderAngleDeg)
        assertEquals(blended.hipAngleDeg, blended.farHipAngleDeg)
        assertLength(BodyProportions.THIGH, blended.joints().knee, blended.joints().hip, "thigh")
    }

    @Test
    fun `a rep needs a loop of keyframes that each take some time`() {
        val start = ExerciseKeyframe(BodyPose())
        val out = ExerciseKeyframe(BodyPose(hipAngleDeg = 40f), weight = 1f)
        val back = start.copy(weight = 1f)
        ExerciseRep(listOf(start, out, back), seconds = 1f)
        assertFailsWith<IllegalArgumentException> { ExerciseRep(listOf(start, back), seconds = 1f) }
        assertFailsWith<IllegalArgumentException> { ExerciseRep(listOf(start, out, out), seconds = 1f) }
        assertFailsWith<IllegalArgumentException> {
            ExerciseRep(listOf(start, out.copy(weight = 0f), back), seconds = 1f)
        }
        assertFailsWith<IllegalArgumentException> {
            ExerciseRep(listOf(start.copy(passing = true), out, back.copy(passing = true)), seconds = 1f)
        }
        assertFailsWith<IllegalArgumentException> { ExerciseRep(listOf(start, out, back), seconds = 1f, stillAt = 3) }
    }

    @Test
    fun `degrees and radians turn into each other`() {
        assertEquals(kotlin.math.PI.toFloat(), Angles.radians(Angles.HALF_TURN), absoluteTolerance = 0.0001f)
        assertEquals(Angles.QUARTER_TURN, Angles.degrees(kotlin.math.PI.toFloat() / 2), absoluteTolerance = 0.001f)
    }
}
