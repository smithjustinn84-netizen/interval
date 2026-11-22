package dev.justinsmith.interval.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// The figure of an exercise diagram as numbers: how long its parts are, the angles a stance turns its joints to,
// and where those put each joint. Everything is in shares of the figure's standing height.

/**
 * How long each part of the figure is, as a share of its standing height: the floor is 0 and the top of the head
 * 1. The lengths are fixed, so a pose can only turn the joints between them, never stretch a limb.
 */
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

    /** From the wrist to the middle of the grip. */
    const val HAND = 0.045f

    /** The bell: from the grip to the top of its body, the body's radius, and half the handle's width. */
    const val BELL_HANDLE = 0.052f
    const val BELL_RADIUS = 0.068f
    const val BELL_GRIP = 0.044f

    /** The foot along the floor, behind and ahead of the ankle, and how high its middle is. */
    const val HEEL = 0.035f
    const val TOE = 0.12f
    const val SOLE = 0.02f

    /** The room a joint's limbs take round it, when the figure is fitted to its canvas. */
    const val JOINT_REACH = 0.06f
}

/**
 * Where a [BodyPose] puts each joint, in shares of the figure's standing height: x is ahead of the ankle, the way
 * the figure faces standing, and y is up from the floor. [kettlebellAnchor] is the middle of the grip, where the
 * handle sits in the hand; [kettlebellCenter] the middle of the bell's body, which is what its path follows.
 *
 * The far side's leg hangs from the same [hip] and its arm from the same [shoulder]: [farKnee] and [farAnkle],
 * its foot from [farHeel] to [farToe], and [farElbow] and [farWrist], its fist. Where the pose says nothing of
 * them they are the near side's own, behind it.
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
) {
    /** Each point the figure reaches to with the room it takes there, for fitting it to a canvas. */
    internal inline fun forEachReach(reach: (point: Offset, room: Float) -> Unit) {
        reach(knee, BodyProportions.JOINT_REACH)
        reach(hip, BodyProportions.JOINT_REACH)
        reach(shoulder, BodyProportions.JOINT_REACH)
        reach(elbow, BodyProportions.JOINT_REACH)
        reach(kettlebellAnchor, BodyProportions.JOINT_REACH)
        reach(headCenter, BodyProportions.HEAD)
        reach(kettlebellCenter, BodyProportions.BELL_RADIUS)
        reach(farKnee, BodyProportions.JOINT_REACH)
        reach(farAnkle, BodyProportions.SOLE)
        reach(farToe, BodyProportions.SOLE)
        reach(farElbow, BodyProportions.JOINT_REACH)
        reach(farWrist, BodyProportions.SOLE)
    }
}

/**
 * A lifter's stance at one moment of a rep, seen from the side and facing right, as the angles of its joints in
 * degrees. Each is the joint's own bend, so standing tall with the arm hanging is every angle at zero:
 *
 * - [torsoAngleDeg]: how far the trunk leans forward from upright. Lying on its back it is a quarter turn back.
 * - [hipAngleDeg]: how far the hip is folded, the thigh from the trunk's line. A hinge is a big fold under a
 *   trunk that leans, which sends the hips back.
 * - [kneeAngleDeg]: how far the knee is bent, the shin from the thigh's line.
 * - [shoulderAngleDeg]: how far the arm is raised ahead, from hanging along the trunk to overhead in line with it
 *   (a half turn).
 * - [elbowAngleDeg]: how far the elbow is bent, the forearm folding up toward the shoulder.
 * - [bellAngleDeg]: how far the bell is turned in the hand from the forearm's line: none while it is swung, most of
 *   a half turn back once it rests on the forearm in the rack or locked out.
 *
 * Those are the near side's: the leg whose foot is planted, which the figure is worked up from, and the arm with
 * the bell. The far side's ([farHipAngleDeg], [farKneeAngleDeg], [farShoulderAngleDeg], [farElbowAngleDeg]) are
 * the same joints of the other leg and arm, and the same angles unless a stance says otherwise: both legs under a
 * swing, a knee down behind for a half-kneeling press, a hand propping a get-up. Its foot goes where its leg puts
 * it, on the floor or off it.
 *
 * The joints are worked out from these by [joints], along limbs of fixed length. So two poses are blended by their
 * angles ([blend]), never by where their joints are, and a limb keeps its length all the way between them.
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
) {
    /** Whether the far leg or arm is anywhere but behind the near one, so there is something of it to draw. */
    val showsFarSide: Boolean
        get() = farHipAngleDeg != hipAngleDeg || farKneeAngleDeg != kneeAngleDeg ||
            farShoulderAngleDeg != shoulderAngleDeg || farElbowAngleDeg != elbowAngleDeg

    /**
     * Where the joints are: up the near leg from its ankle, which never leaves its place, out along the near arm
     * to the bell, then down the far leg from the hip and along the far arm from the shoulder.
     */
    fun joints(): BodyJoints {
        val frame = BodyFrame()
        frame.place(angles())
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
    )

    internal companion object {
        const val JOINTS = 10

        /** Each joint's place among [angles]. */
        const val HIP = 0
        const val KNEE = 1
        const val TORSO = 2
        const val SHOULDER = 3
        const val ELBOW = 4
        const val BELL = 5
        const val FAR_HIP = 6
        const val FAR_KNEE = 7
        const val FAR_SHOULDER = 8
        const val FAR_ELBOW = 9

        /** The pose whose joints are [angles], in the order [BodyPose.angles] gives them. */
        fun of(angles: FloatArray): BodyPose = BodyPose(
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
        )
    }
}

/**
 * Where a stance's joints are, worked out in place: [place] moves each to where a stance's angles put it and makes
 * nothing new, so a diagram that runs keeps one of these and places it again every frame. [joints] is the same as
 * a value, for whatever keeps it.
 */
internal class BodyFrame {
    val ankle = Offset(0f, BodyProportions.ANKLE)
    var knee = Offset.Zero
        private set
    var hip = Offset.Zero
        private set
    var shoulder = Offset.Zero
        private set
    var elbow = Offset.Zero
        private set
    var wrist = Offset.Zero
        private set
    var headCenter = Offset.Zero
        private set
    var kettlebellAnchor = Offset.Zero
        private set
    var kettlebellCenter = Offset.Zero
        private set
    var farKnee = Offset.Zero
        private set
    var farAnkle = Offset.Zero
        private set
    var farHeel = Offset.Zero
        private set
    var farToe = Offset.Zero
        private set
    var farElbow = Offset.Zero
        private set
    var farWrist = Offset.Zero
        private set

    /** Whether the far leg or arm is anywhere but behind the near one ([BodyPose.showsFarSide]). */
    var showsFarSide = false
        private set

    /**
     * Puts the joints where [angles] have them ([BodyPose.angles]' order): up the near leg from its ankle, which
     * never leaves its place, out along the near arm to the bell, then down the far leg from the hip and along
     * the far arm from the shoulder.
     */
    fun place(angles: FloatArray) {
        val torso = angles[BodyPose.TORSO]
        val thighLean = torso - angles[BodyPose.HIP]
        val armSwing = angles[BodyPose.SHOULDER] - torso
        val forearmSwing = armSwing + angles[BodyPose.ELBOW]
        knee = ankle + rising(thighLean + angles[BodyPose.KNEE]) * BodyProportions.SHIN
        hip = knee + rising(thighLean) * BodyProportions.THIGH
        shoulder = hip + rising(torso) * BodyProportions.TORSO
        elbow = shoulder + hanging(armSwing) * BodyProportions.UPPER_ARM
        wrist = elbow + hanging(forearmSwing) * BodyProportions.FOREARM
        headCenter = shoulder + rising(torso) * BodyProportions.NECK
        kettlebellAnchor = wrist + hanging(forearmSwing) * BodyProportions.HAND
        kettlebellCenter = kettlebellAnchor +
            hanging(forearmSwing + angles[BodyPose.BELL]) * (BodyProportions.BELL_HANDLE + BodyProportions.BELL_RADIUS)
        val farThighSwing = angles[BodyPose.FAR_HIP] - torso
        val farShin = hanging(farThighSwing - angles[BodyPose.FAR_KNEE])
        farKnee = hip + hanging(farThighSwing) * BodyProportions.THIGH
        farAnkle = farKnee + farShin * BodyProportions.SHIN
        val farFoot = footFrom(farAnkle, farShin)
        farHeel = farAnkle - farFoot * BodyProportions.HEEL + farFoot.sole() * SOLE_DROP
        farToe = farAnkle + farFoot * BodyProportions.TOE + farFoot.sole() * SOLE_DROP
        val farArmSwing = angles[BodyPose.FAR_SHOULDER] - torso
        farElbow = shoulder + hanging(farArmSwing) * BodyProportions.UPPER_ARM
        farWrist = farElbow + hanging(farArmSwing + angles[BodyPose.FAR_ELBOW]) * BodyProportions.FOREARM
        showsFarSide = angles[BodyPose.FAR_HIP] != angles[BodyPose.HIP] ||
            angles[BodyPose.FAR_KNEE] != angles[BodyPose.KNEE] ||
            angles[BodyPose.FAR_SHOULDER] != angles[BodyPose.SHOULDER] ||
            angles[BodyPose.FAR_ELBOW] != angles[BodyPose.ELBOW]
    }

    /** The joints where they now are, as a value. */
    fun joints() = BodyJoints(
        ankle = ankle,
        knee = knee,
        hip = hip,
        shoulder = shoulder,
        elbow = elbow,
        wrist = wrist,
        headCenter = headCenter,
        kettlebellAnchor = kettlebellAnchor,
        kettlebellCenter = kettlebellCenter,
        farKnee = farKnee,
        farAnkle = farAnkle,
        farHeel = farHeel,
        farToe = farToe,
        farElbow = farElbow,
        farWrist = farWrist,
    )
}

/** How far under the ankle the middle of the sole is. */
private const val SOLE_DROP = BodyProportions.ANKLE - BodyProportions.SOLE

/** How near the floor a foot's toes may come before it is laid along it. */
private const val FLOOR_SLACK = 0.001f

/** The way a part standing on its joint points, leaning [leanDeg] forward from straight up. */
private fun rising(leanDeg: Float): Offset = Angles.radians(leanDeg).let { Offset(sin(it), cos(it)) }

/** The way a part hanging from its joint points, swung [swingDeg] forward from straight down. */
private fun hanging(swingDeg: Float): Offset = Angles.radians(swingDeg).let { Offset(sin(it), -cos(it)) }

/** The side of a foot pointing this way that its sole is on. */
private fun Offset.sole(): Offset = Offset(y, -x)

/**
 * The way the foot at [ankle] points, under a shin that comes down to it along [shin]: square to the shin, as it
 * stands, unless that would put its toes through the floor. Then it points as far that way as the floor lets it:
 * a knee down behind lays the foot along the floor, its sole up.
 */
private fun footFrom(ankle: Offset, shin: Offset): Offset {
    val square = Offset(-shin.y, shin.x)
    val toes = ankle.y + square.y * BodyProportions.TOE + square.sole().y * SOLE_DROP
    if (toes >= BodyProportions.SOLE - FLOOR_SLACK) return square
    // The toes' height is the ankle's plus a sine of the foot's angle: the two angles that put them on the floor,
    // and of those the one nearer to square.
    val reach = hypot(BodyProportions.TOE, SOLE_DROP)
    val tilt = atan2(SOLE_DROP, BodyProportions.TOE)
    val down = asin(((BodyProportions.SOLE - ankle.y) / reach).coerceIn(-1f, 1f))
    val squareAngle = atan2(square.y, square.x)
    val near = down + tilt
    val far = PI.toFloat() - down + tilt
    val angle = if (turn(near - squareAngle) <= turn(far - squareAngle)) near else far
    return Offset(cos(angle), sin(angle))
}

/** How far apart two directions [radians] apart are, the short way round. */
private fun turn(radians: Float): Float = abs(atan2(sin(radians), cos(radians)))
