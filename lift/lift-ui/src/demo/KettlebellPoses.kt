package dev.justinsmith.lift.ui.demo

/**
 * The stances the bell lifts share, so a clean lands in the same rack a press starts from. Angles are as
 * [Pose] has them: a bell in the rack or locked out sits just behind the forearm ([RESTING]), a whole turn on
 * ([RESTING_TURNED]) when it got there over the hand.
 */
internal object KettlebellPoses {
    /** A bell resting on the back of the forearm, a little behind straight down from the fist. */
    const val RESTING = -10f

    /** The same, reached by a bell that went over the top of the hand, as a snatch's does. */
    const val RESTING_TURNED = 350f

    /** A bell turned bottoms-up, standing straight over the fist. */
    const val BOTTOMS_UP = 180f

    /** A bell held by the horns in two hands, hanging just ahead of them, as a goblet or a thruster has it. */
    const val HORNS = 12f

    /** Standing tall, the arm hanging with the bell past the grip. */
    val stand = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 0f)

    /** The bell hiked high between the legs, the hips back, the arm straight. */
    val hike = swingPose(shin = 12f, thigh = -55f, torso = 65f, arm = -40f)

    /** Driving out of the hike, the hips coming through. */
    val drive = swingPose(shin = 6f, thigh = -24f, torso = 30f, arm = -14f)

    /** The hips snapped to a stand, the bell on its way up. */
    val snap = swingPose(shin = 0f, thigh = 0f, torso = -4f, arm = 32f)

    /** Hinging to meet the bell on its way back down. */
    val hinge = swingPose(shin = 8f, thigh = -35f, torso = 40f, arm = -10f)

    /** Standing with the bell in the rack: the elbow tucked to the ribs, the fist at the collarbone. */
    val rack = Pose(shin = 0f, thigh = 0f, torso = -3f, upperArm = 15f, forearm = 165f, bell = RESTING)

    /** Standing with the bell locked out overhead, the arm by the ear. */
    val lockout = Pose(shin = 0f, thigh = 0f, torso = -2f, upperArm = 178f, forearm = 178f, bell = RESTING)

    /** Zipping the bell up close on the way to the rack, the elbow in, the bell about to roll round the wrist. */
    val zip = Pose(shin = 0f, thigh = 0f, torso = -4f, upperArm = 20f, forearm = 70f, bell = 15f)

    /** Halfway through a press, the elbow forward at the shoulder and the forearm upright. */
    val pressing = Pose(shin = 0f, thigh = 0f, torso = -3f, upperArm = 95f, forearm = 175f, bell = RESTING)

    /** A short dip of the knees with the torso upright, before a drive. */
    val dip = Pose(shin = 16f, thigh = -22f, torso = 0f, upperArm = 15f, forearm = 165f, bell = RESTING)

    /** The bottom of a squat, the hips below the knees and the chest up. */
    val squat = Pose(shin = 32f, thigh = -100f, torso = 22f, upperArm = 15f, forearm = 165f, bell = RESTING)

    /** The snatch's pull: the elbow high and the bell flying on past the fist, about to turn over it. */
    val snatchPull = Pose(shin = 0f, thigh = 0f, torso = -4f, upperArm = 125f, forearm = 150f, bell = 175f)

    /** Dropping out of the lockout: the arm tipping forward, the bell turned back over the fist and along the arm. */
    val snatchDrop = swingPose(shin = 0f, thigh = 0f, torso = -3f, arm = 115f).turned()

    /** Casting the bell off the rack: the arm unfolding, the bell falling off the forearm to hang. */
    val castPose = Pose(shin = 0f, thigh = 0f, torso = -2f, upperArm = 15f, forearm = 45f, bell = RESTING)

    /** Dropped under a bell driven off the rack, the knees bent again and the arm already locked. */
    val underTheBell = lockout.standingAs(Pose(shin = 18f, thigh = -28f, torso = 0f, upperArm = 0f, forearm = 0f))

    /** A swing's pose: the arm straight, [arm] from hanging, the bell along it. */
    fun swingPose(shin: Float, thigh: Float, torso: Float, arm: Float) =
        Pose(shin = shin, thigh = thigh, torso = torso, upperArm = arm, forearm = arm)
}

/** This pose with the legs and torso of [legs], the arms as they were. */
internal fun Pose.standingAs(legs: Pose) = copy(
    shin = legs.shin,
    thigh = legs.thigh,
    torso = legs.torso,
    farShin = legs.farShin,
    farThigh = legs.farThigh,
    farFoot = legs.farFoot,
)

/** This pose with the far arm hanging free at the side, swaying [sway] of the way after the near one. */
internal fun Pose.freeHand(sway: Float = FREE_SWAY) = copy(
    farUpperArm = upperArm.coerceIn(-FREE_REACH, FREE_REACH) * sway,
    farForearm = upperArm.coerceIn(-FREE_REACH, FREE_REACH) * sway + FREE_BEND,
)

/** This pose with the far arm hanging still at the side, as in a strict lift. */
internal fun Pose.stillHand() = copy(farUpperArm = 0f, farForearm = FREE_BEND)

/** This pose with the far arm held out in front for balance, as in a one-bell squat. */
internal fun Pose.balancing() = copy(farUpperArm = BALANCE_ARM, farForearm = BALANCE_FOREARM)

/** This pose with its bell turned [by] degrees further round, as a bell that went over the hand is. */
internal fun Pose.turned(by: Float = FULL_TURN) = copy(bell = bell + by, farBell = farBell + by)

/** A free arm's sway, its reach and the bend at its elbow. */
private const val FREE_SWAY = 0.35f
private const val FREE_REACH = 90f
private const val FREE_BEND = 10f
private const val BALANCE_ARM = 70f
private const val BALANCE_FOREARM = 80f
