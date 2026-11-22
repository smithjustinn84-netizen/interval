package dev.justinsmith.interval.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

// The figure of an exercise diagram as numbers: how long its parts are, the angles a stance turns its joints to,
// and where those put each joint. Everything is in shares of the figure's standing height.

/**
 * Where the figure is seen from: [Side], facing right; [Front], facing the eye, for a lift that goes sideways; or
 * [Above], lying on a mat and looked down on, for one that goes across the floor.
 */
enum class RepFacing { Side, Front, Above }

/**
 * What stays put at the rep's anchor: [Ankle] the near ankle, for a lift done on the spot; [Toe] the near toe, for
 * one up on the toes; [Hand] the near hand, for one hung from a bar or propped on a bench; [Hips] the hips across,
 * over whatever of the figure is lowest resting on the floor, for a walk or a lift done lying down.
 */
enum class RepRoot { Ankle, Toe, Hand, Hips }

/**
 * What the hands hold: [BothHands] one bell between them; [NearHand] one bell in the near hand with the far arm
 * free; [Pair] a bell in each hand; [Bar] a barbell seen end on as the near plate; [Free] nothing held.
 */
enum class RepHold { BothHands, NearHand, Pair, Bar, Free }

/**
 * How long each part of the figure is, as a share of its standing height: the floor is 0 and the top of the head
 * 1. The lengths are fixed, so a pose can only turn the joints between them, never stretch a limb.
 */
@Suppress("detekt.MagicNumber") // Anatomical proportions in shares of standing height.
internal object BodyProportions {
    const val ANKLE = 0.04f
    const val SHIN = 0.24f
    const val THIGH = 0.245f
    const val TORSO = 0.29f

    /** From the shoulder to the head's centre, and the head's radius. */
    const val NECK = 0.115f
    const val HEAD = 0.07f
    const val UPPER_ARM = 0.17f
    const val FOREARM = 0.15f

    /** From the wrist to the middle of the grip, and an open hand to the fingertips. */
    const val HAND = 0.045f
    const val PALM = 0.077f

    /** The bell: from the grip to the top of its body, the body's radius, and half the handle's width. */
    const val BELL_HANDLE = 0.052f
    const val BELL_RADIUS = 0.068f
    const val BELL_GRIP = 0.044f

    /** A barbell's plate seen end on: radius (45 cm plate at standing scale), rim and sleeve. */
    const val PLATE_RADIUS = 0.129f
    const val PLATE_RIM = 0.023f
    const val SLEEVE = 0.019f

    /** The foot along the floor, behind and ahead of the ankle, and how high its middle is. */
    const val HEEL = 0.035f
    const val TOE = 0.12f
    const val SOLE = 0.02f

    /** Seen from the front: half-widths of hips and shoulders, and feet pointing at the eye. */
    const val HIP_HALF = 0.039f
    const val SHOULDER_HALF = 0.087f
    const val FRONT_FOOT = 0.039f
    const val FRONT_HEEL = 0.013f

    /** Visual depth offset of far limbs in profile, so twin legs and arms read apart. */
    const val FAR_LEG_OFFSET = 0.029f
    const val FAR_ARM_OFFSET = 0.023f

    /** Resting half-thicknesses used when grounding a lying or kneeling figure onto the floor. */
    const val PELVIS_RADIUS = 0.045f
    const val WAIST_RADIUS = 0.040f
    const val CHEST_RADIUS = 0.053f
    const val SHOULDERS_RADIUS = 0.039f
    const val LEG_HALF = 0.023f
    const val ARM_HALF = 0.018f
    const val FIST_RADIUS = 0.024f
    const val FINGERS_RADIUS = 0.011f

    /** Mat dimensions in standing-height shares. */
    const val MAT = 0.016f
    const val MAT_REACH = 0.058f
    const val MAT_LENGTH = 0.418f
    const val MAT_MARGIN = 0.084f
    const val MAT_CORNER = 0.045f
    const val MAT_EDGE = 0.0065f

    /** Prop dimensions in standing-height shares. */
    const val BENCH_TOP = 0.026f
    const val BENCH_LEG = 0.016f
    const val WALL = 0.019f
    const val RAIL = 0.035f
    const val BAND = 0.013f
    const val POST = 0.019f

    /** The room a joint's limbs take round it, when the figure is fitted to its canvas. */
    const val JOINT_REACH = 0.06f
}

/**
 * Where a [BodyPose] puts each joint, in shares of the figure's standing height: x is ahead of the ankle, the way
 * the figure faces standing, and y is up from the floor. [kettlebellAnchor] is the middle of the grip, where the
 * handle sits in the hand; [kettlebellCenter] the middle of the bell's body, which is what its path follows.
 *
 * The far side's leg hangs from [farHip] and its arm from [farShoulder]: [farKnee] and [farAnkle], its foot from
 * [farHeel] to [farToe], and [farElbow], [farWrist], [farBellAnchor] and [farBellCenter]. Seen from the side,
 * [farHip] and [farShoulder] are [hip] and [shoulder].
 */
@Immutable
data class BodyJoints(
    val ankle: Offset,
    val knee: Offset,
    val hip: Offset,
    val shoulder: Offset,
    val elbow: Offset,
    val wrist: Offset,
    val headCenter: Offset,
    val kettlebellAnchor: Offset,
    val kettlebellCenter: Offset,
    val farKnee: Offset = knee,
    val farAnkle: Offset = ankle,
    val farHeel: Offset = Offset(-BodyProportions.HEEL, BodyProportions.SOLE),
    val farToe: Offset = Offset(BodyProportions.TOE, BodyProportions.SOLE),
    val farElbow: Offset = elbow,
    val farWrist: Offset = wrist,
    val heel: Offset = Offset(-BodyProportions.HEEL, BodyProportions.SOLE),
    val toe: Offset = Offset(BodyProportions.TOE, BodyProportions.SOLE),
    val farHip: Offset = hip,
    val farShoulder: Offset = shoulder,
    val farBellAnchor: Offset = kettlebellAnchor,
    val farBellCenter: Offset = kettlebellCenter,
    val fingers: Offset = kettlebellAnchor,
    val farFingers: Offset = farBellAnchor,
    val front: Boolean = false,
    val armsBehind: Boolean = false,
    val spineBow: Float = 0f,
) {
    /** The middle of the hips, and of the shoulders: the two ends of the spine, whichever way it is seen. */
    val hips: Offset get() = (hip + farHip) / 2f

    val neck: Offset get() = (shoulder + farShoulder) / 2f

    /** Each point the figure reaches to with the room it takes there, for fitting it to a canvas. */
    internal inline fun forEachReach(hold: RepHold = RepHold.NearHand, reach: (point: Offset, room: Float) -> Unit) {
        reach(heel, BodyProportions.SOLE)
        reach(toe, BodyProportions.SOLE)
        reach(ankle, BodyProportions.SOLE)
        reach(knee, BodyProportions.JOINT_REACH)
        reach(hip, BodyProportions.JOINT_REACH)
        reach(farHip, BodyProportions.JOINT_REACH)
        reach(shoulder, BodyProportions.JOINT_REACH)
        reach(farShoulder, BodyProportions.JOINT_REACH)
        reach(elbow, BodyProportions.JOINT_REACH)
        reach(kettlebellAnchor, BodyProportions.JOINT_REACH)
        reach(headCenter, BodyProportions.HEAD)
        reach(fingers, BodyProportions.FINGERS_RADIUS)
        reach(farFingers, BodyProportions.FINGERS_RADIUS)
        when (hold) {
            RepHold.Bar -> reach(kettlebellAnchor, BodyProportions.PLATE_RADIUS)

            RepHold.Free -> Unit

            RepHold.Pair -> {
                reach(kettlebellCenter, BodyProportions.BELL_RADIUS)
                reach(farBellCenter, BodyProportions.BELL_RADIUS)
            }

            RepHold.BothHands, RepHold.NearHand -> reach(kettlebellCenter, BodyProportions.BELL_RADIUS)
        }
        reach(farKnee, BodyProportions.JOINT_REACH)
        reach(farAnkle, BodyProportions.SOLE)
        reach(farHeel, BodyProportions.SOLE)
        reach(farToe, BodyProportions.SOLE)
        reach(farElbow, BodyProportions.JOINT_REACH)
        reach(farWrist, BodyProportions.SOLE)
    }
}

/**
 * A lifter's stance at one moment of a rep, as the angles of its joints in degrees. Seen from the side and facing
 * right, each is the joint's own bend, so standing tall with the arm hanging is every angle at zero:
 *
 * - [torsoAngleDeg]: how far the trunk leans forward from upright. Lying on its back it is a quarter turn back.
 * - [hipAngleDeg]: how far the hip is folded, the thigh from the trunk's line.
 * - [kneeAngleDeg]: how far the knee is bent, the shin from the thigh's line.
 * - [shoulderAngleDeg]: how far the arm is raised ahead, from hanging along the trunk to overhead (180).
 * - [elbowAngleDeg]: how far the elbow is bent, the forearm folding up toward the shoulder.
 * - [bellAngleDeg]: how far the bell is turned in the hand from the forearm's line.
 *
 * The far side's angles default to the near side's unless specified. Optional parameters cover double bells
 * ([farBellAngleDeg]), pitched feet ([footPitchDeg], [farFootPitchDeg]), spinal flexion/extension ([spineBowDeg]),
 * open palms and wrists ([palmOpen], [wristDeg]), and front/above view foreshortening and rotation ([armReach],
 * [legsScale], [farLegsScale], [trunkScale], [armsBehind], [bodyTurnDeg]).
 */
@Immutable
data class BodyPose(
    val hipAngleDeg: Float = 0f,
    val kneeAngleDeg: Float = 0f,
    val torsoAngleDeg: Float = 0f,
    val shoulderAngleDeg: Float = 0f,
    val elbowAngleDeg: Float = 0f,
    val bellAngleDeg: Float = 0f,
    val farHipAngleDeg: Float = hipAngleDeg,
    val farKneeAngleDeg: Float = kneeAngleDeg,
    val farShoulderAngleDeg: Float = shoulderAngleDeg,
    val farElbowAngleDeg: Float = elbowAngleDeg,
    val farBellAngleDeg: Float = bellAngleDeg,
    val footPitchDeg: Float = 0f,
    val farFootPitchDeg: Float = 0f,
    val spineBowDeg: Float = 0f,
    val wristDeg: Float = 0f,
    val farWristDeg: Float = wristDeg,
    val palmOpen: Float = 0f,
    val farPalmOpen: Float = palmOpen,
    val armReach: Float = 1f,
    val legsScale: Float = 1f,
    val farLegsScale: Float = legsScale,
    val trunkScale: Float = 1f,
    val armsBehind: Float = 0f,
    val bodyTurnDeg: Float = 0f,
) {
    /** Whether the far leg or arm is anywhere but behind the near one, so there is something of it to draw. */
    val showsFarSide: Boolean
        get() = farHipAngleDeg != hipAngleDeg || farKneeAngleDeg != kneeAngleDeg ||
            farShoulderAngleDeg != shoulderAngleDeg || farElbowAngleDeg != elbowAngleDeg ||
            farBellAngleDeg != bellAngleDeg || farFootPitchDeg != footPitchDeg ||
            farPalmOpen != palmOpen || farWristDeg != wristDeg || farLegsScale != legsScale

    /**
     * Where the joints are for this stance under [root], [anchor], [facing], and [hold].
     */
    fun joints(
        root: RepRoot = RepRoot.Ankle,
        anchor: Offset = Offset.Zero,
        facing: RepFacing = RepFacing.Side,
        hold: RepHold = RepHold.NearHand,
        bellSwingOverride: Float = Float.NaN,
        farBellSwingOverride: Float = Float.NaN,
    ): BodyJoints {
        val frame = BodyFrame()
        frame.place(angles(), root, anchor, facing, hold, bellSwingOverride, farBellSwingOverride)
        return frame.joints()
    }

    /** This pose [share] of the way to [to], from 0 to 1, every joint turning at once. */
    fun blend(to: BodyPose, share: Float): BodyPose {
        val from = angles()
        val toward = to.angles()
        return of(FloatArray(JOINTS) { from[it] + (toward[it] - from[it]) * share })
    }

    internal fun angles(): FloatArray = floatArrayOf(
        hipAngleDeg,
        kneeAngleDeg,
        torsoAngleDeg,
        shoulderAngleDeg,
        elbowAngleDeg,
        bellAngleDeg,
        farHipAngleDeg,
        farKneeAngleDeg,
        farShoulderAngleDeg,
        farElbowAngleDeg,
        farBellAngleDeg,
        footPitchDeg,
        farFootPitchDeg,
        spineBowDeg,
        wristDeg,
        farWristDeg,
        palmOpen,
        farPalmOpen,
        armReach,
        legsScale,
        farLegsScale,
        trunkScale,
        armsBehind,
        bodyTurnDeg,
    )

    companion object {
        internal const val JOINTS = 24

        /** Each joint's place among angles. */
        internal const val HIP = 0
        internal const val KNEE = 1
        internal const val TORSO = 2
        internal const val SHOULDER = 3
        internal const val ELBOW = 4
        internal const val BELL = 5
        internal const val FAR_HIP = 6
        internal const val FAR_KNEE = 7
        internal const val FAR_SHOULDER = 8
        internal const val FAR_ELBOW = 9
        internal const val FAR_BELL = 10
        internal const val FOOT_PITCH = 11
        internal const val FAR_FOOT_PITCH = 12
        internal const val SPINE_BOW = 13
        internal const val WRIST = 14
        internal const val FAR_WRIST = 15
        internal const val PALM_OPEN = 16
        internal const val FAR_PALM_OPEN = 17
        internal const val ARM_REACH = 18
        internal const val LEGS_SCALE = 19
        internal const val FAR_LEGS_SCALE = 20
        internal const val TRUNK_SCALE = 21
        internal const val ARMS_BEHIND = 22
        internal const val BODY_TURN = 23

        /** Index of the first non-angular scalar channel (scales & toggles). */
        private const val FIRST_SCALAR = PALM_OPEN
        private const val SAME_EPSILON = 0.01f

        /** Whether [a] and [b] represent the same stance, allowing whole 360° turns on angular joints. */
        fun sameStance(a: BodyPose, b: BodyPose): Boolean {
            val fa = a.angles()
            val fb = b.angles()
            return (0..<JOINTS).all { i ->
                val diff = abs(fa[i] - fb[i])
                if (i >= FIRST_SCALAR) {
                    diff < SAME_EPSILON
                } else {
                    val mod = diff % Angles.FULL_TURN
                    mod < SAME_EPSILON || Angles.FULL_TURN - mod < SAME_EPSILON
                }
            }
        }

        /** The pose whose joints are [angles], in the order [BodyPose.angles] gives them. */
        internal fun of(angles: FloatArray): BodyPose = BodyPose(
            hipAngleDeg = angles[HIP],
            kneeAngleDeg = angles[KNEE],
            torsoAngleDeg = angles[TORSO],
            shoulderAngleDeg = angles[SHOULDER],
            elbowAngleDeg = angles[ELBOW],
            bellAngleDeg = angles[BELL],
            farHipAngleDeg = angles[FAR_HIP],
            farKneeAngleDeg = angles[FAR_KNEE],
            farShoulderAngleDeg = angles[FAR_SHOULDER],
            farElbowAngleDeg = angles[FAR_ELBOW],
            farBellAngleDeg = angles[FAR_BELL],
            footPitchDeg = angles[FOOT_PITCH],
            farFootPitchDeg = angles[FAR_FOOT_PITCH],
            spineBowDeg = angles[SPINE_BOW],
            wristDeg = angles[WRIST],
            farWristDeg = angles[FAR_WRIST],
            palmOpen = angles[PALM_OPEN],
            farPalmOpen = angles[FAR_PALM_OPEN],
            armReach = angles[ARM_REACH],
            legsScale = angles[LEGS_SCALE],
            farLegsScale = angles[FAR_LEGS_SCALE],
            trunkScale = angles[TRUNK_SCALE],
            armsBehind = angles[ARMS_BEHIND],
            bodyTurnDeg = angles[BODY_TURN],
        )
    }
}
