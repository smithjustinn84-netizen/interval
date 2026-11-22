package dev.justinsmith.interval.ui

/**
 * What a figure does as a record lands on its session card: with something in hand it raises it overhead
 * ([RaisedBell]), with nothing in hand it takes a short bow ([Bow]). A figure not on its feet (lying, seated,
 * hanging, or seen from the front/above or close up) gives none ([ExerciseRep.celebration]).
 */
enum class Celebration {
    RaisedBell,
    Bow,
    ;

    companion object {
        /** The whole gesture: there, held, and back. */
        const val MILLIS = 1600
        const val SECONDS = MILLIS / 1000f

        /** How far through the gesture the figure is there: the bell overhead, or the bow at its lowest. */
        const val TOP = STOOD_WEIGHT + RISE_WEIGHT

        /**
         * How far through its [Shockwave] a gesture [progress] of the way through is: it sets out as the figure
         * tops out ([TOP]), from the bell raised or the hips of a bow, and is spent well inside the hold. Under 0
         * before then and over 1 after, which draw nothing.
         */
        fun shockAt(progress: Float): Float = (progress - TOP) * MILLIS / Motion.SHOCKWAVE_MILLIS
    }
}

/**
 * The celebration this rep's figure gives, or null when it is never standing on its feet (or lies down during
 * the rep, hangs from a bar, or is viewed from the front/above/cropped).
 */
val ExerciseRep.celebration: Celebration?
    get() {
        if (root == RepRoot.Hand || facing != RepFacing.Side || crop != null) return null
        val joints = keyframes.map { it.pose.joints(root, anchor, facing, hold) }
        if (joints.none { it.isStanding } || joints.any { it.isLying }) return null
        return if (hold == RepHold.Free) Celebration.Bow else Celebration.RaisedBell
    }

/** On its feet in Y-up coordinates: hips well above the near knee, both knees well off the floor. */
private val BodyJoints.isStanding: Boolean
    get() = hip.y >= knee.y + BodyProportions.THIGH / 2f &&
        knee.y > BodyProportions.SHIN / 2f &&
        farKnee.y > BodyProportions.SHIN / 2f

/** Hips down on the floor in Y-up coordinates (lying or seated on the mat). */
private val BodyJoints.isLying: Boolean
    get() = hip.y < BodyProportions.PELVIS_RADIUS * 2f + BodyProportions.ANKLE

/**
 * This rep's figure giving its [celebration] from where it is [at]: a one-rep gesture [Celebration.SECONDS] long
 * that moves to the pose, holds it, and returns to where it left off. Null for a rep that gives none.
 */
fun ExerciseRep.celebrating(at: Float): ExerciseRep? {
    val kind = celebration ?: return null
    val pose = poseAt(at)
    val from = if (bellLag == 0f || at < 0f) {
        pose
    } else {
        val prev = poseAt(looped(at - bellLag))
        val prevArm = prev.shoulderAngleDeg - prev.torsoAngleDeg + prev.elbowAngleDeg
        val curArm = pose.shoulderAngleDeg - pose.torsoAngleDeg + pose.elbowAngleDeg
        val prevFarArm = prev.farShoulderAngleDeg - prev.torsoAngleDeg + prev.farElbowAngleDeg
        val curFarArm = pose.farShoulderAngleDeg - pose.torsoAngleDeg + pose.farElbowAngleDeg
        pose.copy(
            bellAngleDeg = prevArm + prev.bellAngleDeg - curArm,
            farBellAngleDeg = prevFarArm + prev.farBellAngleDeg - curFarArm,
        )
    }
    val gesture = when (kind) {
        Celebration.RaisedBell -> from.raising(hold)
        Celebration.Bow -> from.bowing()
    }
    val farFootOut = gesture.copy(
        farHipAngleDeg = from.farHipAngleDeg - from.torsoAngleDeg + gesture.torsoAngleDeg,
        farKneeAngleDeg = from.farKneeAngleDeg,
        farFootPitchDeg = from.farFootPitchDeg,
    )
    return ExerciseRep(
        keyframes = listOf(
            ExerciseKeyframe(from),
            ExerciseKeyframe(farFootOut, weight = STOOD_WEIGHT, passing = true, beat = RepBeat.Drive),
            ExerciseKeyframe(gesture, weight = RISE_WEIGHT, beat = RepBeat.Drive),
            ExerciseKeyframe(gesture, weight = HOLD_WEIGHT, beat = RepBeat.Drive),
            ExerciseKeyframe(farFootOut, weight = RISE_WEIGHT, passing = true, beat = RepBeat.Drive),
            ExerciseKeyframe(from, weight = STOOD_WEIGHT, beat = RepBeat.Drive),
        ),
        seconds = Celebration.SECONDS,
        stillAt = GESTURE_INDEX,
        hold = hold,
        facing = facing,
        root = root,
        anchor = anchor,
        props = props,
        showsTrajectory = false,
        showsTrail = false,
        continuousLoop = true,
    )
}

private fun BodyPose.raising(hold: RepHold): BodyPose {
    val arm = if (hold == RepHold.Bar) BAR_OVERHEAD else OVERHEAD
    val resting = if (hold == RepHold.BothHands) HORNS_BELL else RESTING_BELL
    val targetShoulder = shoulderAngleDeg.nearestTurn(arm + TALL_TORSO)
    val targetElbow = elbowAngleDeg.nearestTurn(0f)
    val targetBell = bellAngleDeg.nearestTurn(resting)
    val base = BodyPose(
        hipAngleDeg = TALL_TORSO,
        kneeAngleDeg = 0f,
        torsoAngleDeg = TALL_TORSO,
        shoulderAngleDeg = targetShoulder,
        elbowAngleDeg = targetElbow,
        bellAngleDeg = targetBell,
        farHipAngleDeg = TALL_TORSO,
        farKneeAngleDeg = 0f,
        farShoulderAngleDeg = if (hold == RepHold.NearHand) {
            farShoulderAngleDeg.nearestTurn(TALL_TORSO)
        } else {
            farShoulderAngleDeg.nearestTurn(arm + TALL_TORSO)
        },
        farElbowAngleDeg = farElbowAngleDeg.nearestTurn(0f),
        farBellAngleDeg = farBellAngleDeg.nearestTurn(resting),
    )
    return base
}

private fun BodyPose.bowing(): BodyPose = BodyPose(
    hipAngleDeg = BOW_TORSO,
    kneeAngleDeg = 0f,
    torsoAngleDeg = BOW_TORSO,
    shoulderAngleDeg = shoulderAngleDeg.nearestTurn(BOW_UPPER_ARM + BOW_TORSO),
    elbowAngleDeg = elbowAngleDeg.nearestTurn(BOW_ELBOW),
    farHipAngleDeg = BOW_TORSO,
    farKneeAngleDeg = 0f,
    farShoulderAngleDeg = farShoulderAngleDeg.nearestTurn(BOW_UPPER_ARM + BOW_TORSO),
    farElbowAngleDeg = farElbowAngleDeg.nearestTurn(BOW_ELBOW),
)

private const val STOOD_WEIGHT = 0.2f
private const val RISE_WEIGHT = 0.1f
private const val HOLD_WEIGHT = 0.4f
private const val GESTURE_INDEX = 2
private const val TALL_TORSO = -2f
private const val OVERHEAD = 178f
private const val BAR_OVERHEAD = 182f
private const val RESTING_BELL = -205f
private const val HORNS_BELL = -168f
private const val BOW_TORSO = 38f
private const val BOW_UPPER_ARM = 8f
private const val BOW_ELBOW = 4f
