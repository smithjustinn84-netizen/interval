package dev.justinsmith.lift.ui.demo

import dev.justinsmith.interval.ui.ExerciseRep

/** The farmer's walk: a bell hanging at each side, walking tall with short, steady steps. */
val FarmersWalkDemo: ExerciseRep = walk(
    holding = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 0f),
    hold = Hold.Pair,
    swingFreeArm = false,
)

/** The suitcase carry: one bell at the side like a briefcase, the free arm swinging as in any walk. */
val SuitcaseCarryDemo: ExerciseRep = walk(
    holding = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 0f),
    hold = Hold.NearHand,
    swingFreeArm = true,
)

/** The rack carry: the bell in the rack, fist under the chin, walking slowly and tall. */
val RackCarryDemo: ExerciseRep = walk(holding = rackPose, hold = Hold.NearHand, swingFreeArm = true)

/** The bottoms-up carry: the bell upside down at shoulder height over an upright forearm. */
val BottomsUpCarryDemo: ExerciseRep = walk(
    holding = rackPose.copy(upperArm = 32f, forearm = 178f, bell = BOTTOMS_UP_ANGLE),
    hold = Hold.NearHand,
    swingFreeArm = true,
)

private fun walk(holding: Pose, hold: Hold, swingFreeArm: Boolean): ExerciseRep {
    fun step(legs: Pose, swing: Float): Pose {
        val stepping = holding.standingAs(legs)
        return if (swingFreeArm) stepping.copy(farUpperArm = swing, farForearm = swing + ARM_BEND) else stepping
    }
    val nearStrikes = step(
        Pose(
            shin = -6f,
            thigh = -22f,
            torso = 0f,
            upperArm = 0f,
            forearm = 0f,
            farShin = 22f,
            farThigh = 18f,
            farFoot = 25f,
        ),
        swing = ARM_SWING,
    )
    val nearCarries = step(
        Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 0f, farShin = 40f, farThigh = -12f),
        swing = 0f,
    )
    val farStrikes = step(
        Pose(shin = 22f, thigh = 18f, torso = 0f, upperArm = 0f, forearm = 0f, farShin = -6f, farThigh = -22f),
        swing = -ARM_SWING,
    )
    val farCarries = step(
        Pose(shin = 40f, thigh = -12f, torso = 0f, upperArm = 0f, forearm = 0f, farShin = 0f, farThigh = 0f),
        swing = 0f,
    )
    return repOf(
        keyframes = listOf(
            Keyframe(at = 0f, pose = nearStrikes),
            Keyframe(at = 0.25f, pose = nearCarries),
            Keyframe(at = 0.5f, pose = farStrikes),
            Keyframe(at = 0.75f, pose = farCarries),
            Keyframe(at = 1f, pose = nearStrikes),
        ),
        phases = listOf(
            DemoPhase(until = 0.5f, beat = DemoBeat.Drive, cue = 1),
            DemoPhase(until = 1f, beat = DemoBeat.Ease, cue = 1),
        ),
        hold = hold,
        secondsPerRep = STRIDE_SECONDS,
        still = 0.25f,
        root = Root.Hips,
    )
}

private const val ARM_SWING = 18f
private const val ARM_BEND = 12f
private const val STRIDE_SECONDS = 1.3f
