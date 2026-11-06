package dev.justinsmith.lift.ui.demo

import dev.justinsmith.lift.ui.demo.KettlebellPoses.BOTTOMS_UP
import dev.justinsmith.lift.ui.demo.KettlebellPoses.rack

/** The farmer's walk: a bell hanging at each side, walking tall with short, steady steps. */
val FarmersWalkDemo: ExerciseDemo = walk(
    hold = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 0f),
    bells = Bells.Pair,
    swingFreeArm = false,
)

/** The suitcase carry: one bell at the side like a briefcase, the free arm swinging as in any walk. */
val SuitcaseCarryDemo: ExerciseDemo = walk(
    hold = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 0f),
    bells = Bells.NearHand,
    swingFreeArm = true,
)

/** The rack carry: the bell in the rack, fist under the chin, walking slowly and tall. */
val RackCarryDemo: ExerciseDemo = walk(hold = rack, bells = Bells.NearHand, swingFreeArm = true)

/** The bottoms-up carry: the bell upside down at shoulder height over an upright forearm. */
val BottomsUpCarryDemo: ExerciseDemo = walk(
    hold = rack.copy(upperArm = 32f, forearm = 178f, bell = BOTTOMS_UP),
    bells = Bells.NearHand,
    swingFreeArm = true,
)

/**
 * A walk on the spot (the hips stay put while the floor goes by underfoot), a stride a rep, with the arms as
 * [hold] has them and the free arm swinging opposite the near leg if [swingFreeArm]. Its phases are each step,
 * under the cue to walk tall (1).
 */
private fun walk(hold: Pose, bells: Bells, swingFreeArm: Boolean): ExerciseDemo {
    fun step(legs: Pose, swing: Float): Pose {
        val stepping = hold.standingAs(legs)
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
    return ExerciseDemo(
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
        bells = bells,
        secondsPerRep = STRIDE_SECONDS,
        still = 0.25f,
        root = Root.Hips,
    )
}

/** A free arm's swing either way in a walk, and the bend at its elbow. */
private const val ARM_SWING = 18f
private const val ARM_BEND = 12f

/** A stride, two steps, at a steady walk. */
private const val STRIDE_SECONDS = 1.3f
