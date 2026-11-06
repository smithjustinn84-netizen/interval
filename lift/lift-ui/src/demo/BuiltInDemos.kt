package dev.justinsmith.lift.ui.demo

import dev.justinsmith.lift.ui.demo.KettlebellPoses.swingPose

/**
 * The two-hand swing: hike the bell back between the legs, snap the hips to stand, let it float to the chest,
 * wait for the arms to meet the ribs, hinge. The hips lead and the bell follows: they are locked out by the
 * snap with the bell still on its way up, and on the way down the hinge waits for the arms. Its phases are
 * its cues: Hike (0), Snap (1), Float (2).
 */
val TwoHandSwingDemo: ExerciseDemo = run {
    val hike = swingPose(shin = 12f, thigh = -55f, torso = 65f, arm = -40f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = hike),
            Keyframe(at = 0.14f, pose = swingPose(shin = 6f, thigh = -24f, torso = 30f, arm = -14f)),
            Keyframe(at = 0.26f, pose = swingPose(shin = 0f, thigh = 0f, torso = -4f, arm = 32f)),
            Keyframe(at = 0.46f, pose = swingPose(shin = 0f, thigh = 0f, torso = -3f, arm = 88f)),
            Keyframe(at = 0.66f, pose = swingPose(shin = 0f, thigh = 0f, torso = -2f, arm = 16f)),
            Keyframe(at = 0.80f, pose = swingPose(shin = 8f, thigh = -35f, torso = 40f, arm = -10f)),
            Keyframe(at = 1f, pose = hike),
        ),
        phases = listOf(
            DemoPhase(until = 0.26f, beat = DemoBeat.Drive, cue = 1),
            DemoPhase(until = 0.66f, beat = DemoBeat.Ease, cue = 2),
            DemoPhase(until = 1f, beat = DemoBeat.Set, cue = 0),
        ),
        hold = Hold.BothHands,
        secondsPerRep = 1.8f,
        still = 0.46f,
        bellLag = 0.03f,
        trail = 0.16f,
    )
}

/**
 * The goblet squat: stand braced with the bell at the chest, sit down between the heels, hold the bottom a
 * moment, drive up. Its phases are its cues: Brace (0), Sit (1), Drive (2).
 */
val GobletSquatDemo: ExerciseDemo = run {
    val stand = gobletPose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 10f, forearm = 155f)
    val bottom = gobletPose(shin = 32f, thigh = -100f, torso = 22f, upperArm = 15f, forearm = 150f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = stand),
            Keyframe(at = 0.08f, pose = stand),
            Keyframe(
                at = 0.30f,
                pose = gobletPose(shin = 18f, thigh = -55f, torso = 25f, upperArm = 12f, forearm = 152f),
            ),
            Keyframe(at = 0.52f, pose = bottom),
            Keyframe(at = 0.60f, pose = bottom),
            Keyframe(
                at = 0.78f,
                pose = gobletPose(shin = 14f, thigh = -40f, torso = 20f, upperArm = 12f, forearm = 153f),
            ),
            Keyframe(at = 0.92f, pose = stand),
            Keyframe(at = 1f, pose = stand),
        ),
        phases = listOf(
            DemoPhase(until = 0.08f, beat = DemoBeat.Set, cue = 0),
            DemoPhase(until = 0.60f, beat = DemoBeat.Ease, cue = 1),
            DemoPhase(until = 0.92f, beat = DemoBeat.Drive, cue = 2),
            DemoPhase(until = 1f, beat = DemoBeat.Set, cue = 0),
        ),
        hold = Hold.BothHands,
        secondsPerRep = 3f,
        still = 0.56f,
    )
}

/** A goblet squat's pose: the bell by the horns under the hands, hanging a little forward, clear of the ribs. */
private fun gobletPose(shin: Float, thigh: Float, torso: Float, upperArm: Float, forearm: Float) =
    Pose(shin = shin, thigh = thigh, torso = torso, upperArm = upperArm, forearm = forearm, bell = CHEST_BELL)

private const val CHEST_BELL = 12f
