package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Where a stance's joints are, worked out in place: [place] moves each to where a stance's angles put it and makes
 * nothing new, so a diagram that runs keeps one of these and places it again every frame. [joints] is the same as
 * a value, for whatever keeps it.
 */
@Suppress("detekt.TooManyFunctions") // Zero-allocation solver split into cohesive placement steps.
internal class BodyFrame {
    var ankle = Offset(0f, BodyProportions.ANKLE)
        private set
    var heel = Offset(-BodyProportions.HEEL, BodyProportions.SOLE)
        private set
    var toe = Offset(BodyProportions.TOE, BodyProportions.SOLE)
        private set
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
    var farHip = Offset.Zero
        private set
    var farKnee = Offset.Zero
        private set
    var farAnkle = Offset.Zero
        private set
    var farHeel = Offset.Zero
        private set
    var farToe = Offset.Zero
        private set
    var farShoulder = Offset.Zero
        private set
    var farElbow = Offset.Zero
        private set
    var farWrist = Offset.Zero
        private set
    var farBellAnchor = Offset.Zero
        private set
    var farBellCenter = Offset.Zero
        private set
    var fingers = Offset.Zero
        private set
    var farFingers = Offset.Zero
        private set

    /** Whether the far leg or arm is anywhere but behind the near one ([BodyPose.showsFarSide]). */
    var showsFarSide = false
        private set

    /** Whether the figure is viewed from the front or above rather than in profile. */
    var front = false
        private set

    /** Whether arms and held equipment sit behind the trunk (front view). */
    var armsBehind = false
        private set

    /** Spinal flexion (> 0) or extension (< 0) in degrees. */
    var spineBow = 0f
        private set

    /** Whether the near or far hand is open past the wrist. */
    var hasOpenPalm = false
        private set
    var hasFarOpenPalm = false
        private set

    /** Middle of the hips and shoulders, whichever way the figure is viewed. */
    val hips: Offset get() = Offset((hip.x + farHip.x) * HALF, (hip.y + farHip.y) * HALF)
    val neck: Offset get() = Offset((shoulder.x + farShoulder.x) * HALF, (shoulder.y + farShoulder.y) * HALF)

    /**
     * Puts the joints where [angles] have them ([BodyPose.angles]' order) under [root], [anchor], [facing], and
     * [hold]. Optional [bellSwingOverride] and [farBellSwingOverride] supply lagged bell hang angles (in degrees)
     * without allocating.
     */
    fun place(
        angles: FloatArray,
        root: RepRoot = RepRoot.Ankle,
        anchor: Offset = Offset.Zero,
        facing: RepFacing = RepFacing.Side,
        hold: RepHold = RepHold.NearHand,
        bellSwingOverride: Float = Float.NaN,
        farBellSwingOverride: Float = Float.NaN,
    ) {
        if (facing == RepFacing.Side) {
            placeSide(angles, hold, bellSwingOverride, farBellSwingOverride)
        } else {
            placeFront(angles, hold, bellSwingOverride, farBellSwingOverride)
        }
        val turn = angles[BodyPose.BODY_TURN]
        if (turn != 0f) rotateAbout(hips, turn)
        ground(root, anchor, facing)
    }

    private fun placeSide(
        angles: FloatArray,
        hold: RepHold,
        bellSwingOverride: Float,
        farBellSwingOverride: Float,
    ) {
        front = false
        armsBehind = false
        spineBow = angles[BodyPose.SPINE_BOW]
        val legs = angles[BodyPose.LEGS_SCALE]
        val farLegs = angles[BodyPose.FAR_LEGS_SCALE]
        val trunk = angles[BodyPose.TRUNK_SCALE]
        val reach = angles[BodyPose.ARM_REACH]
        val torso = angles[BodyPose.TORSO]
        val thighLean = torso - angles[BodyPose.HIP]
        val armSwing = angles[BodyPose.SHOULDER] - torso
        val forearmSwing = armSwing + angles[BodyPose.ELBOW]

        ankle = Offset(0f, BodyProportions.ANKLE)
        placeFoot(ankle, angles[BodyPose.FOOT_PITCH], far = false)
        knee = ankle + rising(thighLean + angles[BodyPose.KNEE]) * (BodyProportions.SHIN * legs)
        hip = knee + rising(thighLean) * (BodyProportions.THIGH * legs)
        farHip = hip
        shoulder = hip + rising(torso) * (BodyProportions.TORSO * trunk)
        farShoulder = shoulder
        headCenter = shoulder + rising(torso) * BodyProportions.NECK

        elbow = shoulder + hanging(armSwing) * (BodyProportions.UPPER_ARM * reach)
        wrist = elbow + hanging(forearmSwing) * (BodyProportions.FOREARM * reach)
        kettlebellAnchor = wrist + hanging(forearmSwing) * (BodyProportions.HAND * reach)
        val bellSwing = if (bellSwingOverride.isNaN()) forearmSwing + angles[BodyPose.BELL] else bellSwingOverride
        kettlebellCenter = if (hold == RepHold.Free || hold == RepHold.Bar) {
            kettlebellAnchor
        } else {
            kettlebellAnchor + hanging(bellSwing) * BELL_REACH
        }

        placeSideFarLimbs(angles, hold, torso, farLegs, reach, farBellSwingOverride)
        val farForearmSwing = angles[BodyPose.FAR_SHOULDER] - torso + angles[BodyPose.FAR_ELBOW]
        placeHands(angles, forearmSwing, farForearmSwing, reach)
        showsFarSide = hasDistinctFarSide(angles, legs, farLegs)
    }

    private fun placeSideFarLimbs(
        angles: FloatArray,
        hold: RepHold,
        torso: Float,
        farLegs: Float,
        reach: Float,
        farBellSwingOverride: Float,
    ) {
        val farThighSwing = angles[BodyPose.FAR_HIP] - torso
        val farShin = hanging(farThighSwing - angles[BodyPose.FAR_KNEE])
        farKnee = hip + hanging(farThighSwing) * (BodyProportions.THIGH * farLegs)
        farAnkle = farKnee + farShin * (BodyProportions.SHIN * farLegs)
        val farFootPitch = angles[BodyPose.FAR_FOOT_PITCH]
        if (farFootPitch != 0f) {
            placeFoot(farAnkle, farFootPitch, far = true)
        } else {
            val farFoot = footFrom(farAnkle, farShin)
            farHeel = farAnkle - farFoot * BodyProportions.HEEL + farFoot.sole() * SOLE_DROP
            farToe = farAnkle + farFoot * BodyProportions.TOE + farFoot.sole() * SOLE_DROP
        }

        val farArmSwing = angles[BodyPose.FAR_SHOULDER] - torso
        val farForearmSwing = farArmSwing + angles[BodyPose.FAR_ELBOW]
        farElbow = shoulder + hanging(farArmSwing) * (BodyProportions.UPPER_ARM * reach)
        farWrist = farElbow + hanging(farForearmSwing) * (BodyProportions.FOREARM * reach)
        farBellAnchor = farWrist + hanging(farForearmSwing) * (BodyProportions.HAND * reach)
        val farBellSwing =
            if (farBellSwingOverride.isNaN()) farForearmSwing + angles[BodyPose.FAR_BELL] else farBellSwingOverride
        farBellCenter = if (hold == RepHold.Pair) {
            farBellAnchor + hanging(farBellSwing) * BELL_REACH
        } else {
            farBellAnchor
        }
    }

    private fun hasDistinctFarSide(angles: FloatArray, legs: Float, farLegs: Float): Boolean =
        angles[BodyPose.FAR_HIP] != angles[BodyPose.HIP] ||
            angles[BodyPose.FAR_KNEE] != angles[BodyPose.KNEE] ||
            angles[BodyPose.FAR_SHOULDER] != angles[BodyPose.SHOULDER] ||
            angles[BodyPose.FAR_ELBOW] != angles[BodyPose.ELBOW] ||
            angles[BodyPose.FAR_BELL] != angles[BodyPose.BELL] ||
            angles[BodyPose.FAR_FOOT_PITCH] != angles[BodyPose.FOOT_PITCH] ||
            angles[BodyPose.FAR_PALM_OPEN] != angles[BodyPose.PALM_OPEN] ||
            angles[BodyPose.FAR_WRIST] != angles[BodyPose.WRIST] ||
            farLegs != legs

    private fun placeFront(
        angles: FloatArray,
        hold: RepHold,
        bellSwingOverride: Float,
        farBellSwingOverride: Float,
    ) {
        front = true
        armsBehind = angles[BodyPose.ARMS_BEHIND] > BEHIND_THRESHOLD
        spineBow = angles[BodyPose.SPINE_BOW]
        val legs = angles[BodyPose.LEGS_SCALE]
        val farLegs = angles[BodyPose.FAR_LEGS_SCALE]
        val trunk = angles[BodyPose.TRUNK_SCALE]
        val reach = angles[BodyPose.ARM_REACH]
        val torso = angles[BodyPose.TORSO]
        val thighLean = torso - angles[BodyPose.HIP]

        ankle = Offset(0f, BodyProportions.ANKLE)
        knee = ankle + rising(thighLean + angles[BodyPose.KNEE]) * (BodyProportions.SHIN * legs)
        hip = knee + rising(thighLean) * (BodyProportions.THIGH * legs)
        farHip = Offset(hip.x - 2f * BodyProportions.HIP_HALF, hip.y)
        val farThighSwing = angles[BodyPose.FAR_HIP] - torso
        val farShin = hanging(farThighSwing - angles[BodyPose.FAR_KNEE])
        farKnee = farHip + hanging(farThighSwing) * (BodyProportions.THIGH * farLegs)
        farAnkle = farKnee + farShin * (BodyProportions.SHIN * farLegs)

        val midHips = Offset((hip.x + farHip.x) * HALF, (hip.y + farHip.y) * HALF)
        val midNeck = midHips + rising(torso) * (BodyProportions.TORSO * trunk)
        val torsoRad = Angles.radians(torso)
        val across = Offset(cos(torsoRad), -sin(torsoRad)) * BodyProportions.SHOULDER_HALF
        shoulder = midNeck + across
        farShoulder = midNeck - across
        headCenter = midNeck + rising(torso) * BodyProportions.NECK

        heel = Offset(ankle.x - BodyProportions.FRONT_HEEL, ankle.y - SOLE_DROP)
        toe = Offset(ankle.x + BodyProportions.FRONT_FOOT, ankle.y - SOLE_DROP)
        farHeel = Offset(farAnkle.x + BodyProportions.FRONT_HEEL, farAnkle.y - SOLE_DROP)
        farToe = Offset(farAnkle.x - BodyProportions.FRONT_FOOT, farAnkle.y - SOLE_DROP)

        val armSwing = angles[BodyPose.SHOULDER] - torso
        val forearmSwing = armSwing + angles[BodyPose.ELBOW]
        elbow = shoulder + hanging(armSwing) * (BodyProportions.UPPER_ARM * reach)
        wrist = elbow + hanging(forearmSwing) * (BodyProportions.FOREARM * reach)
        kettlebellAnchor = wrist + hanging(forearmSwing) * (BodyProportions.HAND * reach)

        val farArmSwing = angles[BodyPose.FAR_SHOULDER] - torso
        val farForearmSwing = farArmSwing + angles[BodyPose.FAR_ELBOW]
        farElbow = farShoulder + hanging(farArmSwing) * (BodyProportions.UPPER_ARM * reach)
        farWrist = farElbow + hanging(farForearmSwing) * (BodyProportions.FOREARM * reach)
        farBellAnchor = farWrist + hanging(farForearmSwing) * (BodyProportions.HAND * reach)

        val bellSwing = if (bellSwingOverride.isNaN()) forearmSwing + angles[BodyPose.BELL] else bellSwingOverride
        val farBellSwing =
            if (farBellSwingOverride.isNaN()) farForearmSwing + angles[BodyPose.FAR_BELL] else farBellSwingOverride
        kettlebellCenter = when (hold) {
            RepHold.Free, RepHold.Bar -> kettlebellAnchor

            RepHold.BothHands -> Offset(
                (kettlebellAnchor.x + farBellAnchor.x) * HALF,
                (kettlebellAnchor.y + farBellAnchor.y) * HALF,
            ) + hanging(bellSwing) * BELL_REACH

            RepHold.NearHand, RepHold.Pair -> kettlebellAnchor + hanging(bellSwing) * BELL_REACH
        }
        farBellCenter = if (hold == RepHold.Pair) {
            farBellAnchor + hanging(farBellSwing) * BELL_REACH
        } else {
            farBellAnchor
        }

        placeHands(angles, forearmSwing, farForearmSwing, reach)
        showsFarSide = true
    }

    private fun placeFoot(ankleAt: Offset, pitchDeg: Float, far: Boolean) {
        if (pitchDeg == 0f) {
            val h = Offset(ankleAt.x - BodyProportions.HEEL, ankleAt.y - SOLE_DROP)
            val t = Offset(ankleAt.x + BodyProportions.TOE, ankleAt.y - SOLE_DROP)
            if (far) {
                farHeel = h
                farToe = t
            } else {
                heel = h
                toe = t
            }
            return
        }
        val rad = Angles.radians(pitchDeg)
        val c = cos(rad)
        val s = sin(rad)
        val dirX = c
        val dirY = -s
        val upX = s
        val upY = c
        val h = Offset(
            ankleAt.x - dirX * BodyProportions.HEEL - upX * SOLE_DROP,
            ankleAt.y - dirY * BodyProportions.HEEL - upY * SOLE_DROP,
        )
        val t = Offset(
            ankleAt.x + dirX * BodyProportions.TOE - upX * SOLE_DROP,
            ankleAt.y + dirY * BodyProportions.TOE - upY * SOLE_DROP,
        )
        if (far) {
            farHeel = h
            farToe = t
        } else {
            heel = h
            toe = t
        }
    }

    private fun placeHands(angles: FloatArray, forearmSwing: Float, farForearmSwing: Float, reach: Float) {
        val palm = angles[BodyPose.PALM_OPEN]
        hasOpenPalm = palm > PALM_THRESHOLD
        fingers = if (hasOpenPalm) {
            kettlebellAnchor + hanging(forearmSwing + angles[BodyPose.WRIST]) * (BodyProportions.PALM * reach * palm)
        } else {
            kettlebellAnchor
        }
        val farPalm = angles[BodyPose.FAR_PALM_OPEN]
        hasFarOpenPalm = farPalm > PALM_THRESHOLD
        farFingers = if (hasFarOpenPalm) {
            farBellAnchor +
                hanging(farForearmSwing + angles[BodyPose.FAR_WRIST]) * (BodyProportions.PALM * reach * farPalm)
        } else {
            farBellAnchor
        }
    }

    private fun rotateAbout(pivot: Offset, clockwiseDeg: Float) {
        val rad = Angles.radians(clockwiseDeg)
        val c = cos(rad)
        val s = sin(rad)
        ankle = rot(ankle, pivot, c, s)
        heel = rot(heel, pivot, c, s)
        toe = rot(toe, pivot, c, s)
        knee = rot(knee, pivot, c, s)
        hip = rot(hip, pivot, c, s)
        shoulder = rot(shoulder, pivot, c, s)
        elbow = rot(elbow, pivot, c, s)
        wrist = rot(wrist, pivot, c, s)
        headCenter = rot(headCenter, pivot, c, s)
        kettlebellAnchor = rot(kettlebellAnchor, pivot, c, s)
        kettlebellCenter = rot(kettlebellCenter, pivot, c, s)
        farHip = rot(farHip, pivot, c, s)
        farKnee = rot(farKnee, pivot, c, s)
        farAnkle = rot(farAnkle, pivot, c, s)
        farHeel = rot(farHeel, pivot, c, s)
        farToe = rot(farToe, pivot, c, s)
        farShoulder = rot(farShoulder, pivot, c, s)
        farElbow = rot(farElbow, pivot, c, s)
        farWrist = rot(farWrist, pivot, c, s)
        farBellAnchor = rot(farBellAnchor, pivot, c, s)
        farBellCenter = rot(farBellCenter, pivot, c, s)
        fingers = rot(fingers, pivot, c, s)
        farFingers = rot(farFingers, pivot, c, s)
    }

    private fun ground(root: RepRoot, anchor: Offset, facing: RepFacing) {
        val shift = if (facing == RepFacing.Above) {
            val origin = if (root == RepRoot.Ankle) ankle else hips
            Offset(anchor.x - origin.x, anchor.y - origin.y)
        } else {
            when (root) {
                RepRoot.Ankle -> Offset(anchor.x - ankle.x, anchor.y + BodyProportions.ANKLE - ankle.y)

                RepRoot.Toe -> Offset(anchor.x - toe.x, anchor.y + BodyProportions.SOLE - toe.y)

                RepRoot.Hand -> Offset(anchor.x - kettlebellAnchor.x, anchor.y - kettlebellAnchor.y)

                RepRoot.Hips -> {
                    val hx = if (front) hips.x else hip.x
                    Offset(anchor.x - hx, anchor.y - lowestY())
                }
            }
        }
        if (shift.x != 0f || shift.y != 0f) translate(shift)
    }

    private fun translate(by: Offset) {
        ankle += by
        heel += by
        toe += by
        knee += by
        hip += by
        shoulder += by
        elbow += by
        wrist += by
        headCenter += by
        kettlebellAnchor += by
        kettlebellCenter += by
        farHip += by
        farKnee += by
        farAnkle += by
        farHeel += by
        farToe += by
        farShoulder += by
        farElbow += by
        farWrist += by
        farBellAnchor += by
        farBellCenter += by
        fingers += by
        farFingers += by
    }

    /** Lowest Y reached by any body part (in Y-up coordinates, accounting for part thickness). */
    private fun lowestY(): Float {
        var low = min(min(toe.y, heel.y), min(farToe.y, farHeel.y)) - BodyProportions.SOLE
        low = min(low, min(knee.y, farKnee.y) - BodyProportions.LEG_HALF)
        low = min(low, min(hip.y, farHip.y) - BodyProportions.PELVIS_RADIUS)
        low = min(low, min(shoulder.y, farShoulder.y) - BodyProportions.SHOULDERS_RADIUS)
        low = min(low, headCenter.y - BodyProportions.HEAD)
        low = min(low, min(elbow.y, farElbow.y) - BodyProportions.ARM_HALF)
        low = min(low, min(kettlebellAnchor.y, farBellAnchor.y) - BodyProportions.FIST_RADIUS)
        low = min(low, min(fingers.y, farFingers.y) - BodyProportions.FINGERS_RADIUS)
        if (!front) {
            val spX = shoulder.x - hip.x
            val spY = shoulder.y - hip.y
            val len = hypot(spX, spY)
            val frontY = if (len > MIN_SPINE) -spX / len else 0f
            val waistY = hip.y + spY * WAIST_AT - frontY * (spineBow * SPINE_BOW_REACH)
            val chestY = hip.y + spY * CHEST_AT + frontY * (CHEST_FORWARD - spineBow * SPINE_BOW_REACH * CHEST_BOW)
            low = min(low, waistY - BodyProportions.WAIST_RADIUS)
            low = min(low, chestY - BodyProportions.CHEST_RADIUS)
        }
        return low
    }

    /** Where [end] of a resistance band attaches on this frame (in Y-up standing-height units). */
    fun bandPlace(end: RepBandEnd): Offset = when (end) {
        RepBandEnd.Hand -> kettlebellAnchor
        RepBandEnd.FarHand -> farBellAnchor
        RepBandEnd.Foot -> Offset((ankle.x + toe.x) * HALF, (ankle.y + toe.y) * HALF)
        RepBandEnd.Knee -> knee
        RepBandEnd.FarKnee -> if (front) farKnee else Offset(farKnee.x - BodyProportions.FAR_LEG_OFFSET, farKnee.y)
        RepBandEnd.Neck -> neck
        is RepBandEnd.Post -> end.at
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
        heel = heel,
        toe = toe,
        farHip = farHip,
        farShoulder = farShoulder,
        farBellAnchor = farBellAnchor,
        farBellCenter = farBellCenter,
        fingers = fingers,
        farFingers = farFingers,
        front = front,
        armsBehind = armsBehind,
        spineBow = spineBow,
    )
}

/** Rotates [point] clockwise in screen space (negative angle in Y-up) about [pivot]. */
private fun rot(point: Offset, pivot: Offset, cos: Float, sin: Float): Offset {
    val dx = point.x - pivot.x
    val dy = point.y - pivot.y
    return Offset(pivot.x + dx * cos + dy * sin, pivot.y - dx * sin + dy * cos)
}

private const val HALF = 0.5f
private const val BELL_REACH = BodyProportions.BELL_HANDLE + BodyProportions.BELL_RADIUS
private const val SOLE_DROP = BodyProportions.ANKLE - BodyProportions.SOLE
private const val FLOOR_SLACK = 0.001f
private const val BEHIND_THRESHOLD = 0.5f
private const val PALM_THRESHOLD = 0.001f
private const val MIN_SPINE = 0.001f
private const val WAIST_AT = 0.42f
private const val CHEST_AT = 0.74f
private const val CHEST_FORWARD = BodyProportions.CHEST_RADIUS - BodyProportions.PELVIS_RADIUS
private const val SPINE_BOW_REACH = 0.00193f
private const val CHEST_BOW = 0.7f

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
