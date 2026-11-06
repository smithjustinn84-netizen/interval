package dev.justinsmith.lift.ui.demo

/**
 * The stances the barbell lifts share, so a pull ends where a catch begins. The bar is in the hands, so each
 * is where the arms put it: straight down to the floor, the hang or the hips; on the front of the shoulders;
 * on the upper back; locked out overhead.
 */
internal object BarbellPoses {
    /** The bar on the floor over the middle of the foot, the shins close, the back flat. */
    val floor = bar(shin = 15f, thigh = -90f, torso = 50f, arm = -6f)

    /** The bar passing the knees on the way up, the back angle kept. */
    val atKnee = bar(shin = 4f, thigh = -50f, torso = 45f, arm = -4f)

    /** The hang: standing hinged a little with the bar just above the knees. */
    val hang = bar(shin = 10f, thigh = -30f, torso = 35f, arm = -2f)

    /** Standing tall with the bar at the hips, the arms long. */
    val standing = bar(shin = 0f, thigh = 0f, torso = 0f, arm = 2f)

    /** Hips and knees driven straight, leaning back a touch, the bar brushing the hips: the top of a pull. */
    val extended = bar(shin = -2f, thigh = 8f, torso = -10f, arm = 6f)

    /** The bar on the front of the shoulders, the elbows high. */
    val frontRack = Pose(shin = 0f, thigh = 0f, torso = -2f, upperArm = 70f, forearm = 232f)

    /** The bar on the front of the shoulders for a press, the forearms upright under it. */
    val pressRack = Pose(shin = 0f, thigh = 0f, torso = -2f, upperArm = 25f, forearm = 185f)

    /** Halfway through a press, the bar past the face. */
    val pressing = Pose(shin = 0f, thigh = 0f, torso = -4f, upperArm = 95f, forearm = 182f)

    /** The bar on the upper back, the hands tight on it either side. */
    val backRack = Pose(shin = 0f, thigh = 0f, torso = 5f, upperArm = -40f, forearm = 160f)

    /** Standing with the bar locked out overhead, a little behind the head. */
    val overhead = Pose(shin = 0f, thigh = 0f, torso = -2f, upperArm = 182f, forearm = 182f)

    /** The same, reached from the back: the arms went up behind, so a whole turn back. */
    val overheadFromBack = overhead.copy(upperArm = overhead.upperArm - FULL_TURN)

    /** A short dip of the knees, the torso upright. */
    val dip = Pose(shin = 16f, thigh = -22f, torso = 0f, upperArm = 0f, forearm = 0f)

    /** Caught above a quarter squat. */
    val power = Pose(shin = 16f, thigh = -36f, torso = 8f, upperArm = 0f, forearm = 0f)

    /** Caught in a full squat, the chest up. */
    val deep = Pose(shin = 30f, thigh = -105f, torso = 18f, upperArm = 0f, forearm = 0f)

    /** The bottom of a back squat, leaning further over the bar on the back. */
    val backDeep = Pose(shin = 30f, thigh = -100f, torso = 35f, upperArm = 0f, forearm = 0f)

    /** Caught in a split, the near foot forward and the far one back on its toes. */
    val split = Pose(
        shin = 15f,
        thigh = -45f,
        torso = 0f,
        upperArm = 0f,
        forearm = 0f,
        farShin = 60f,
        farThigh = 30f,
        farFoot = 45f,
    )

    /** Recovering from the split, the front foot stepped back and the back foot coming up under the hips. */
    val halfSplit = Pose(
        shin = 4f,
        thigh = -12f,
        torso = 0f,
        upperArm = 0f,
        forearm = 0f,
        farShin = 20f,
        farThigh = 20f,
    )

    /** Driving up out of a dip into a split, the back foot already leaving the floor. */
    val jumping = Pose(
        shin = 0f,
        thigh = 0f,
        torso = 0f,
        upperArm = 0f,
        forearm = 0f,
        farShin = 20f,
        farThigh = -10f,
        farFoot = -20f,
    )

    /** A pull's pose: the arms straight, [arm] from hanging, the bar in the hands. */
    fun bar(shin: Float, thigh: Float, torso: Float, arm: Float) =
        Pose(shin = shin, thigh = thigh, torso = torso, upperArm = arm, forearm = arm)

    private const val FULL_TURN = 360f
}

/** The arms of this pose over the legs and torso of [legs]. */
internal infix fun Pose.over(legs: Pose) = standingAs(legs)
